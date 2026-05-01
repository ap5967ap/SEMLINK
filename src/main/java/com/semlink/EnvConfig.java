package com.semlink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class EnvConfig {
    private EnvConfig() {
    }

    public static String getGeminiApiKey() {
        String apiKey = firstNonBlank(
            System.getenv("GEMINI_API_KEY"),
            System.getenv("GEMINI_KEY"),
            loadEnvFile("GEMINI_API_KEY"),
            loadEnvFile("GEMINI_KEY")
        );
        return apiKey == null ? null : stripQuotes(apiKey.trim());
    }

    public static String loadEnvFile(String key) {
        try {
            Path envFile = Path.of(".env");
            if (!Files.exists(envFile)) {
                return null;
            }
            List<String> lines = Files.readAllLines(envFile);
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                String envKey = parts[0].trim();
                if ("export".equals(envKey)) {
                    String remainder = parts[1].trim();
                    int nextEquals = remainder.indexOf('=');
                    if (nextEquals < 0) {
                        continue;
                    }
                    envKey = remainder.substring(0, nextEquals).trim();
                    if (!envKey.equals(key)) {
                        continue;
                    }
                    return stripQuotes(remainder.substring(nextEquals + 1).trim());
                }
                if (envKey.equals(key)) {
                    return stripQuotes(parts[1].trim());
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
