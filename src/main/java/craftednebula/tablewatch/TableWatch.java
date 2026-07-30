package craftednebula.tablewatch;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.HalpLibe;
import turniplabs.halplibe.event.defs.CommonEvents;
import turniplabs.halplibe.util.dependency.Key;
import net.minecraft.core.net.command.CommandManager;

import craftednebula.tablewatch.command.*;
import craftednebula.tablewatch.command.CommandCheck;
import java.io.File;

public class TableWatch implements ModInitializer {

	public static final String MOD_ID = HalpLibe.registerMod("tablewatch", true);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {

		CommonEvents.BEFORE_GAME_START.listen(Key.of(MOD_ID), this::beforeGameStart);
		CommonEvents.AFTER_GAME_START.listen(Key.of(MOD_ID), this::afterGameStart);

		LOGGER.info("TableWatch initializing...");
		CommandManager.registerServerCommand(new CommandGetRecent());
		CommandManager.registerServerCommand(new CommandCheck());
		CommandManager.registerServerCommand(new CommandCheckDate());
		CommandManager.registerServerCommand(new CommandClearDb());
	}

	public void beforeGameStart() {
		LOGGER.info("TableWatch async block logging started successfully.");
	}

	public void afterGameStart() {
		File dataDir = new File("config/" + MOD_ID);
		DatabaseManager.initialize(dataDir);


		Runtime.getRuntime().addShutdownHook(new Thread(DatabaseManager::shutdown));





	}

	/**
	 * Clean helper method so our Mixins can log block changes from anywhere.
	 */
	public static void logBlockChange(String playerName, BlockLogEntry.Action action, String worldName, int x, int y, int z, int blockId, int blockMeta, int oldBlockId, int oldBlockMeta) {
		DatabaseManager.queue(new BlockLogEntry(playerName, action, worldName, x, y, z, blockId, blockMeta, oldBlockId, oldBlockMeta));
	}
}
