package craftednebula.tablewatch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentTypeInteger;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.builder.ArgumentBuilderRequired;
import craftednebula.tablewatch.BlockLogEntry;
import craftednebula.tablewatch.DatabaseManager;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.util.phys.HitResult;
import org.joml.Vector3d;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CommandGetRecent implements CommandManager.CommandRegistry {
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm:ss");
	private static final int PAGE_SIZE = 6;

	public CommandGetRecent() {
	}

	@Override
	public void register(CommandDispatcher<CommandSource> dispatcher) {
		registerCommandNode(dispatcher, "getrecent");
		// quick alias
		registerCommandNode(dispatcher, "gr");
	}

	private void registerCommandNode(CommandDispatcher<CommandSource> dispatcher, String name) {
		dispatcher.register(
			ArgumentBuilderLiteral.<CommandSource>literal(name)
				.requires(CommandSource::hasAdmin)
				.executes(context -> executeGetRecent(context.getSource(), 1))
				// Optional page argument: /getrecent <page>
				.then(
					ArgumentBuilderRequired.<CommandSource, Integer>argument("page", ArgumentTypeInteger.integer(1))
						.executes(context -> {
							int page = ArgumentTypeInteger.getInteger(context, "page");
							return executeGetRecent(context.getSource(), page);
						})
				)
		);
	}

	private int executeGetRecent(CommandSource source, int page) {
		Player player = source.getSender();

		if (player == null) {
			source.sendMessage("§cThis command can only be executed by an in-game player.");
			return 0;
		}

		HitResult hit = raycast(player);

		// Check if we hit a block using sealed subclass pattern matching
		if (!(hit instanceof HitResult.Tile tileHit)) {
			source.sendMessage("§e[TableWatch] §cYou are not looking at a block!");
			return 0;
		}

		int x = tileHit.tilePos.x();
		int y = tileHit.tilePos.y();
		int z = tileHit.tilePos.z();

		List<BlockLogEntry> logs = DatabaseManager.getRecentLogs("world", x, y, z, page, PAGE_SIZE);

		source.sendMessage("§6> TableWatch History (" + x + ", " + y + ", " + z + ") §e(Page " + page + ")");
		if (logs.isEmpty()) {
			source.sendMessage("§7No recorded history for this coordinate on page " + page + ".");
			return 1;
		}

		for (BlockLogEntry log : logs) {
			String dateStr = DATE_FORMAT.format(new Date(log.timestamp * 1000L));
			String actionColor = getActionColor(log.action);
			String blockName = getReadableBlockName(log.blockId);

			// Highlight suspicious block names in light red to match /check
			if (isSuspiciousBlock(log.blockId)) {
				blockName = "§c" + blockName + "§7";
			}

			// Uniform output format for BREAK, PLACE, and INTERACT
			source.sendMessage(String.format("§8[%s] §b%s %s%s §7(%s)",
				dateStr, log.playerName, actionColor, log.action.name(), blockName));
		}

		return 1;
	}

	private boolean isSuspiciousBlock(int blockId) {
		return blockId == 10 || blockId == 11 || blockId == 46 || blockId == 51;
	}

	private String getActionColor(BlockLogEntry.Action action) {
		return switch (action) {
			case BREAK -> "§c";
			case PLACE -> "§a";
			case INTERACT -> "§e";
		};
	}

	private String getReadableBlockName(int blockId) {
		if (blockId == 0) return "Air";
		Block<?> block = Blocks.blocksList[blockId];
		if (block == null) return "Unknown (" + blockId + ")";

		String name = block.getKey();
		if (name != null && name.contains(".")) {
			name = name.substring(name.lastIndexOf('.') + 1);
		}
		return name != null ? name : "ID:" + blockId;
	}

	/**
	 * Casts a ray out from player's eye position using JOML vectors to find the targeted block.
	 */
	private HitResult raycast(Player player) {
		Vector3d pos = new Vector3d(player.x, player.y + player.getHeadHeight(), player.z);
		double cosYaw = Math.cos(-player.yRot * 0.017453292F - (float) Math.PI);
		double sinYaw = Math.sin(-player.yRot * 0.017453292F - (float) Math.PI);
		double cosPitch = -Math.cos(-player.xRot * 0.017453292F);
		double sinPitch = Math.sin(-player.xRot * 0.017453292F);

		double lookX = sinYaw * cosPitch;
		double lookZ = cosYaw * cosPitch;

		Vector3d target = new Vector3d(pos).add(lookX * 5.0, sinPitch * 5.0, lookZ * 5.0);
		return player.world.checkBlockCollisionBetweenPoints(pos, target);
	}
}
