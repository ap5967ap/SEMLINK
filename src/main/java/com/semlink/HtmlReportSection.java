package com.semlink;

/**
 * One visual row or panel in the generated SEMLINK HTML report.
 */
public record HtmlReportSection(String title, String body, String status) {
}
