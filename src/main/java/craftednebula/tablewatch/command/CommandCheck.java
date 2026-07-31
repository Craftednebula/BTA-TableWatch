package craftednebula.tablewatch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentTypeBool;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CommandCheck implements CommandManager.CommandRegistry {
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm:ss");
	private static final SimpleDateFormat DATE_ONLY = new SimpleDateFormat("yyyy-MM-dd");
	private static final SimpleDateFormat DATE_TIME = new SimpleDateFormat("yyyy-MM-dd-HH:mm");
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
						// /check <player> (default: page 1, suspicious=false, no date filter)
						.executes(context -> {
							String player = ArgumentTypeString.getString(context, "player");
							return executeCheck(context.getSource(), player, 1, false);
						})
						// /check <player> <page>
						.then(
							ArgumentBuilderRequired.<CommandSource, Integer>argument("page", ArgumentTypeInteger.integer(1))
								.executes(context -> {
									String player = ArgumentTypeString.getString(context, "player");
									int page = ArgumentTypeInteger.getInteger(context, "page");
									return executeCheck(context.getSource(), player, page, false);
								})
						)
						// /check <player> <suspiciousOnly> ...
						.then(
							ArgumentBuilderRequired.<CommandSource, Boolean>argument("suspiciousOnly", ArgumentTypeBool.bool())
								.executes(context -> {
									String player = ArgumentTypeString.getString(context, "player");
									boolean suspicious = ArgumentTypeBool.getBool(context, "suspiciousOnly");
									return executeCheck(context.getSource(), player, 1, suspicious);
								})
								// /check <player> <suspiciousOnly> <page>
								.then(
									ArgumentBuilderRequired.<CommandSource, Integer>argument("page", ArgumentTypeInteger.integer(1))
										.executes(context -> {
											String player = ArgumentTypeString.getString(context, "player");
											boolean suspicious = ArgumentTypeBool.getBool(context, "suspiciousOnly");
											int page = ArgumentTypeInteger.getInteger(context, "page");
											return executeCheck(context.getSource(), player, page, suspicious);
										})
								)
								// /check <player> <suspiciousOnly> <min> ...
								.then(
									buildDateBranches(true)
								)
						)
						// /check <player> <min> ... (when suspiciousOnly is omitted, defaults to false)
						.then(
							buildDateBranches(false)
						)
				)
		);
	}

	/**
	 * Builds the date filtering branches (<min> [max] [page]) for a given suspiciousOnly state.
	 */
	private ArgumentBuilderRequired<CommandSource, String> buildDateBranches(boolean suspiciousOnly) {
		return ArgumentBuilderRequired.<CommandSource, String>argument("min", ArgumentTypeString.word())
			// /check <player> [suspiciousOnly] <min> (default: max = now, page 1)
			.executes(context -> {
				String player = ArgumentTypeString.getString(context, "player");
				String minStr = ArgumentTypeString.getString(context, "min");
				return executeCheckDate(context.getSource(), player, minStr, null, 1, suspiciousOnly);
			})
			// /check <player> [suspiciousOnly] <min> <page> (no max date specified)
			.then(
				ArgumentBuilderRequired.<CommandSource, Integer>argument("minOnlyPage", ArgumentTypeInteger.integer(1))
					.executes(context -> {
						String player = ArgumentTypeString.getString(context, "player");
						String minStr = ArgumentTypeString.getString(context, "min");
						int page = ArgumentTypeInteger.getInteger(context, "minOnlyPage");
						return executeCheckDate(context.getSource(), player, minStr, null, page, suspiciousOnly);
					})
			)
			// /check <player> [suspiciousOnly] <min> <max> ...
			.then(
				ArgumentBuilderRequired.<CommandSource, String>argument("max", ArgumentTypeString.word())
					.executes(context -> {
						String player = ArgumentTypeString.getString(context, "player");
						String minStr = ArgumentTypeString.getString(context, "min");
						String maxStr = ArgumentTypeString.getString(context, "max");
						return executeCheckDate(context.getSource(), player, minStr, maxStr, 1, suspiciousOnly);
					})
					// /check <player> [suspiciousOnly] <min> <max> <page>
					.then(
						ArgumentBuilderRequired.<CommandSource, Integer>argument("page", ArgumentTypeInteger.integer(1))
							.executes(context -> {
								String player = ArgumentTypeString.getString(context, "player");
								String minStr = ArgumentTypeString.getString(context, "min");
								String maxStr = ArgumentTypeString.getString(context, "max");
								int page = ArgumentTypeInteger.getInteger(context, "page");
								return executeCheckDate(context.getSource(), player, minStr, maxStr, page, suspiciousOnly);
							})
					)
			);
	}

	private int executeCheck(CommandSource source, String targetPlayer, int page, boolean suspiciousOnly) {
		List<BlockLogEntry> logs = DatabaseManager.getPlayerLogs("world", targetPlayer, page, PAGE_SIZE, suspiciousOnly);

		String filterTag = suspiciousOnly ? " §c[SUSPICIOUS ONLY]" : "";
		source.sendMessage("§6> TableWatch History: §b" + targetPlayer + " §e(Page " + page + ")" + filterTag);
		if (logs.isEmpty()) {
			source.sendMessage("§7No recorded history found on page " + page + ".");
			return 1;
		}

		displayLogs(source, logs);
		return 1;
	}

	private int executeCheckDate(CommandSource source, String player, String minStr, String maxStr, int page, boolean suspiciousOnly) {
		long minTs = parseTimestamp(minStr, false);
		// If maxStr is omitted (null), default to the current system timestamp
		long maxTs = (maxStr == null) ? (System.currentTimeMillis() / 1000L) : parseTimestamp(maxStr, true);

		if (minTs == -1 || maxTs == -1) {
			source.sendMessage("§cInvalid date format! Use YYYY-MM-DD or YYYY-MM-DD-HH:mm");
			return 0;
		}

		List<BlockLogEntry> logs = DatabaseManager.getPlayerLogsByDate("world", player, minTs, maxTs, page, PAGE_SIZE, suspiciousOnly);

		String filterTag = suspiciousOnly ? " §c[SUSPICIOUS ONLY]" : "";
		source.sendMessage("§6> TableWatch History: §b" + player + " §e(Page " + page + ")" + filterTag);
		if (logs.isEmpty()) {
			source.sendMessage("§7No logs found for this date range on page " + page + ".");
			return 1;
		}

		displayLogs(source, logs);
		return 1;
	}

	public static void displayLogs(CommandSource source, List<BlockLogEntry> logs) {
		for (BlockLogEntry log : logs) {
			String dateStr = DATE_FORMAT.format(new Date(log.timestamp * 1000L));
			String actionColor = getActionColor(log.action);
			String blockName = getReadableBlockName(log.blockId);

			// Highlight suspicious block names in light red
			if (isSuspiciousBlock(log.blockId)) {
				blockName = "§c" + blockName + "§7";
			}

			source.sendMessage(String.format("§8[%s] %s%s §7%s §8(%d, %d, %d)",
				dateStr, actionColor, log.action.name(), blockName, log.x, log.y, log.z));
		}
	}

	private static boolean isSuspiciousBlock(int blockId) {
		return blockId == 10 || blockId == 11 || blockId == 46 || blockId == 51;
	}

	private static String getActionColor(BlockLogEntry.Action action) {
		return switch (action) {
			case BREAK -> "§c";
			case PLACE -> "§a";
			case INTERACT -> "§e";
			default -> "§f";
		};
	}

	private static String getReadableBlockName(int blockId) {
		if (blockId == 0) return "Air";
		Block<?> block = Blocks.blocksList[blockId];
		if (block == null) return "Unknown (" + blockId + ")";

		String name = block.getKey();
		if (name != null && name.contains(".")) {
			name = name.substring(name.lastIndexOf('.') + 1);
		}
		return name != null ? name : "ID:" + blockId;
	}

	private long parseTimestamp(String input, boolean endOfDay) {
		try {
			Date date;
			if (input.length() > 10) {
				date = DATE_TIME.parse(input);
			} else {
				date = DATE_ONLY.parse(input);
				if (endOfDay) {
					return (date.getTime() / 1000L) + 86399L;
				}
			}
			return date.getTime() / 1000L;
		} catch (ParseException e) {
			return -1;
		}
	}
}
