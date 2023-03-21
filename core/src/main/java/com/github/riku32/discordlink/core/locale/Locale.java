package com.github.riku32.discordlink.core.locale;

import java.util.Properties;

public class Locale {
    private final Properties properties;
    private final String defaultLocale = "en_US"; // Set default locale to en_US

    public Locale(Properties properties) {
        this.properties = properties;
        if (!properties.containsKey("default")) {
            // If no default locale is specified, set it to the default value
            properties.setProperty("default", defaultLocale);
        }
    }

    public LocaleElement getElement(String identifier) {
        return new LocaleElement(properties.getOrDefault(identifier,
                "No identifier found in locale").toString());
    }
}