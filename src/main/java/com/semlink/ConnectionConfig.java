package com.semlink;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-neutral connection descriptor used by CLI, adapters, and future
 * REST API layers. Secrets are kept as fields here for local demos; production
 * deployments should back this with a secret manager.
 */
public record ConnectionConfig(
    String id,
    String type,
    String host,
    int port,
    String database,
    String username,
    String password,
    boolean ssl,
    int timeoutSeconds,
    Map<String, String> options
) {
    public ConnectionConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        options = Map.copyOf(options == null ? Map.of() : options);
    }

    public static ConnectionConfig owlFile(String id, String path) {
        return new ConnectionConfig(id, "owl", "", 0, path, "", "", false, 30, Map.of("path", path));
    }

    public static ConnectionConfig csv(String id, String path) {
        return new ConnectionConfig(id, "csv", "", 0, path, "", "", false, 30, Map.of("path", path));
    }

    public static ConnectionConfig mysql(String id, String host, int port, String database, String username, String password) {
        return new ConnectionConfig(id, "mysql", host, port, database, username, password, false, 30, Map.of());
    }

    public static ConnectionConfig mongo(String id, String uri) {
        return new ConnectionConfig(id, "mongodb", "", 0, "", "", "", false, 30, Map.of("uri", uri));
    }

    public static ConnectionConfig neo4j(String id, String uri, String username, String password) {
        return new ConnectionConfig(id, "neo4j", "", 0, "", username, password, false, 30, Map.of("uri", uri));
    }

    public static ConnectionConfig redis(String id, String host, int port) {
        return new ConnectionConfig(id, "redis", host, port, "", "", "", false, 30, Map.of());
    }

    public Optional<String> option(String name) {
        return Optional.ofNullable(options.get(name));
    }

    public String jdbcUrl() {
        if ("sqlite".equalsIgnoreCase(type)) {
            return "jdbc:sqlite:" + database;
        }
        return "jdbc:" + type + "://" + host + ":" + port + "/" + database;
    }

    public String toJson() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", id);
        fields.put("type", type);
        fields.put("host", host);
        fields.put("port", Integer.toString(port));
        fields.put("database", database);
        fields.put("username", username);
        fields.put("ssl", Boolean.toString(ssl));
        fields.put("timeoutSeconds", Integer.toString(timeoutSeconds));
        fields.putAll(options);
        StringBuilder json = new StringBuilder("{");
        int index = 0;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (index++ > 0) {
                json.append(',');
            }
            json.append('"').append(escape(entry.getKey())).append("\":\"")
                .append(escape(entry.getValue())).append('"');
        }
        json.append('}');
        return json.toString();
    }

    public static ConnectionConfig fromJson(String json) {
        Map<String, String> values = parseFlatJson(json);
        Map<String, String> options = new LinkedHashMap<>(values);
        options.keySet().removeAll(List.of("id", "type", "host", "port", "database", "username", "ssl", "timeoutSeconds"));
        return new ConnectionConfig(
            values.getOrDefault("id", values.getOrDefault("type", "source")),
            values.getOrDefault("type", "owl"),
            values.getOrDefault("host", ""),
            parseInt(values.getOrDefault("port", "0")),
            values.getOrDefault("database", ""),
            values.getOrDefault("username", ""),
            "",
            Boolean.parseBoolean(values.getOrDefault("ssl", "false")),
            parseInt(values.getOrDefault("timeoutSeconds", "30")),
            options
        );
    }

    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        String text = json == null ? "" : json.trim();
        if (text.startsWith("{")) {
            text = text.substring(1);
        }
        if (text.endsWith("}")) {
            text = text.substring(0, text.length() - 1);
        }
        for (String pair : text.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            int colon = pair.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = unquote(pair.substring(0, colon).trim());
            String value = unquote(pair.substring(colon + 1).trim());
            values.put(key, value);
        }
        return values;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
