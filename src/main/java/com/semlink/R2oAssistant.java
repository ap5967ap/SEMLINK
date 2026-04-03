package com.semlink;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class R2oAssistant {
    private static final String AICTE_NS = "https://semlink.example.org/aicte#";
    private static final Map<String, List<String>> CLASS_HINTS = Map.of(
        "University", List.of("university", "hub", "parentuniversity"),
        "College", List.of("college", "institute", "campus", "affiliatedcollege"),
        "Student", List.of("student", "learner", "pupil", "studentinfo"),
        "Course", List.of("course", "module", "subject", "paper"),
        "Department", List.of("department", "school", "division", "faculty"),
        "Program", List.of("program", "track", "plan")
    );

    private static final Set<String> NAME_HINTS = Set.of("name", "title", "label");

    public DraftResult buildDraft(String exampleName, SqlInputParser.SqlSchema schema) {
        List<TablePlan> tablePlans = new ArrayList<>();
        for (SqlInputParser.TableDefinition table : schema.tables().values()) {
            tablePlans.add(planTable(table, schema));
        }
        tablePlans.sort(Comparator.comparing(plan -> plan.table().name()));

        String draftMapping = writeDraftMapping(exampleName, tablePlans);
        String reviewReport = writeReviewReport(exampleName, tablePlans);
        String schemaProfile = writeSchemaProfile(tablePlans);
        return new DraftResult(draftMapping, reviewReport, schemaProfile);
    }

    private TablePlan planTable(SqlInputParser.TableDefinition table, SqlInputParser.SqlSchema schema) {
        Candidate classMatch = guessClass(table.name());
        String idColumn = guessIdColumn(table, classMatch.localName());
        String nameColumn = guessNameColumn(table);

        List<LiteralMapping> literals = new ArrayList<>();
        if (idColumn != null) {
            literals.add(new LiteralMapping(idColumn, "id", 0.99, "identifier-column"));
        }
        if (nameColumn != null) {
            literals.add(new LiteralMapping(nameColumn, "name", 0.96, "label-column"));
        }
        if ("Student".equals(classMatch.localName())) {
            String departmentColumn = findFirstMatchingColumn(table, List.of("department_name", "dept_name", "department", "dept"));
            if (departmentColumn != null && !departmentColumn.equals(nameColumn)) {
                literals.add(new LiteralMapping(departmentColumn, "department", 0.93, "student-department-column"));
            }
        }

        List<ObjectMapping> objects = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> omittedColumns = new ArrayList<>();

        for (Map.Entry<String, SqlInputParser.ForeignKey> entry : table.foreignKeys().entrySet()) {
            String localColumn = entry.getKey();
            SqlInputParser.ForeignKey foreignKey = entry.getValue();
            Candidate targetClass = guessClass(foreignKey.targetTable());
            if ("College".equals(classMatch.localName()) && "University".equals(targetClass.localName())) {
                objects.add(new ObjectMapping(
                    localColumn,
                    "belongsToUniversity",
                    foreignKey.targetTable(),
                    false,
                    0.95,
                    "college-to-university-foreign-key"
                ));
            } else if ("Student".equals(classMatch.localName()) && "College".equals(targetClass.localName())) {
                objects.add(new ObjectMapping(
                    localColumn,
                    "studiesAt",
                    foreignKey.targetTable(),
                    false,
                    0.95,
                    "student-to-college-foreign-key"
                ));
            } else if ("Student".equals(classMatch.localName()) && "Department".equals(targetClass.localName())) {
                objects.add(new ObjectMapping(
                    localColumn,
                    "memberOfDepartment",
                    foreignKey.targetTable(),
                    false,
                    0.90,
                    "student-to-department-foreign-key"
                ));
            } else if ("Course".equals(classMatch.localName()) && "College".equals(targetClass.localName())) {
                objects.add(new ObjectMapping(
                    localColumn,
                    "offersCourse",
                    foreignKey.targetTable(),
                    true,
                    0.89,
                    "reverse-course-offering-foreign-key"
                ));
            } else {
                notes.add("Foreign key " + localColumn + " -> " + foreignKey.targetTable()
                    + " was not mapped into the compact AICTE core automatically.");
            }
        }

        for (String column : table.columns()) {
            if (column.equals(idColumn) || column.equals(nameColumn) || table.foreignKeys().containsKey(column)) {
                continue;
            }
            if ("Student".equals(classMatch.localName()) && isDepartmentColumn(column)) {
                continue;
            }
            omittedColumns.add(column);
        }

        if (omittedColumns.isEmpty()) {
            notes.add("All business columns are covered by the current AICTE core draft.");
        } else {
            notes.add("Columns omitted from the draft because they are outside the compact AICTE core: "
                + String.join(", ", omittedColumns));
        }

        double confidence = averageConfidence(classMatch.score(), literals, objects);
        return new TablePlan(table, classMatch, idColumn, nameColumn, literals, objects, omittedColumns, notes, confidence);
    }

    private String writeDraftMapping(String exampleName, List<TablePlan> plans) {
        StringBuilder builder = new StringBuilder();
        builder.append("@prefix rr: <http://www.w3.org/ns/r2rml#> .\n");
        builder.append("@prefix aicte: <").append(AICTE_NS).append("> .\n\n");
        builder.append("# Draft mapping generated by the SEMLINK assisted R2O workflow.\n");
        builder.append("# Review this file before using it for production conversion.\n\n");

        for (TablePlan plan : plans) {
            if (plan.classMatch().score() < 0.55 || plan.idColumn() == null) {
                continue;
            }
            String entitySlug = slug(plan.classMatch().localName());
            String mapName = plan.classMatch().localName() + "DraftMap";
            builder.append("<#").append(mapName).append("> a rr:TriplesMap ;\n");
            builder.append("  rr:logicalTable [ rr:tableName \"").append(plan.table().name()).append("\" ] ;\n");
            builder.append("  rr:subjectMap [\n");
            builder.append("    rr:template \"https://semlink.example.org/r2o/").append(entitySlug)
                .append("/{").append(plan.idColumn()).append("}\" ;\n");
            builder.append("    rr:class aicte:").append(plan.classMatch().localName()).append('\n');
            builder.append("  ]");

            List<String> predicateBlocks = new ArrayList<>();
            for (LiteralMapping literal : plan.literals()) {
                predicateBlocks.add(literalBlock(literal.column(), literal.propertyLocalName()));
            }
            for (ObjectMapping mapping : plan.objects()) {
                if (!mapping.reverseSubject()) {
                    Candidate targetClass = guessClass(mapping.targetTable());
                    predicateBlocks.add(templateBlock(
                        mapping.predicateLocalName(),
                        "https://semlink.example.org/r2o/" + slug(targetClass.localName()) + "/{" + mapping.column() + "}"
                    ));
                }
            }

            if (predicateBlocks.isEmpty()) {
                builder.append(" .\n\n");
            } else {
                builder.append(" ;\n");
                builder.append(String.join(" ;\n", predicateBlocks));
                builder.append(" .\n\n");
            }

            for (ObjectMapping mapping : plan.objects()) {
                if (!mapping.reverseSubject()) {
                    continue;
                }
                Candidate targetClass = guessClass(mapping.targetTable());
                builder.append("<#").append(targetClass.localName()).append(plan.classMatch().localName()).append("ReverseMap> a rr:TriplesMap ;\n");
                builder.append("  rr:logicalTable [ rr:tableName \"").append(plan.table().name()).append("\" ] ;\n");
                builder.append("  rr:subjectMap [\n");
                builder.append("    rr:template \"https://semlink.example.org/r2o/")
                    .append(slug(targetClass.localName())).append("/{").append(mapping.column()).append("}\"\n");
                builder.append("  ] ;\n");
                builder.append(templateBlock(mapping.predicateLocalName(),
                    "https://semlink.example.org/r2o/" + slug(plan.classMatch().localName()) + "/{" + plan.idColumn() + "}"));
                builder.append(" .\n\n");
            }
        }

        builder.append("# Example name: ").append(exampleName).append('\n');
        return builder.toString();
    }

    private String literalBlock(String column, String propertyLocalName) {
        return "  rr:predicateObjectMap [\n"
            + "    rr:predicate aicte:" + propertyLocalName + " ;\n"
            + "    rr:objectMap [ rr:column \"" + column + "\" ]\n"
            + "  ]";
    }

    private String templateBlock(String predicateLocalName, String template) {
        return "  rr:predicateObjectMap [\n"
            + "    rr:predicate aicte:" + predicateLocalName + " ;\n"
            + "    rr:objectMap [ rr:template \"" + template + "\" ]\n"
            + "  ]";
    }

    private String writeReviewReport(String exampleName, List<TablePlan> plans) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Assisted R2O Review Report\n\n");
        builder.append("Example: `").append(exampleName).append("`\n\n");
        builder.append("This report was generated by the local assisted R2O workflow.\n");
        builder.append("It behaves like a lightweight agentic reviewer: it inspects the schema, scores candidate AICTE mappings, drafts R2RML, and leaves human checkpoints before conversion.\n\n");
        builder.append("## Recommended Flow\n\n");
        builder.append("1. Run `mvn -q exec:java -Dexec.args=\"r2o assist ").append(exampleName).append("\"`\n");
        builder.append("2. Review `draft-r2rml-mapping.ttl`\n");
        builder.append("3. Edit `refined-r2rml-mapping.ttl` if needed\n");
        builder.append("4. Run `mvn -q exec:java -Dexec.args=\"r2o generate ").append(exampleName).append(" refined\"`\n\n");
        builder.append("## Table Decisions\n\n");

        for (TablePlan plan : plans) {
            builder.append("### ").append(plan.table().name()).append('\n');
            builder.append("- Suggested AICTE class: `aicte:").append(plan.classMatch().localName()).append("`\n");
            builder.append("- Confidence: ").append(String.format(Locale.ROOT, "%.2f", plan.confidence())).append('\n');
            builder.append("- Identifier column: `").append(plan.idColumn() == null ? "not-detected" : plan.idColumn()).append("`\n");
            builder.append("- Label column: `").append(plan.nameColumn() == null ? "not-detected" : plan.nameColumn()).append("`\n");
            if (!plan.literals().isEmpty()) {
                builder.append("- Literal mappings: ");
                builder.append(plan.literals().stream()
                    .map(mapping -> mapping.column() + " -> aicte:" + mapping.propertyLocalName())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("none"));
                builder.append('\n');
            }
            if (!plan.objects().isEmpty()) {
                builder.append("- Relationship mappings: ");
                builder.append(plan.objects().stream()
                    .map(mapping -> mapping.column() + " -> aicte:" + mapping.predicateLocalName()
                        + (mapping.reverseSubject() ? " (reverse)" : ""))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("none"));
                builder.append('\n');
            }
            for (String note : plan.notes()) {
                builder.append("- Review note: ").append(note).append('\n');
            }
            builder.append('\n');
        }

        builder.append("## Human Refinement Checklist\n\n");
        builder.append("- Confirm every table really represents the suggested AICTE class.\n");
        builder.append("- Confirm `*_name` and `*_title` columns are semantically safe to map to `aicte:name`.\n");
        builder.append("- Add local extensions for omitted columns such as `city`, `credits`, or accreditation fields if your deployment needs them.\n");
        builder.append("- Review any reverse relationship drafts, especially `aicte:offersCourse`, because they depend on business meaning rather than syntax alone.\n");
        return builder.toString();
    }

    private String writeSchemaProfile(List<TablePlan> plans) {
        StringBuilder builder = new StringBuilder();
        builder.append("table\tsuggested_class\tconfidence\tid_column\tname_column\tomitted_columns\n");
        for (TablePlan plan : plans) {
            builder.append(plan.table().name()).append('\t')
                .append(plan.classMatch().localName()).append('\t')
                .append(String.format(Locale.ROOT, "%.2f", plan.confidence())).append('\t')
                .append(plan.idColumn() == null ? "" : plan.idColumn()).append('\t')
                .append(plan.nameColumn() == null ? "" : plan.nameColumn()).append('\t')
                .append(String.join(",", plan.omittedColumns())).append('\n');
        }
        return builder.toString();
    }

    private Candidate guessClass(String rawName) {
        String normalized = normalize(rawName)
            .replace("master", "")
            .replace("table", "")
            .replace("tbl", "");

        return CLASS_HINTS.entrySet().stream()
            .map(entry -> scoreClass(entry.getKey(), normalized, entry.getValue()))
            .max(Comparator.comparingDouble(Candidate::score))
            .orElse(new Candidate("Student", 0.0, "fallback"));
    }

    private Candidate scoreClass(String classLocalName, String normalizedInput, List<String> hints) {
        String normalizedClass = normalize(classLocalName);
        for (String hint : hints) {
            String normalizedHint = normalize(hint);
            if (normalizedInput.contains(normalizedHint) || normalizedHint.contains(normalizedInput)) {
                return new Candidate(classLocalName, 0.95, "keyword");
            }
        }
        double levenshtein = similarity(normalizedInput, normalizedClass);
        return new Candidate(classLocalName, levenshtein, "levenshtein");
    }

    private String guessIdColumn(SqlInputParser.TableDefinition table, String classLocalName) {
        String exactId = findFirstMatchingColumn(table, List.of(slug(classLocalName) + "_id"));
        if (exactId != null) {
            return exactId;
        }
        return table.columns().stream()
            .filter(column -> column.toLowerCase(Locale.ROOT).endsWith("_id"))
            .findFirst()
            .orElse(null);
    }

    private String guessNameColumn(SqlInputParser.TableDefinition table) {
        for (String column : table.columns()) {
            String normalized = normalize(column);
            for (String hint : NAME_HINTS) {
                if (normalized.contains(hint)) {
                    return column;
                }
            }
        }
        return null;
    }

    private String findFirstMatchingColumn(SqlInputParser.TableDefinition table, List<String> matches) {
        for (String column : table.columns()) {
            String normalizedColumn = normalize(column);
            for (String match : matches) {
                if (normalizedColumn.equals(normalize(match))) {
                    return column;
                }
            }
        }
        return null;
    }

    private boolean isDepartmentColumn(String column) {
        String normalized = normalize(column);
        return normalized.contains("department") || normalized.contains("dept");
    }

    private double averageConfidence(double classConfidence, List<LiteralMapping> literals, List<ObjectMapping> objects) {
        double total = classConfidence;
        int count = 1;
        for (LiteralMapping literal : literals) {
            total += literal.score();
            count++;
        }
        for (ObjectMapping object : objects) {
            total += object.score();
            count++;
        }
        return total / count;
    }

    private String normalize(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1 $2")
            .replaceAll("[^A-Za-z0-9]+", "")
            .toLowerCase(Locale.ROOT);
    }

    private double similarity(String left, String right) {
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) {
            return 1.0;
        }
        int distance = levenshtein(left, right);
        return 1.0 - ((double) distance / maxLength);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }

        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int substitution = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                    Math.min(current[column - 1] + 1, previous[column] + 1),
                    previous[column - 1] + substitution
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    public record DraftResult(String draftMapping, String reviewReport, String schemaProfile) {
    }

    private record Candidate(String localName, double score, String method) {
    }

    private record LiteralMapping(String column, String propertyLocalName, double score, String method) {
    }

    private record ObjectMapping(
        String column,
        String predicateLocalName,
        String targetTable,
        boolean reverseSubject,
        double score,
        String method
    ) {
    }

    private record TablePlan(
        SqlInputParser.TableDefinition table,
        Candidate classMatch,
        String idColumn,
        String nameColumn,
        List<LiteralMapping> literals,
        List<ObjectMapping> objects,
        List<String> omittedColumns,
        List<String> notes,
        double confidence
    ) {
    }
}
