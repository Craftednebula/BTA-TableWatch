package craftednebula.tablewatch;

public class BlockLogEntry {
	public enum Action {
		BREAK(0),
		PLACE(1),
		INTERACT(2);

		public final int id;
		Action(int id) { this.id = id; }
	}

	public final long timestamp;
	public final String playerName;
	public final Action action;
	public final String worldName;
	public final int x, y, z;
	public final int blockId;
	public final int blockMeta;
	public final int oldBlockId;
	public final int oldBlockMeta;

	public BlockLogEntry(String playerName, Action action, String worldName, int x, int y, int z, int blockId, int blockMeta, int oldBlockId, int oldBlockMeta) {
		this.timestamp = System.currentTimeMillis() / 1000L; // Unix epoch in seconds
		this.playerName = playerName;
		this.action = action;
		this.worldName = worldName;
		this.x = x;
		this.y = y;
		this.z = z;
		this.blockId = blockId;
		this.blockMeta = blockMeta;
		this.oldBlockId = oldBlockId;
		this.oldBlockMeta = oldBlockMeta;
	}
	public BlockLogEntry(long timestamp, String playerName, Action action, String worldName, int x, int y, int z, int blockId, int blockMeta, int oldBlockId, int oldBlockMeta) {
		this.timestamp = timestamp;
		this.playerName = playerName;
		this.action = action;
		this.worldName = worldName;
		this.x = x; this.y = y; this.z = z;
		this.blockId = blockId; this.blockMeta = blockMeta;
		this.oldBlockId = oldBlockId; this.oldBlockMeta = oldBlockMeta;
	}
}
