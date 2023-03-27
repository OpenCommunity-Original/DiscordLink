package com.github.riku32.discordlink.core.locale;

import org.simpleyaml.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.bukkit.plugin.Plugin;

/**
 * The LocaleAPI class provides a simple and efficient way to manage player
 * locales and retrieve localized messages based on the player's locale.
 */
public class DiscordLocaleAPI {
    private static final Locale DEFAULT_LOCALE = Locale.US;
    private static final List<Locale> SUPPORTED_LOCALES = new ArrayList<>();
    private static String baseName;
    private static Plugin plugin;
    private static final Map<String, YamlConfiguration> configurationCache = new ConcurrentHashMap<>();

    public DiscordLocaleAPI(Plugin plugin) {
        this.plugin = plugin;
    }

    private static CompletableFuture<YamlConfiguration> loadConfigurationAsync(File file) {
        String key = file.getAbsolutePath();
        YamlConfiguration cachedConfig = configurationCache.get(key);
        if (cachedConfig != null) {
            return CompletableFuture.completedFuture(cachedConfig);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                configurationCache.put(key, config);
                return config;
            } catch (Exception e) {
                plugin.getLogger().warning("Error loading configuration file: " + e.getMessage());
                return null;
            }
        });
    }

    public static String getMessage(Locale locale, String key, String... placeholders) {
        String lang = locale.toLanguageTag();
        File file = new File(baseName, lang + ".lang");

        CompletableFuture<YamlConfiguration> future = loadConfigurationAsync(file);
        String message = future.thenApply(config -> config.getString(key))
                .exceptionally(e -> {
                    plugin.getLogger().warning("Error getting message: " + e.getMessage());
                    return null;
                })
                .join();

        if (message == null) {
            CompletableFuture<YamlConfiguration> fallback = loadConfigurationAsync(new File(baseName, DEFAULT_LOCALE.toLanguageTag() + ".lang"));
            message = fallback.thenApply(config -> config.getString(key))
                    .exceptionally(e -> {
                        plugin.getLogger().warning("Error getting message: " + e.getMessage());
                        return null;
                    })
                    .join();
        }

        // replace placeholders
        for (int i = 0; i < placeholders.length; i += 2) {
            message = message.replace(placeholders[i], placeholders[i + 1]);
        }

        return message;
    }



    /**
     * Checks if the locale is supported.
     *
     * @param locale the locale
     * @return true if supported, false otherwise
     */
    private static boolean isLocaleSupported(Locale locale) {
        return SUPPORTED_LOCALES.contains(locale);
    }

    /**
     * Copies the Messages folder from the plugin resources to the plugin folder.
     * This is used to provide default message files.
     *
     * @param plugin The plugin
     */
    private static void copyMessages(Plugin plugin) {
        File sourceFolder = new File(plugin.getDataFolder(), "Messages");
        if (!sourceFolder.exists()) {
            sourceFolder.mkdirs();
        }

        try {
            // Open the plugin jar file as a ZipFile
            File pluginFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            ZipFile zipFile = new ZipFile(pluginFile);

            // Loop through the contents of the jar file to find the Messages folder
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith("Messages/") && !entry.isDirectory()) {
                    // Extract the file to the Messages folder in the plugin folder
                    File targetFile = new File(sourceFolder, entryName.substring("Messages/".length()));
                    if (!targetFile.getParentFile().exists()) {
                        targetFile.getParentFile().mkdirs();
                    }
                    InputStream inputStream = zipFile.getInputStream(entry);
                    Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    inputStream.close();
                }
            }

            zipFile.close();
        } catch (Exception e) {
            plugin.getLogger().warning("Error copying Messages folder from plugin resources: " + e.getMessage());
        }
    }


    /**
     * Loads the supported locales from the plugin's messages folder.
     * If no locales are found, the plugin will be disabled.
     *
     * @param plugin the plugin
     */
    public void loadSupportedLocales(Plugin plugin) {
        baseName = plugin.getDataFolder().getAbsolutePath() + File.separator + "Messages" + File.separator;
        File messagesFolder = new File(baseName);
        if (messagesFolder.listFiles() == null) {
            copyMessages(plugin);
        }
        File[] messageFiles = messagesFolder.listFiles();
        if (messageFiles != null) {
            for (File file : messageFiles) {
                String fileName = file.getName();
                if (fileName.endsWith(".lang")) {
                    String localeString = fileName.substring(0, fileName.indexOf(".lang"));
                    Locale locale = Locale.forLanguageTag(localeString.replace("_", "-"));
                    if (locale != null) {
                        SUPPORTED_LOCALES.add(locale);
                    }
                }
            }
        }
        if (SUPPORTED_LOCALES.isEmpty()) {
            plugin.getLogger().warning("Failed to load any language files.");
        }
    }

}