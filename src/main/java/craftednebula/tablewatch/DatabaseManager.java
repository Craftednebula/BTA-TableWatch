package craftednebula.tablewatch;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DatabaseManager {
	private static final ConcurrentLinkedQueue<BlockLogEntry> QUEUE = new ConcurrentLinkedQueue<>();
	private static volatile boolean running = false;
	private static Thread workerThread;
	private static Connection connection;
	private static Thread pruneThread;

	/**
	 * Deletes logs older than the specified age in seconds and reclaims disk space.
	 * @return The number of deleted rows.
	 */
	public static int pruneOldLogs(long maxAgeSeconds) {
		long cutoffTimestamp = (System.currentTimeMillis() / 1000L) - maxAgeSeconds;
		String sql = "DELETE FROM block_logs WHERE timestamp < ?;";
		int deletedRows = 0;

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setLong(1, cutoffTimestamp);
			deletedRows = pstmt.executeUpdate();

			if (deletedRows > 0) {
				// Reclaim unused disk space after deleting records
				try (Statement stmt = connection.createStatement()) {
					stmt.execute("VACUUM;");
				}
				System.out.println("[TableWatch] Pruned " + deletedRows + " old block log entries.");
			}
		} catch (SQLException e) {
			System.err.println("[TableWatch] Failed to prune old database logs!");
			e.printStackTrace();
		}

		return deletedRows;
	}

	private static void startPruneScheduler() {
		if (!ConfigManager.autoPruneEnabled) return;

		pruneThread = new Thread(() -> {
			while (running) {
				try {
					pruneOldLogs(ConfigManager.maxLogAgeSeconds);
					Thread.sleep(ConfigManager.pruneIntervalSeconds * 1000L);
				} catch (InterruptedException ignored) {
					break;
				}
			}
		}, "TableWatch-Prune-Worker");

		pruneThread.setDaemon(true);
		pruneThread.start();
	}

	public static void initialize(File dataDirectory) {
		try {
			if (!dataDirectory.exists()) {
				dataDirectory.mkdirs();
			}
			File dbFile = new File(dataDirectory, "block_logs.db");

			// Ensure SQLite driver is registered by the Knot classloader
			try {
				Class.forName("org.sqlite.JDBC");
			} catch (ClassNotFoundException ignored) {}

			connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
			createSchema();
			startWorker();
			startPruneScheduler();

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static List<BlockLogEntry> getRecentLogs(String worldName, int x, int y, int z, int page, int pageSize) {
		List<BlockLogEntry> results = new ArrayList<>();
		int offset = Math.max(0, (page - 1) * pageSize);

		String sql = "SELECT timestamp, player_name, action_type, block_id, block_meta " +
			"FROM block_logs WHERE world_name = ? AND x = ? AND y = ? AND z = ? " +
			"ORDER BY id DESC LIMIT ? OFFSET ?";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, worldName);
			pstmt.setInt(2, x);
			pstmt.setInt(3, y);
			pstmt.setInt(4, z);
			pstmt.setInt(5, pageSize);
			pstmt.setInt(6, offset);

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					long timestamp = rs.getLong("timestamp");
					String playerName = rs.getString("player_name");
					int actionId = rs.getInt("action_type");
					int blockId = rs.getInt("block_id");
					int blockMeta = rs.getInt("block_meta");

					BlockLogEntry.Action action = BlockLogEntry.Action.BREAK;
					for (BlockLogEntry.Action a : BlockLogEntry.Action.values()) {
						if (a.id == actionId) {
							action = a;
							break;
						}
					}

					results.add(new BlockLogEntry(timestamp, playerName, action, worldName, x, y, z, blockId, blockMeta));
				}
			}
		} catch (SQLException e) {
			System.err.println("[BlockLogger] Failed to query recent block logs!");
			e.printStackTrace();
		}

		// Reverse so the oldest entry on this page prints at the top, newest at the bottom
		Collections.reverse(results);
		return results;
	}

	private static String getSuspiciousFilterSql(boolean suspiciousOnly) {
		if (!suspiciousOnly) return "";
		return " AND block_id IN (580, 630)";
	}

	/**
	 * Retrieves paginated logs for a specific player.
	 * @param page 1-indexed page number (page 1 = newest entries)
	 */
	public static List<BlockLogEntry> getPlayerLogs(String worldName, String playerName, int page, int pageSize, boolean suspiciousOnly) {
		List<BlockLogEntry> results = new ArrayList<>();
		int offset = Math.max(0, (page - 1) * pageSize);

		String sql = "SELECT timestamp, action_type, x, y, z, block_id, block_meta " +
			"FROM block_logs WHERE world_name = ? AND player_name = ?" +
			getSuspiciousFilterSql(suspiciousOnly) +
			" ORDER BY id DESC LIMIT ? OFFSET ?;";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, worldName);
			pstmt.setString(2, playerName);
			pstmt.setInt(3, pageSize);
			pstmt.setInt(4, offset);

			executeQueryAndPopulate(results, pstmt, playerName, worldName);
		} catch (SQLException e) {
			System.err.println("[BlockLogger] Failed to query paginated player block logs!");
			e.printStackTrace();
		}

		Collections.reverse(results);
		return results;
	}
	/**
	 * Retrieves paginated logs for a specific 3D area (bounding box) within an optional timestamp range.
	 */
	public static List<BlockLogEntry> getAreaLogs(
		String worldName,
		int x1, int y1, int z1,
		int x2, int y2, int z2,
		long minTimestamp, long maxTimestamp,
		int page, int pageSize,
		boolean suspiciousOnly
	) {
		List<BlockLogEntry> results = new ArrayList<>();
		int offset = Math.max(0, (page - 1) * pageSize);

		int minX = Math.min(x1, x2);
		int maxX = Math.max(x1, x2);
		int minY = Math.min(y1, y2);
		int maxY = Math.max(y1, y2);
		int minZ = Math.min(z1, z2);
		int maxZ = Math.max(z1, z2);

		String sql = "SELECT timestamp, player_name, action_type, x, y, z, block_id, block_meta " +
			"FROM block_logs WHERE world_name = ? " +
			"AND x >= ? AND x <= ? " +
			"AND y >= ? AND y <= ? " +
			"AND z >= ? AND z <= ? " +
			"AND timestamp >= ? AND timestamp <= ?" +
			getSuspiciousFilterSql(suspiciousOnly) +
			" ORDER BY id DESC LIMIT ? OFFSET ?;";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, worldName);
			pstmt.setInt(2, minX);
			pstmt.setInt(3, maxX);
			pstmt.setInt(4, minY);
			pstmt.setInt(5, maxY);
			pstmt.setInt(6, minZ);
			pstmt.setInt(7, maxZ);
			pstmt.setLong(8, minTimestamp);
			pstmt.setLong(9, maxTimestamp);
			pstmt.setInt(10, pageSize);
			pstmt.setInt(11, offset);

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					long timestamp = rs.getLong("timestamp");
					String playerName = rs.getString("player_name");
					int actionId = rs.getInt("action_type");
					int x = rs.getInt("x");
					int y = rs.getInt("y");
					int z = rs.getInt("z");
					int blockId = rs.getInt("block_id");
					int blockMeta = rs.getInt("block_meta");

					BlockLogEntry.Action action = BlockLogEntry.Action.BREAK;
					for (BlockLogEntry.Action a : BlockLogEntry.Action.values()) {
						if (a.id == actionId) {
							action = a;
							break;
						}
					}

					results.add(new BlockLogEntry(timestamp, playerName, action, worldName, x, y, z, blockId, blockMeta));
				}
			}
		} catch (SQLException e) {
			System.err.println("[BlockLogger] Failed to query area block logs!");
			e.printStackTrace();
		}

		Collections.reverse(results);
		return results;
	}
	/**
	 * Retrieves paginated logs for a specific player within a Unix timestamp range (seconds).
	 */
	public static List<BlockLogEntry> getPlayerLogsByDate(String worldName, String playerName, long minTimestamp, long maxTimestamp, int page, int pageSize, boolean suspiciousOnly) {
		List<BlockLogEntry> results = new ArrayList<>();
		int offset = Math.max(0, (page - 1) * pageSize);

		String sql = "SELECT timestamp, action_type, x, y, z, block_id, block_meta " +
			"FROM block_logs WHERE world_name = ? AND player_name = ? AND timestamp >= ? AND timestamp <= ?" +
			getSuspiciousFilterSql(suspiciousOnly) +
			" ORDER BY id DESC LIMIT ? OFFSET ?;";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, worldName);
			pstmt.setString(2, playerName);
			pstmt.setLong(3, minTimestamp);
			pstmt.setLong(4, maxTimestamp);
			pstmt.setInt(5, pageSize);
			pstmt.setInt(6, offset);

			executeQueryAndPopulate(results, pstmt, playerName, worldName);
		} catch (SQLException e) {
			System.err.println("[BlockLogger] Failed to query date-filtered player block logs!");
			e.printStackTrace();
		}

		Collections.reverse(results);
		return results;
	}

	private static void executeQueryAndPopulate(List<BlockLogEntry> results, PreparedStatement pstmt, String playerName, String worldName) throws SQLException {
		try (ResultSet rs = pstmt.executeQuery()) {
			while (rs.next()) {
				long timestamp = rs.getLong("timestamp");
				int actionId = rs.getInt("action_type");
				int x = rs.getInt("x");
				int y = rs.getInt("y");
				int z = rs.getInt("z");
				int blockId = rs.getInt("block_id");
				int blockMeta = rs.getInt("block_meta");

				BlockLogEntry.Action action = BlockLogEntry.Action.BREAK;
				for (BlockLogEntry.Action a : BlockLogEntry.Action.values()) {
					if (a.id == actionId) {
						action = a;
						break;
					}
				}

				results.add(new BlockLogEntry(timestamp, playerName, action, worldName, x, y, z, blockId, blockMeta));
			}
		}
	}

	/**
	 * Clears all recorded block logs from the queue and SQLite database.
	 */
	public static void clearDatabase() {
		QUEUE.clear(); // Clear any pending inserts waiting in the worker queue
		String sql = "DELETE FROM block_logs;";

		try (Statement stmt = connection.createStatement()) {
			stmt.execute(sql);
			stmt.execute("VACUUM;"); // Reclaims disk space after mass deletion
			System.out.println("[BlockLogger] Database cleared successfully.");
		} catch (SQLException e) {
			System.err.println("[BlockLogger] Failed to clear database!");
			e.printStackTrace();
		}
	}

	private static void createSchema() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("CREATE TABLE IF NOT EXISTS block_logs (" +
				"id INTEGER PRIMARY KEY AUTOINCREMENT," +
				"timestamp INTEGER NOT NULL," +
				"player_name TEXT NOT NULL," +
				"action_type INTEGER NOT NULL," +
				"world_name TEXT NOT NULL," +
				"x INTEGER NOT NULL," +
				"y INTEGER NOT NULL," +
				"z INTEGER NOT NULL," +
				"block_id INTEGER NOT NULL," +
				"block_meta INTEGER NOT NULL" +
				");");

			stmt.execute("CREATE INDEX IF NOT EXISTS idx_location ON block_logs (world_name, x, y, z);");
			stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_time ON block_logs (player_name, timestamp);");
		}
	}

	public static void queue(BlockLogEntry entry) {
		QUEUE.add(entry);
	}

	private static void startWorker() {
		running = true;
		workerThread = new Thread(() -> {
			String sql = "INSERT INTO block_logs (timestamp, player_name, action_type, world_name, x, y, z, block_id, block_meta) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
			while (running || !QUEUE.isEmpty()) {
				if (QUEUE.isEmpty()) {
					try {
						Thread.sleep(250);
					} catch (InterruptedException ignored) {}
					continue;
				}

				List<BlockLogEntry> batch = new ArrayList<>();
				for (int i = 0; i < 100 && !QUEUE.isEmpty(); i++) {
					BlockLogEntry entry = QUEUE.poll();
					if (entry != null) batch.add(entry);
				}

				if (!batch.isEmpty()) {
					try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
						connection.setAutoCommit(false);
						for (BlockLogEntry entry : batch) {
							pstmt.setLong(1, entry.timestamp);
							pstmt.setString(2, entry.playerName);
							pstmt.setInt(3, entry.action.id);
							pstmt.setString(4, entry.worldName);
							pstmt.setInt(5, entry.x);
							pstmt.setInt(6, entry.y);
							pstmt.setInt(7, entry.z);
							pstmt.setInt(8, entry.blockId);
							pstmt.setInt(9, entry.blockMeta);

							pstmt.addBatch();
						}
						pstmt.executeBatch();
						connection.commit();
						connection.setAutoCommit(true);
					} catch (SQLException e) {
						System.err.println("[BlockLogger] Failed to save block log batch!");
						e.printStackTrace();
					}
				}
			}
		}, "BlockLogger-DB-Worker");
		workerThread.setDaemon(true);
		workerThread.start();
	}

	public static void shutdown() {
		running = false;
		if (workerThread != null) {
			try {
				workerThread.join(3000); // Give it up to 3 seconds to flush remaining queue
			} catch (InterruptedException ignored) {}
		}
		try {
			if (connection != null && !connection.isClosed()) {
				connection.close();
			}
		} catch (SQLException ignored) {}
	}
}
