package com.semlink;

import java.util.List;

/**
 * Presentation-ready demo scenario with before/after queries and lesson text.
 */
public record DemoScenario(
    String id,
    String title,
    String context,
    String dataModelLesson,
    List<String> beforeQueries,
    String afterSparql,
    String wowMoment,
    List<String> commands
) {
    public DemoScenario {
        beforeQueries = List.copyOf(beforeQueries == null ? List.of() : beforeQueries);
        commands = List.copyOf(commands == null ? List.of() : commands);
    }

    public String render() {
        StringBuilder text = new StringBuilder();
        text.append("# ").append(title).append('\n').append('\n');
        text.append("Context: ").append(context).append('\n');
        text.append("Data model lesson: ").append(dataModelLesson).append("\n\n");
        text.append("BEFORE native queries:\n");
        beforeQueries.forEach(query -> text.append("- ").append(query).append('\n'));
        text.append("\nAFTER SEMLINK SPARQL:\n").append(afterSparql).append("\n\n");
        text.append("WOW: ").append(wowMoment).append("\n\n");
        text.append("Commands:\n");
        commands.forEach(command -> text.append("- `").append(command).append("`\n"));
        return text.toString();
    }
}
