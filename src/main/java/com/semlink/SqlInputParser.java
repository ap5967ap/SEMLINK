package com.semlink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlInputParser {
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
        "(?is)CREATE\\s+TABLE\\s+(\\w+)\\s*\\((.*?)\\);"
    );

    private static final Pattern INSERT_PATTERN = Pattern.compile(
        "(?is)INSERT\\s+INTO\\s+(\\w+)\\s*\\((.*?)\\)\\s*VALUES\\s*(.*?);"
    );

    private static final Pattern FOREIGN_KEY_PATTERN = Pattern.compile(
        "(?is)FOREIGN\\s+KEY\\s*\\(([^)]+)\\)\\s+REFERENCES\\s+(\\w+)\\s*\\(([^)]+)\\)"
    );

    public SqlSchema parseSchema(String schemaSql) {
        Map<String, TableDefinition> tables = new LinkedHashMap<>();
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(schemaSql);
        while (matcher.find()) {
            String tableName = matcher.group(1).trim();
            String body = matcher.group(2);
            tables.put(tableName, parseTable(tableName, body));
        }
        return new SqlSchema(tables);
    }

    public SqlData parseData(String dataSql) {
        Map<String, List<Map<String, Object>>> rowsByTable = new LinkedHashMap<>();
        Matcher matcher = INSERT_PATTERN.matcher(dataSql);
        while (matcher.find()) {
            String tableName = matcher.group(1).trim();
            List<String> columns = splitCsv(matcher.group(2));
            List<String> tupleBodies = splitTuples(matcher.group(3));
            List<Map<String, Object>> rows = rowsByTable.computeIfAbsent(tableName, key -> new ArrayList<>());
            for (String tupleBody : tupleBodies) {
                List<String> values = splitTupleValues(tupleBody);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int index = 0; index < columns.size() && index < values.size(); index++) {
                    row.put(columns.get(index), parseValue(values.get(index)));
                }
                rows.add(row);
            }
        }
        return new SqlData(rowsByTable);
    }

    private TableDefinition parseTable(String tableName, String body) {
        List<String> lines = splitDefinitionLines(body);
        List<String> columns = new ArrayList<>();
        Map<String, ForeignKey> foreignKeys = new LinkedHashMap<>();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            Matcher foreignKeyMatcher = FOREIGN_KEY_PATTERN.matcher(line);
            if (foreignKeyMatcher.find()) {
                String localColumn = foreignKeyMatcher.group(1).trim();
                String targetTable = foreignKeyMatcher.group(2).trim();
                String targetColumn = foreignKeyMatcher.group(3).trim();
                foreignKeys.put(localColumn, new ForeignKey(localColumn, targetTable, targetColumn));
                continue;
            }
            if (line.regionMatches(true, 0, "PRIMARY KEY", 0, "PRIMARY KEY".length())) {
                continue;
            }
            String[] parts = line.split("\\s+", 2);
            if (parts.length > 0) {
                columns.add(parts[0].trim());
            }
        }
        return new TableDefinition(tableName, columns, foreignKeys);
    }

    private List<String> splitDefinitionLines(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenthesesDepth = 0;
        boolean inQuote = false;

        for (int index = 0; index < body.length(); index++) {
            char currentChar = body.charAt(index);
            if (currentChar == '\'') {
                inQuote = !inQuote;
            }
            if (!inQuote) {
                if (currentChar == '(') {
                    parenthesesDepth++;
                } else if (currentChar == ')') {
                    parenthesesDepth--;
                } else if (currentChar == ',' && parenthesesDepth == 0) {
                    parts.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(currentChar);
        }

        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    private List<String> splitCsv(String input) {
        List<String> values = new ArrayList<>();
        for (String value : input.split(",")) {
            values.add(value.trim());
        }
        return values;
    }

    private List<String> splitTuples(String valuesBlock) {
        List<String> tuples = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        int depth = 0;

        for (int index = 0; index < valuesBlock.length(); index++) {
            char currentChar = valuesBlock.charAt(index);
            if (currentChar == '\'') {
                inQuote = !inQuote;
            }
            if (!inQuote) {
                if (currentChar == '(') {
                    if (depth == 0) {
                        current.setLength(0);
                    } else {
                        current.append(currentChar);
                    }
                    depth++;
                    continue;
                }
                if (currentChar == ')') {
                    depth--;
                    if (depth == 0) {
                        tuples.add(current.toString().trim());
                        current.setLength(0);
                        continue;
                    }
                }
            }
            if (depth > 0) {
                current.append(currentChar);
            }
        }
        return tuples;
    }

    private List<String> splitTupleValues(String tupleBody) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;

        for (int index = 0; index < tupleBody.length(); index++) {
            char currentChar = tupleBody.charAt(index);
            if (currentChar == '\'') {
                inQuote = !inQuote;
                current.append(currentChar);
                continue;
            }
            if (currentChar == ',' && !inQuote) {
                values.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }

        if (!current.isEmpty()) {
            values.add(current.toString().trim());
        }
        return values;
    }

    private Object parseValue(String token) {
        String value = token.trim();
        if (value.equalsIgnoreCase("NULL")) {
            return null;
        }
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (value.matches("-?\\d+")) {
            return Integer.parseInt(value);
        }
        if (value.matches("-?\\d+\\.\\d+")) {
            return Double.parseDouble(value);
        }
        return value;
    }

    public record SqlSchema(Map<String, TableDefinition> tables) {
        public TableDefinition table(String tableName) {
            return tables.get(tableName);
        }
    }

    public record SqlData(Map<String, List<Map<String, Object>>> rowsByTable) {
        public List<Map<String, Object>> rows(String tableName) {
            return rowsByTable.getOrDefault(tableName, List.of());
        }
    }

    public record TableDefinition(String name, List<String> columns, Map<String, ForeignKey> foreignKeys) {
        public String normalizedName() {
            return name.toLowerCase(Locale.ROOT);
        }
    }

    public record ForeignKey(String column, String targetTable, String targetColumn) {
    }
}
