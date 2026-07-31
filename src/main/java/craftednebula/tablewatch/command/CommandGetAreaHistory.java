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
import net.minecraft.core.net.command.arguments.ArgumentTypeIntegerCoordinates;
import net.minecraft.core.net.command.helpers.IntegerCoordinates;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CommandGetAreaHistory implements CommandManager.CommandRegistry {
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm:ss");
	private static final SimpleDateFormat DATE_ONLY = new SimpleDateFormat("yyyy-MM-dd");
	private static final SimpleDateFormat DATE_TIME = new SimpleDateFormat("yyyy-MM-dd-HH:mm");
	private static final int PAGE_SIZE = 6;

	@Override
	public void register(CommandDispatcher<CommandSource> dispatcher) {
		registerCommandNode(dispatcher, "getareahistory");
		registerCommandNode(dispatcher, "gh");
	}

	private void registerCommandNode(CommandDispatcher<CommandSource> dispatcher, String name) {
		dispatcher.register(
			ArgumentBuilderLiteral.<CommandSource>literal(name)
				.requires(CommandSource::hasAdmin)
				.then(
					ArgumentBuilderRequired.<CommandSource, IntegerCoordinates>argument("pos1", ArgumentTypeIntegerCoordinates.intCoordinates())
						.then(
							ArgumentBuilderRequired.<CommandSource, IntegerCoordinates>argument("pos2", ArgumentTypeIntegerCoordinates.intCoordinates())
								.then(
									ArgumentBuilderRequired.<CommandSource, Boolean>argument("suspiciousOnly", ArgumentTypeBool.bool())
										// 1. /gh <pos1> <pos2> <suspiciousOnly> (default: all time, page 1)
										.executes(context -> {
											IntegerCoordinates pos1 = context.getArgument("pos1", IntegerCoordinates.class);
											IntegerCoordinates pos2 = context.getArgument("pos2", IntegerCoordinates.class);
											boolean suspicious = ArgumentTypeBool.getBool(context, "suspiciousOnly");
											return executeAreaCheck(context.getSource(), pos1, pos2, suspicious, null, null, 1);
										})
										// 2. /gh <pos1> <pos2> <suspiciousOnly> <page>
										.then(
											ArgumentBuilderRequired.<CommandSource, Integer>argument("page", ArgumentTypeInteger.integer(1))
												.executes(context -> {
													IntegerCoordinates pos1 = context.getArgument("pos1", IntegerCoordinates.class);
													IntegerCoordinates pos2 = context.getArgument("pos2", IntegerCoordinates.class);
													boolean suspicious = ArgumentTypeBool.getBool(context, "suspiciousOnly");
													int page = ArgumentTypeInteger.getInteger(context, "page");
													return executeAreaCheck(context.getSource(), pos1, pos2, suspicious, null, null, page);
												})
										)
										// 3. /gh <pos1> <pos2> <suspiciousOnly> <minDate> [maxDate] [page]
										.then(
											ArgumentBuilderRequired.<CommandSource, String>argument("minDate", ArgumentTypeString.word())
												.executes(context -> {
													IntegerCoordinates pos1 = context.getArgument("pos1", IntegerCoordinates.class);
													IntegerCoordinates pos2 = context.getArgument("pos2", IntegerCoordinates.class);
													boolean suspicious = ArgumentTypeBool.getBool(context, "suspiciousOnly");
													String minStr = ArgumentTypeString.getString(context, "minDate");
													return executeAreaCheck(context.getSource(), pos1, pos2, suspicious, minStr, null, 1);
												})
												.then(
													ArgumentBuilderRequired.<CommandSource, Integer>argument("minOnlyPage", ArgumentTypeInteger.integer(1))
														.executes(context -> {
															IntegerCoordinates pos1 = context.getArgument("pos1", IntegerCoordinates.class);
															IntegerCoordinates pos2 = context.getArgument("pos2", IntegerCoordinates.class);
															boolean suspicious = ArgumentTypeBool.getBool(context, "suspiciousOnly");
															String minStr = ArgumentTypeString.getString(context, "minDate");
															int page = ArgumentTypeInteger.getInteger(context, "minOnlyPage");
															return executeAreaCheck(context.getSource(), pos1, pos2, suspicious, minStr, null, page);
														})
												)
												.then(
													ArgumentBuilderRequired.<CommandSource, String>argument("maxDate", ArgumentTypeString.word())
														.executes(context -> {
															IntegerCoordinates pos1 = context.getArgument("pos1", IntegerCoordinates.class);
															IntegerCoordinates pos2 = context.getArgument("pos2", IntegerCoordinates.class);
															boolean suspicious = ArgumentTypeBool.getBool(context, "suspiciousOnly");
															String minStr = ArgumentTypeString.getString(context, "minDate");
															String maxStr = ArgumentTypeString.getString(context, "maxDate");
															return executeAreaCheck(context.getSource(), pos1, pos2, suspicious, minStr, maxStr, 1);
														})
														.then(
															ArgumentBuilderRequired.<CommandSource, Integer>argument("page", ArgumentTypeInteger.integer(1))
																.executes(context -> {
																	IntegerCoordinates pos1 = context.getArgument("pos1", IntegerCoordinates.class);
																	IntegerCoordinates pos2 = context.getArgument("pos2", IntegerCoordinates.class);
																	boolean suspicious = ArgumentTypeBool.getBool(context, "suspiciousOnly");
																	String minStr = ArgumentTypeString.getString(context, "minDate");
																	String maxStr = ArgumentTypeString.getString(context, "maxDate");
																	int page = ArgumentTypeInteger.getInteger(context, "page");
																	return executeAreaCheck(context.getSource(), pos1, pos2, suspicious, minStr, maxStr, page);
																})
														)
												)
										)
								)
						)
				)
		);
	}

	private int executeAreaCheck(
		CommandSource source,
		IntegerCoordinates pos1Coords, IntegerCoordinates pos2Coords,
		boolean suspiciousOnly,
		String minDateStr, String maxDateStr,
		int page
	) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		// Resolve each coordinate axis individually from the sender's position
		// Pass 'false' to getY so relative Y coordinates (~0) align to feet/block level rather than head height
		int x1 = pos1Coords.getX(source);
		int y1 = pos1Coords.getY(source, false);
		int z1 = pos1Coords.getZ(source);

		int x2 = pos2Coords.getX(source);
		int y2 = pos2Coords.getY(source, false);
		int z2 = pos2Coords.getZ(source);

		long minTs = 0L;
		long maxTs = System.currentTimeMillis() / 1000L;

		if (minDateStr != null) {
			minTs = parseTimestamp(minDateStr, false);
			if (minTs == -1) {
				source.sendMessage("§cInvalid min date! Use YYYY-MM-DD or YYYY-MM-DD-HH:mm");
				return 0;
			}
		}

		if (maxDateStr != null) {
			maxTs = parseTimestamp(maxDateStr, true);
			if (maxTs == -1) {
				source.sendMessage("§cInvalid max date! Use YYYY-MM-DD or YYYY-MM-DD-HH:mm");
				return 0;
			}
		}

		List<BlockLogEntry> logs = DatabaseManager.getAreaLogs(
			"world",
			x1, y1, z1,
			x2, y2, z2,
			minTs, maxTs,
			page, PAGE_SIZE,
			suspiciousOnly
		);

		String filterTag = suspiciousOnly ? " §c[SUSPICIOUS ONLY]" : "";
		source.sendMessage("§6> TableWatch Area History §e(Page " + page + ")" + filterTag);
		if (logs.isEmpty()) {
			source.sendMessage("§7No logs found for this area on page " + page + ".");
			return 1;
		}

		for (BlockLogEntry log : logs) {
			String dateStr = DATE_FORMAT.format(new Date(log.timestamp * 1000L));
			String actionColor = getActionColor(log.action);
			String blockName = getReadableBlockName(log.blockId);

			if (isSuspiciousBlock(log.blockId)) {
				blockName = "§c" + blockName + "§7";
			}

			source.sendMessage(String.format("§8[%s] §b%s %s%s §7%s §8(%d, %d, %d)",
				dateStr, log.playerName, actionColor, log.action.name(), blockName, log.x, log.y, log.z));
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
