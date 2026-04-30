package com.semlink;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders demo and validation summaries to a static HTML artifact suitable for
 * the course presentation and regulator-style compliance walkthroughs.
 */
public class HtmlReportRenderer {
    public void render(HtmlReport report, Path outputPath) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<title>").append(escape(report.title())).append("</title>");
        html.append("<style>");
        html.append(":root{color-scheme:dark;--bg:#080b12;--panel:#101725;--line:#253247;--text:#edf4ff;--muted:#9fb0c7;--accent:#4fd1c5}");
        html.append("*{box-sizing:border-box}body{font-family:Inter,Arial,sans-serif;margin:0;background:#080b12;color:var(--text)}");
        html.append("main{max-width:1180px;margin:0 auto;padding:34px 24px 48px}");
        html.append(".top{display:flex;align-items:center;justify-content:space-between;gap:18px;border-bottom:1px solid var(--line);padding-bottom:22px}");
        html.append(".brand{font-size:13px;letter-spacing:.14em;text-transform:uppercase;color:var(--accent);font-weight:700}");
        html.append("h1{font-size:38px;line-height:1.08;margin:8px 0 10px;letter-spacing:0}");
        html.append(".lede{max-width:760px;color:var(--muted);font-size:16px;line-height:1.6;margin:0}");
        html.append(".badge{border:1px solid var(--line);border-radius:999px;padding:10px 14px;color:var(--muted);white-space:nowrap}");
        html.append(".metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:26px 0}");
        html.append(".metric{border:1px solid var(--line);background:#0c111c;padding:16px;border-radius:8px}");
        html.append(".metric strong{display:block;font-size:26px}.metric span{color:var(--muted);font-size:13px}");
        html.append(".flow{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1px;background:var(--line);border:1px solid var(--line);border-radius:8px;overflow:hidden;margin-bottom:26px}");
        html.append(".step{background:var(--panel);padding:18px;min-height:128px}.step b{display:block;color:var(--accent);font-size:13px;margin-bottom:8px}.step p{color:var(--muted);line-height:1.5;margin:0}");
        html.append(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:14px}");
        html.append(".card{background:#0c111c;border:1px solid var(--line);border-radius:8px;padding:18px;min-height:132px;transition:transform .18s ease,border-color .18s ease}");
        html.append(".card:hover{transform:translateY(-2px);border-color:var(--accent)}");
        html.append(".ok{box-shadow:inset 4px 0 0 #38d996}.warn{box-shadow:inset 4px 0 0 #f4b740}.fail{box-shadow:inset 4px 0 0 #ff6b6b}");
        html.append("h2{font-size:18px;margin:0 0 10px;letter-spacing:0}p{line-height:1.5;margin:0;color:var(--muted)}code{color:#d7fffb}");
        html.append("@media(max-width:760px){.top{display:block}.metrics,.flow{grid-template-columns:1fr}h1{font-size:30px}.badge{display:inline-block;margin-top:16px}}");
        html.append("</style></head><body><main>");
        html.append("<section class=\"top\"><div><div class=\"brand\">SEMLINK Demo Console</div>");
        html.append("<h1>").append(escape(report.title())).append("</h1>");
        html.append("<p class=\"lede\">AICTE semantic integration flow: onboard heterogeneous university data, align it to the central ontology, validate quality with SHACL, and query everything through one semantic layer.</p>");
        html.append("</div><div class=\"badge\">v2.0.0 presentation build</div></section>");
        html.append("<section class=\"metrics\">");
        html.append("<div class=\"metric\"><strong>4</strong><span>university source models</span></div>");
        html.append("<div class=\"metric\"><strong>12</strong><span>SPARQL demo queries</span></div>");
        html.append("<div class=\"metric\"><strong>24</strong><span>students inferred</span></div>");
        html.append("<div class=\"metric\"><strong>SHACL</strong><span>quality gate active</span></div>");
        html.append("</section>");
        html.append("<section class=\"flow\">");
        html.append("<div class=\"step\"><b>01 Onboarding</b><p>Load OWL, custom college packs, or R2O exports from relational schema inputs.</p></div>");
        html.append("<div class=\"step\"><b>02 Alignment</b><p>Apply R2RML, Jena rules, and similarity suggestions into the AICTE vocabulary.</p></div>");
        html.append("<div class=\"step\"><b>03 Validation</b><p>Run SHACL constraints and produce regulator-ready quality evidence.</p></div>");
        html.append("<div class=\"step\"><b>04 Querying</b><p>Run one SPARQL or natural-language query over all aligned sources.</p></div>");
        html.append("</section>");
        html.append("<section class=\"grid\">");
        for (HtmlReportSection section : report.sections()) {
            html.append("<article class=\"card ").append(cssClass(section.status())).append("\">");
            html.append("<h2>").append(escape(section.title())).append("</h2>");
            html.append("<p>").append(escape(section.body())).append("</p>");
            html.append("</article>");
        }
        html.append("</section></main></body></html>");
        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, html.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write HTML report " + outputPath, exception);
        }
    }

    private String cssClass(String status) {
        if ("ok".equals(status) || "warn".equals(status) || "fail".equals(status)) {
            return status;
        }
        return "ok";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
