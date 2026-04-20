package com.semlink;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class R2oAssistant {
    private static final String AICTE_NS = "https://semlink.example.org/aicte#";
    private static final String REFINED_NS = "https://semlink.example.org/r2o/refined/";
    private static final Map<String, List<String>> CLASS_HINTS = Map.of(
        "University", List.of("university", "hub", "parentuniversity"),
        "College", List.of("college", "institute", "campus", "affiliatedcollege"),
        "Student", List.of("student", "learner", "pupil", "studentinfo"),
        "Course", List.of("course", "module", "subject", "paper"),
        "Department", List.of("department", "school", "division", "faculty"),
        "Program", List.of("program", "track", "plan")
    );
    private static final Set<String> NAME_HINTS = Set.of("name", "title", "label");

    public DraftResult refine(String exampleName,
                              SqlInputParser.SqlSchema schema,
                              Model rawModel,
                              Map<String, String> primaryKeys) {
        List<TablePlan> tablePlans = new ArrayList<>();
        for (SqlInputParser.TableDefinition table : schema.tables().values()) {
            tablePlans.add(planTable(table));
        }
        tablePlans.sort(Comparator.comparing(plan -> plan.table().name()));

        Model refinedModel = buildRefinedModel(schema, rawModel, primaryKeys, tablePlans);
        String reviewReport = writeReviewReport(exampleName, tablePlans);
        String schemaProfile = writeSchemaProfile(tablePlans);
        return new DraftResult(refinedModel, reviewReport, schemaProfile);
    }

    private TablePlan planTable(SqlInputParser.TableDefinition table) {
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
                objects.add(new ObjectMapping(localColumn, "belongsToUniversity", foreignKey.targetTable(), false, 0.95, "college-to-university-foreign-key"));
            } else if ("Student".equals(classMatch.localName()) && "College".equals(targetClass.localName())) {
                objects.add(new ObjectMapping(localColumn, "studiesAt", foreignKey.targetTable(), false, 0.95, "student-to-college-foreign-key"));
            } else if ("Student".equals(classMatch.localName()) && "Department".equals(targetClass.localName())) {
                objects.add(new ObjectMapping(localColumn, "memberOfDepartment", foreignKey.targetTable(), false, 0.90, "student-to-department-foreign-key"));
            } else if ("Course".equals(classMatch.localName()) && "College".equals(targetClass.localName())) {
                objects.add(new ObjectMapping(localColumn, "offersCourse", foreignKey.targetTable(), true, 0.89, "reverse-course-offering-foreign-key"));
            } else {
                notes.add("Foreign key " + localColumn + " -> " + foreignKey.targetTable()
                    + " remains in the raw RDF only; it was not elevated into the compact AICTE core automatically.");
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
            notes.add("All business columns were either preserved in raw RDF or promoted into the compact AICTE refinement.");
        } else {
            notes.add("Columns preserved only in raw RDF because they are outside the compact AICTE core: "
                + String.join(", ", omittedColumns));
        }

        double confidence = averageConfidence(classMatch.score(), literals, objects);
        return new TablePlan(table, classMatch, idColumn, nameColumn, literals, objects, omittedColumns, notes, confidence);
    }

    private Model buildRefinedModel(SqlInputParser.SqlSchema schema,
                                    Model rawModel,
                                    Map<String, String> primaryKeys,
                                    List<TablePlan> tablePlans) {
        Model refinedModel = ModelFactory.createDefaultModel();
        refinedModel.setNsPrefix("aicte", AICTE_NS);

        Map<String, TablePlan> plansByTable = new LinkedHashMap<>();
        for (TablePlan plan : tablePlans) {
            plansByTable.put(plan.table().name(), plan);
        }

        for (SqlInputParser.TableDefinition table : schema.tables().values()) {
            TablePlan plan = plansByTable.get(table.name());
            if (plan == null || plan.classMatch().score() < 0.55 || plan.idColumn() == null) {
                continue;
            }

            Property tableType = rawModel.createProperty(RDF.type.getURI());
            Resource rawTableClass = rawModel.createResource(rawClassUri(table.name()));
            StmtIterator rows = rawModel.listStatements(null, tableType, rawTableClass);
            try {
                while (rows.hasNext()) {
                    Resource rawRow = rows.nextStatement().getSubject();
                    Resource refinedSubject = refinedModel.createResource(refinedUri(plan.classMatch().localName(), rawRow, plan.idColumn()));
                    refinedModel.add(refinedSubject, RDF.type, refinedModel.createResource(AICTE_NS + plan.classMatch().localName()));

                    for (LiteralMapping literalMapping : plan.literals()) {
                        RDFNode value = rawValue(rawModel, rawRow, literalMapping.column());
                        if (value != null) {
                            refinedModel.add(refinedSubject, refinedModel.createProperty(AICTE_NS + literalMapping.propertyLocalName()), value);
                        }
                    }

                    for (ObjectMapping objectMapping : plan.objects()) {
                        Resource objectResource = rawReference(rawModel, rawRow, objectMapping.column());
                        if (objectResource == null) {
                            continue;
                        }
                        Candidate targetClass = guessClass(objectMapping.targetTable());
                        Resource refinedObject = refinedModel.createResource(refinedUri(
                            targetClass.localName(),
                            objectResource,
                            primaryKeys.getOrDefault(objectMapping.targetTable(), objectMapping.column())
                        ));
                        if (objectMapping.reverseSubject()) {
                            refinedModel.add(refinedObject,
                                refinedModel.createProperty(AICTE_NS + objectMapping.predicateLocalName()),
                                refinedSubject);
                        } else {
                            refinedModel.add(refinedSubject,
                                refinedModel.createProperty(AICTE_NS + objectMapping.predicateLocalName()),
                                refinedObject);
                        }
                    }
                }
            } finally {
                rows.close();
            }
        }

        return refinedModel;
    }

    private RDFNode rawValue(Model rawModel, Resource subject, String column) {
        Statement statement = rawModel.getProperty(subject, rawModel.createProperty(rawClassPropertyUri(column)));
        return statement == null ? null : statement.getObject();
    }

    private Resource rawReference(Model rawModel, Resource subject, String column) {
        Statement statement = rawModel.getProperty(subject, rawModel.createProperty(rawReferenceUri(column)));
        return statement != null && statement.getObject().isResource() ? statement.getResource() : null;
    }

    private String refinedUri(String classLocalName, Resource rawRow, String idColumn) {
        RDFNode identifier = rawValue(rawRow.getModel(), rawRow, idColumn);
        if (identifier != null && identifier.isLiteral()) {
            return REFINED_NS + slug(classLocalName) + "/" + slug(identifier.asLiteral().getString());
        }
        return REFINED_NS + slug(classLocalName) + "/" + slug(rawRow.getLocalName());
    }

    private String rawClassUri(String tableName) {
        return "https://semlink.example.org/r2o/raw#" + camelLocalName(tableName);
    }

    private String rawClassPropertyUri(String column) {
        return "https://semlink.example.org/r2o/raw#" + camelLocalName(column);
    }

    private String rawReferenceUri(String column) {
        return "https://semlink.example.org/r2o/raw#ref" + capitalize(camelLocalName(column));
    }

    private String writeReviewReport(String exampleName, List<TablePlan> plans) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Raw RDF Refinement Report\n\n");
        builder.append("Example: `").append(exampleName).append("`\n\n");
        builder.append("This report was generated after exporting the relational source into raw RDF triples using direct table, column, and foreign-key rules.\n");
        builder.append("The assistant does not author R2RML here. It reads the raw triples, promotes obvious facts into the compact AICTE view, and leaves the rest in the raw layer.\n\n");
        builder.append("## Recommended Flow\n\n");
        builder.append("1. Run `mvn -q exec:java -Dexec.args=\"r2o raw ").append(exampleName).append("\"`\n");
        builder.append("2. Inspect `raw/raw-direct-mapping.ttl`\n");
        builder.append("3. Run `mvn -q exec:java -Dexec.args=\"r2o assist ").append(exampleName).append("\"`\n");
        builder.append("4. Review `assisted/refined-from-raw.ttl` and this report\n\n");
        builder.append("## Table Decisions\n\n");

        for (TablePlan plan : plans) {
            builder.append("### ").append(plan.table().name()).append('\n');
            builder.append("- Suggested AICTE class: `aicte:").append(plan.classMatch().localName()).append("`\n");
            builder.append("- Confidence: ").append(String.format(Locale.ROOT, "%.2f", plan.confidence())).append('\n');
            builder.append("- Identifier column: `").append(plan.idColumn() == null ? "not-detected" : plan.idColumn()).append("`\n");
            builder.append("- Label column: `").append(plan.nameColumn() == null ? "not-detected" : plan.nameColumn()).append("`\n");
            if (!plan.literals().isEmpty()) {
                builder.append("- Promoted literals: ");
                builder.append(plan.literals().stream()
                    .map(mapping -> mapping.column() + " -> aicte:" + mapping.propertyLocalName())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("none"));
                builder.append('\n');
            }
            if (!plan.objects().isEmpty()) {
                builder.append("- Promoted relationships: ");
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
        builder.append("- Confirm `*_name` and `*_title` columns are semantically safe to promote to `aicte:name`.\n");
        builder.append("- Check whether raw-only columns such as `city` or `credits` should be mapped into local extensions.\n");
        builder.append("- Review reverse relationships such as `aicte:offersCourse`, because they depend on domain meaning rather than schema syntax alone.\n");
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
                .append(String.join(",", plan.omittedColumns()))
                .append('\n');
        }
        return builder.toString();
    }

    private Candidate guessClass(String tableName) {
        String normalized = tableName.toLowerCase(Locale.ROOT);
        Candidate best = new Candidate("Entity", 0.25);
        for (Map.Entry<String, List<String>> entry : CLASS_HINTS.entrySet()) {
            for (String hint : entry.getValue()) {
                if (normalized.contains(hint)) {
                    double score = 0.65 + (0.05 * hint.length() / 10.0);
                    if (score > best.score()) {
                        best = new Candidate(entry.getKey(), Math.min(score, 0.98));
                    }
                }
            }
        }
        return best;
    }

    private String guessIdColumn(SqlInputParser.TableDefinition table, String className) {
        List<String> candidates = List.of(
            slug(className) + "_id",
            table.normalizedName() + "_id",
            "id"
        );
        for (String candidate : candidates) {
            String match = findExactColumn(table, candidate);
            if (match != null) {
                return match;
            }
        }
        for (String column : table.columns()) {
            if (column.toLowerCase(Locale.ROOT).endsWith("_id")) {
                return column;
            }
        }
        return table.columns().isEmpty() ? null : table.columns().getFirst();
    }

    private String guessNameColumn(SqlInputParser.TableDefinition table) {
        for (String hint : NAME_HINTS) {
            String match = findFirstMatchingColumn(table, List.of(hint, table.normalizedName() + "_" + hint));
            if (match != null) {
                return match;
            }
        }
        return findFirstMatchingColumn(table, List.of("name", "title"));
    }

    private String findExactColumn(SqlInputParser.TableDefinition table, String expected) {
        for (String column : table.columns()) {
            if (column.equalsIgnoreCase(expected)) {
                return column;
            }
        }
        return null;
    }

    private String findFirstMatchingColumn(SqlInputParser.TableDefinition table, List<String> patterns) {
        for (String pattern : patterns) {
            for (String column : table.columns()) {
                if (column.equalsIgnoreCase(pattern) || column.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT))) {
                    return column;
                }
            }
        }
        return null;
    }

    private boolean isDepartmentColumn(String column) {
        String normalized = column.toLowerCase(Locale.ROOT);
        return normalized.contains("department") || normalized.contains("dept");
    }

    private double averageConfidence(double classConfidence,
                                     List<LiteralMapping> literals,
                                     List<ObjectMapping> objects) {
        double total = classConfidence;
        int count = 1;
        for (LiteralMapping literal : literals) {
            total += literal.confidence();
            count++;
        }
        for (ObjectMapping object : objects) {
            total += object.confidence();
            count++;
        }
        return total / count;
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    private String camelLocalName(String value) {
        String[] parts = value.split("_+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(capitalize(part.toLowerCase(Locale.ROOT)));
        }
        if (builder.isEmpty()) {
            return "value";
        }
        String camel = builder.toString();
        return Character.toLowerCase(camel.charAt(0)) + camel.substring(1);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    public record DraftResult(Model refinedModel, String reviewReport, String schemaProfile) {
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

    private record Candidate(String localName, double score) {
    }

    private record LiteralMapping(String column, String propertyLocalName, double confidence, String rationale) {
    }

    private record ObjectMapping(
        String column,
        String predicateLocalName,
        String targetTable,
        boolean reverseSubject,
        double confidence,
        String rationale
    ) {
    }
}
