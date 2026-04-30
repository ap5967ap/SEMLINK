package com.semlink.onboarding;

public record ApprovedMapping(
    String sourceTable,
    String sourceColumn,
    String aicteClass,
    String aicteProperty,
    boolean userCustomized
) {}