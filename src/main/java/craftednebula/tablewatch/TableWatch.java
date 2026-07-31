package craftednebula.tablewatch;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.HalpLibe;
import turniplabs.halplibe.event.defs.CommonEvents;
import turniplabs.halplibe.util.dependency.Key;
import net.minecraft.core.net.command.CommandManager;

import craftednebula.tablewatch.ConfigManager;

import craftednebula.tablewatch.command.*;

import java.io.File;

public class TableWatch implements ModInitializer {

	public static final String MOD_ID = HalpLibe.registerMod("tablewatch", true);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {

		CommonEvents.BEFORE_GAME_START.listen(Key.of(MOD_ID), this::beforeGameStart);
		CommonEvents.AFTER_GAME_START.listen(Key.of(MOD_ID), this::afterGameStart);

		LOGGER.info("TableWatch initializing...");
		File dataDir = new File("config/" + MOD_ID);
		ConfigManager.initialize(dataDir);
		CommandManager.registerServerCommand(new CommandGetRecent());
		CommandManager.registerServerCommand(new CommandCheck());
		CommandManager.registerServerCommand(new CommandGetAreaHistory());
		CommandManager.registerServerCommand(new CommandClearDb());
		CommandManager.registerServerCommand(new CommandPruneDb());
	}

	public void beforeGameStart() {
		LOGGER.info("TableWatch async block logging started successfully.");

	}

	public void afterGameStart() {
		File dataDir = new File("config/" + MOD_ID);
		ConfigManager.initialize(dataDir);
		DatabaseManager.initialize(dataDir);
		Runtime.getRuntime().addShutdownHook(new Thread(DatabaseManager::shutdown));





	}

	public static void logBlockChange(String playerName, BlockLogEntry.Action action, String worldName, int x, int y, int z, int blockId, int blockMeta) {
		DatabaseManager.queue(new BlockLogEntry(
			System.currentTimeMillis() / 1000L,
			playerName,
			action,
			worldName,
			x, y, z,
			blockId,
			blockMeta
		));
	}
}
