package craftednebula.tablewatch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import craftednebula.tablewatch.DatabaseManager;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.net.command.CommandSource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CommandClearDb implements CommandManager.CommandRegistry {

	private static final Map<String, Long> PENDING_CONFIRMATIONS = new ConcurrentHashMap<>();
	private static final long CONFIRMATION_WINDOW_MS = 15000L; // 15 seconds

	public CommandClearDb() {
	}

	@Override
	public void register(CommandDispatcher<CommandSource> dispatcher) {
		registerCommandNode(dispatcher);
	}

	private void registerCommandNode(CommandDispatcher<CommandSource> dispatcher) {
		dispatcher.register(
			ArgumentBuilderLiteral.<CommandSource>literal("cleardb")
				.requires(CommandSource::hasAdmin)
				.executes(context -> {
					CommandSource source = context.getSource();
					Player player = source.getSender();

					if (player == null) {
						source.sendMessage("§cThis command can only be executed by an in-game player.");
						return 0;
					}

					String playerName = player.username;
					long currentTime = System.currentTimeMillis();
					Long firstAttemptTime = PENDING_CONFIRMATIONS.get(playerName);

					if (firstAttemptTime != null && (currentTime - firstAttemptTime) <= CONFIRMATION_WINDOW_MS) {
						PENDING_CONFIRMATIONS.remove(playerName);
						source.sendMessage("§e[TableWatch] §cWiping database...");
						DatabaseManager.clearDatabase();
						source.sendMessage("§a[TableWatch] Database cleared successfully!");
						return 1;
					} else {
						// First attempt or expired attempt
						PENDING_CONFIRMATIONS.put(playerName, currentTime);
						source.sendMessage("§c§l[WARNING] §r§cThis will permanently delete ALL TableWatch block logs!");
						source.sendMessage("§eType §6/cleardb §eagain within §c15 seconds §eto confirm.");
						return 1;
					}
				})
		);
	}
}
