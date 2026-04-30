package com.semlink;

import java.util.List;

/**
 * Immutable report model used by HtmlReportRenderer.
 */
public record HtmlReport(String title, List<HtmlReportSection> sections) {
    public HtmlReport {
        sections = List.copyOf(sections == null ? List.of() : sections);
    }
}
