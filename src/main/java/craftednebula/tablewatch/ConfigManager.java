package craftednebula.tablewatch;

import java.io.*;
import java.util.Properties;

public class ConfigManager {
	private static final Properties properties = new Properties();
	private static File configFile;

	// Default: Prune logs older than 30 days (in seconds: 30 * 24 * 60 * 60 = 2592000)
	public static long maxLogAgeSeconds = 1036800L;
	// Default: How often to run the prune check (every 24 hours = 86400 seconds)
	public static long pruneIntervalSeconds = 1800L;
	// Default: Whether automatic pruning is enabled
	public static boolean autoPruneEnabled = true;

	public static void initialize(File dataDirectory) {
		if (!dataDirectory.exists()) {
			dataDirectory.mkdirs();
		}
		configFile = new File(dataDirectory, "tablewatch.properties");

		if (!configFile.exists()) {
			createDefaultConfig();
		} else {
			loadConfig();
		}
	}

	private static void createDefaultConfig() {
		properties.setProperty("auto_prune_enabled", "true");
		properties.setProperty("max_log_age_days", "12");
		properties.setProperty("prune_interval_minutes", "30");

		save();
		System.out.println("[TableWatch] Created default configuration file.");
	}

	private static void loadConfig() {
		try (InputStream input = new FileInputStream(configFile)) {
			properties.load(input);

			autoPruneEnabled = Boolean.parseBoolean(properties.getProperty("auto_prune_enabled", "true"));

			long days = Long.parseLong(properties.getProperty("max_log_age_days", "12"));
			maxLogAgeSeconds = days * 86400L;

			long minutes = Long.parseLong(properties.getProperty("prune_interval_minutes", "30"));
			pruneIntervalSeconds = minutes * 60L;

			System.out.println("[TableWatch] Configuration loaded successfully.");
		} catch (Exception e) {
			System.err.println("[TableWatch] Failed to load config! Using default values.");
			e.printStackTrace();
		}
	}
	// i dunno how LOGGER.info works T_T
	private static void save() {
		try (OutputStream output = new FileOutputStream(configFile)) {
			properties.store(output, "TableWatch Configuration File");
		} catch (IOException e) {
			System.err.println("[TableWatch] Failed to save configuration!");
			e.printStackTrace();
		}
	}
}
