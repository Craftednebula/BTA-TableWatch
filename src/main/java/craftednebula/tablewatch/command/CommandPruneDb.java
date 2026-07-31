package craftednebula.tablewatch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentTypeInteger;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.builder.ArgumentBuilderRequired;
import craftednebula.tablewatch.ConfigManager;
import craftednebula.tablewatch.DatabaseManager;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.net.command.CommandSource;

public class CommandPruneDb implements CommandManager.CommandRegistry {

	@Override
	public void register(CommandDispatcher<CommandSource> dispatcher) {
		registerCommandNode(dispatcher, "prunedb");
	}

	private void registerCommandNode(CommandDispatcher<CommandSource> dispatcher, String name) {
		dispatcher.register(
			ArgumentBuilderLiteral.<CommandSource>literal(name)
				.requires(CommandSource::hasAdmin)
				// Default: Prune using the age set in the config file
				.executes(context -> {
					CommandSource source = context.getSource();
					long days = ConfigManager.maxLogAgeSeconds / 86400L;
					source.sendMessage("§e[TableWatch] Pruning logs older than " + days + " days...");

					int deleted = DatabaseManager.pruneOldLogs(ConfigManager.maxLogAgeSeconds);
					source.sendMessage("§a[TableWatch] Successfully pruned " + deleted + " old entries!");
					return 1;
				})
				// Custom age: /prunedb <days>
				.then(
					ArgumentBuilderRequired.<CommandSource, Integer>argument("days", ArgumentTypeInteger.integer(1))
						.executes(context -> {
							CommandSource source = context.getSource();
							int days = ArgumentTypeInteger.getInteger(context, "days");
							long ageSeconds = days * 86400L;

							source.sendMessage("§e[TableWatch] Pruning logs older than " + days + " days...");
							int deleted = DatabaseManager.pruneOldLogs(ageSeconds);
							source.sendMessage("§a[TableWatch] Successfully pruned " + deleted + " old entries!");
							return 1;
						})
				)
		);
	}
}
