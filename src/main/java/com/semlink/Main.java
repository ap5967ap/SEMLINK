package com.semlink;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        SemanticProject project = new SemanticProject();
        String command = args.length == 0 ? "demo" : args[0];

        switch (command) {
            case "demo" -> project.runDemo();
            case "r2o" -> project.runR2o(Arrays.copyOfRange(args, 1, args.length));
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
        System.out.println("  mvn exec:java -Dexec.args=\"r2o assist example-college\"");
        System.out.println("  mvn exec:java -Dexec.args=\"r2o pipeline example-college\"");
        System.out.println("  mvn exec:java -Dexec.args=\"r2o generate example-college manual\"");
    }
}
