package craftednebula.tablewatch;

public class BlockLogEntry {
	public enum Action {
		BREAK(1), PLACE(2), INTERACT(3);
		public final int id;
		Action(int id) { this.id = id; }
	}

	public long timestamp;
	public String playerName;
	public Action action;
	public String worldName;
	public int x, y, z;
	public int blockId;
	public int blockMeta;

	public BlockLogEntry(long timestamp, String playerName, Action action, String worldName,
	                     int x, int y, int z, int blockId, int blockMeta) {
		this.timestamp = timestamp;
		this.playerName = playerName;
		this.action = action;
		this.worldName = worldName;
		this.x = x;
		this.y = y;
		this.z = z;
		this.blockId = blockId;
		this.blockMeta = blockMeta;
	}
}
