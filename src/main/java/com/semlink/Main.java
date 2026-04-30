package com.semlink;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        SemanticProject project = new SemanticProject();
        String command = args.length == 0 ? "demo" : args[0];

        switch (command) {
            case "demo" -> project.runDemo();
            case "r2o" -> project.runR2o(Arrays.copyOfRange(args, 1, args.length));
            case "custom" -> project.runCustom(Arrays.copyOfRange(args, 1, args.length));
            case "connect" -> project.runConnect(Arrays.copyOfRange(args, 1, args.length));
            case "pipeline" -> project.runPipeline(Arrays.copyOfRange(args, 1, args.length));
            case "schema" -> project.runSchema(Arrays.copyOfRange(args, 1, args.length));
            case "nl" -> {
                if (args.length < 2) {
                    printUsage();
                    throw new IllegalArgumentException("Missing natural-language question.");
                }
                project.runNaturalLanguageQuery(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            }
            case "report" -> project.runHtmlReport();
            case "usecase" -> project.runUseCase(Arrays.copyOfRange(args, 1, args.length));
            case "query" -> {
                if (args.length < 2) {
                    printUsage();
                    throw new IllegalArgumentException("Missing query name.");
                }
                project.runQuery(args[1]);
            }
            case "validate" -> project.runValidationOnly();
            default -> {
                printUsage();
                throw new IllegalArgumentException("Unknown command: " + command + " " + Arrays.toString(args));
            }
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  mvn exec:java -Dexec.args=\"demo\"");
        System.out.println("  mvn exec:java -Dexec.args=\"query all_students\"");
        System.out.println("  mvn exec:java -Dexec.args=\"validate\"");
        System.out.println("  mvn exec:java -Dexec.args=\"r2o raw example-college\"");
        System.out.println("  mvn exec:java -Dexec.args=\"r2o assist example-college\"");
        System.out.println("  mvn exec:java -Dexec.args=\"r2o pipeline example-college\"");
        System.out.println("  mvn exec:java -Dexec.args=\"r2o generate example-college manual\"");
        System.out.println("  mvn exec:java -Dexec.args=\"custom run college-pack src/main/resources/semantic/onboarding/custom-sample/college.owl src/main/resources/semantic/onboarding/custom-sample/mapping-rules.ttl\"");
        System.out.println("  mvn exec:java -Dexec.args=\"connect add --type owl --id university1 --path src/main/resources/semantic/ontologies/local/university1/university1.ttl\"");
        System.out.println("  mvn exec:java -Dexec.args=\"connect list\"");
        System.out.println("  mvn exec:java -Dexec.args=\"pipeline run\"");
        System.out.println("  mvn exec:java -Dexec.args=\"schema diff\"");
        System.out.println("  mvn exec:java -Dexec.args=\"nl Show students with CGPA above 9 from all universities\"");
        System.out.println("  mvn exec:java -Dexec.args=\"report\"");
        System.out.println("  mvn exec:java -Dexec.args=\"usecase list\"");
        System.out.println("  mvn exec:java -Dexec.args=\"usecase run usecase1\"");
    }
}
