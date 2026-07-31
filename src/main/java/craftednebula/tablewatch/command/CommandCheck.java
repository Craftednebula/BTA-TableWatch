package craftednebula.tablewatch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentTypeInteger;
import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.builder.ArgumentBuilderRequired;
import craftednebula.tablewatch.BlockLogEntry;
import craftednebula.tablewatch.DatabaseManager;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.net.command.CommandSource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CommandCheck implements CommandManager.CommandRegistry {
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm:ss");
	private static final int PAGE_SIZE = 6;

	@Override
	public void register(CommandDispatcher<CommandSource> dispatcher) {
		registerCommandNode(dispatcher, "check");
		registerCommandNode(dispatcher, "ch");
	}

	private void registerCommandNode(CommandDispatcher<CommandSource> dispatcher, String name) {
		dispatcher.register(
			ArgumentBuilderLiteral.<CommandSource>literal(name)
				.requires(CommandSource::hasAdmin)
				.then(
					ArgumentBuilderRequired.<CommandSource, String>argument("player", ArgumentTypeString.word())
						.executes(context -> {
							String targetPlayer = ArgumentTypeString.getString(context, "player");
							return executeCheck(context.getSource(), targetPlayer, 1);
						})
						// Optional Page argument: /check <player> <page>
						.then(
							ArgumentBuilderRequired.<CommandSource, Integer>argument("page", ArgumentTypeInteger.integer(1))
								.executes(context -> {
									String targetPlayer = ArgumentTypeString.getString(context, "player");
									int page = ArgumentTypeInteger.getInteger(context, "page");
									return executeCheck(context.getSource(), targetPlayer, page);
								})
						)
				)
		);
	}

	private int executeCheck(CommandSource source, String targetPlayer, int page) {
		List<BlockLogEntry> logs = DatabaseManager.getPlayerLogs("world", targetPlayer, page, PAGE_SIZE);

		source.sendMessage("§6> TableWatch History: §b" + targetPlayer + " §e(Page " + page + ")");
		if (logs.isEmpty()) {
			source.sendMessage("§7No recorded history found on page " + page + ".");
			return 1;
		}

		displayLogs(source, logs);
		return 1;
	}

	public static void displayLogs(CommandSource source, List<BlockLogEntry> logs) {
		for (BlockLogEntry log : logs) {
			String dateStr = DATE_FORMAT.format(new Date(log.timestamp * 1000L));
			String actionColor = getActionColor(log.action);
			String blockName = getReadableBlockName(log.action == BlockLogEntry.Action.BREAK ? log.oldBlockId : log.blockId);

			source.sendMessage(String.format("§8[%s] %s%s §7%s §8(%d, %d, %d)",
				dateStr, actionColor, log.action.name(), blockName, log.x, log.y, log.z));
		}
	}

	private static String getActionColor(BlockLogEntry.Action action) {
		return switch (action) {
			case BREAK -> "§c";
			case PLACE -> "§a";
			case INTERACT -> "§e";
		};
	}

	private static String getReadableBlockName(int blockId) {
		if (blockId == 0) return "Air";
		Block<?> block = Blocks.blocksList[blockId];
		if (block == null) return "Unknown (" + blockId + ")";

		String name = block.getKey();
		if (name.contains(".")) {
			name = name.substring(name.lastIndexOf('.') + 1);
		}
		return name;
	}
}
