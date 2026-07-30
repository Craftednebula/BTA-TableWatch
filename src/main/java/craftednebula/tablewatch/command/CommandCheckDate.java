package craftednebula.tablewatch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentTypeInteger;
import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.builder.ArgumentBuilderRequired;
import craftednebula.tablewatch.BlockLogEntry;
import craftednebula.tablewatch.DatabaseManager;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.net.command.CommandSource;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CommandCheckDate implements CommandManager.CommandRegistry {
	// Supports formats like "2026-07-30" or "2026-07-30-14:30"
	private static final SimpleDateFormat DATE_ONLY = new SimpleDateFormat("yyyy-MM-dd");
	private static final SimpleDateFormat DATE_TIME = new SimpleDateFormat("yyyy-MM-dd-HH:mm");
	private static final int PAGE_SIZE = 8;

	@Override
	public void register(CommandDispatcher<CommandSource> dispatcher) {
		registerCommandNode(dispatcher, "checkdate");
		registerCommandNode(dispatcher, "cd");
	}
	// shit's ugly asf
	private void registerCommandNode(CommandDispatcher<CommandSource> dispatcher, String name) {
		dispatcher.register(
			ArgumentBuilderLiteral.<CommandSource>literal(name)
				.requires(CommandSource::hasAdmin)
				.then(
					ArgumentBuilderRequired.<CommandSource, String>argument("player", ArgumentTypeString.word())
						.then(
							ArgumentBuilderRequired.<CommandSource, String>argument("min", ArgumentTypeString.word())
								.then(
									ArgumentBuilderRequired.<CommandSource, String>argument("max", ArgumentTypeString.word())
										// Default to Page 1
										.executes(context -> {
											String player = ArgumentTypeString.getString(context, "player");
											String minStr = ArgumentTypeString.getString(context, "min");
											String maxStr = ArgumentTypeString.getString(context, "max");
											return executeCheckDate(context.getSource(), player, minStr, maxStr, 1);
										})
										// Optional Page argument: /checkdate <player> <min> <max> <page>
										.then(
											ArgumentBuilderRequired.<CommandSource, Integer>argument("page", ArgumentTypeInteger.integer(1))
												.executes(context -> {
													String player = ArgumentTypeString.getString(context, "player");
													String minStr = ArgumentTypeString.getString(context, "min");
													String maxStr = ArgumentTypeString.getString(context, "max");
													int page = ArgumentTypeInteger.getInteger(context, "page");
													return executeCheckDate(context.getSource(), player, minStr, maxStr, page);
												})
										)
								)
						)
				)
		);
	}

	private int executeCheckDate(CommandSource source, String player, String minStr, String maxStr, int page) {
		long minTs = parseTimestamp(minStr, false);
		long maxTs = parseTimestamp(maxStr, true);

		if (minTs == -1 || maxTs == -1) {
			source.sendMessage("§cInvalid date format! Use YYYY-MM-DD or YYYY-MM-DD-HH:mm");
			return 0;
		}

		List<BlockLogEntry> logs = DatabaseManager.getPlayerLogsByDate("world", player, minTs, maxTs, page, PAGE_SIZE);

		source.sendMessage("§6> TableWatch History: §b" + player + " §e(Page " + page + ")");
		if (logs.isEmpty()) {
			source.sendMessage("§7No logs found for this date range on page " + page + ".");
			return 1;
		}

		CommandCheck.displayLogs(source, logs);
		return 1;
	}

	private long parseTimestamp(String input, boolean endOfDay) {
		try {
			Date date;
			if (input.length() > 10) {
				date = DATE_TIME.parse(input);
			} else {
				date = DATE_ONLY.parse(input);
				if (endOfDay) {
					// Include the entire day up to 23:59:59 if only YYYY-MM-DD was given
					return (date.getTime() / 1000L) + 86399L;
				}
			}
			return date.getTime() / 1000L;
		} catch (ParseException e) {
			return -1;
		}
	}
}
