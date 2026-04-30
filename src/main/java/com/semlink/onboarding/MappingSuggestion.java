package com.semlink.onboarding;

public record MappingSuggestion(
    String sourceTable,
    String sourceColumn,
    String suggestedClass,
    String suggestedProperty,
    double confidence,
    String rationale,
    String matchType
) {
    public static final String TYPE_CLASS = "class";
    public static final String TYPE_PROPERTY = "property";
    public static final String TYPE_RELATIONSHIP = "relationship";
}