package com.semlink;

import com.semlink.onboarding.OnboardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API controller exposing all SEMLINK pipeline operations.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ApiController {

    private final SemanticService service;
    private final OnboardingService onboardingService;

    public ApiController(SemanticService service, OntologyDatabase ontologyDatabase) {
        this.service = service;
        this.onboardingService = new OnboardingService(EnvConfig.getGeminiApiKey(), ontologyDatabase);
        service.setOnboardingService(onboardingService);
    }

    /* ── Health ────────────────────────────────────────────────── */

    @GetMapping("/health")
    public Map<String, Object> health() {
        return service.getHealth();
    }

    /* ── Pipeline ─────────────────────────────────────────────── */

    @PostMapping("/pipeline/run")
    public Map<String, Object> runPipeline() {
        return service.runPipeline();
    }

    /* ── Connections ──────────────────────────────────────────── */

    @GetMapping("/connections")
    public Collection<ConnectionConfig> listConnections() {
        return service.listConnections();
    }

    @PostMapping("/connections")
    public ConnectionConfig addConnection(@RequestBody Map<String, String> body) {
        int port = 0;
        try { port = Integer.parseInt(body.getOrDefault("port", "0")); } catch (NumberFormatException ignored) {}
        return service.addConnection(
            body.getOrDefault("id", "source-" + System.currentTimeMillis()),
            body.getOrDefault("type", "owl"),
            body.getOrDefault("host", ""),
            port,
            body.getOrDefault("database", "")
        );
    }

    @DeleteMapping("/connections/{id}")
    public ResponseEntity<Void> removeConnection(@PathVariable String id) {
        return service.removeConnection(id) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    /* ── Queries ──────────────────────────────────────────────── */

    @GetMapping("/query/catalog")
    public List<String> queryCatalog() {
        return service.listQueries();
    }

    @PostMapping("/query/sparql")
    public Map<String, Object> executeSparql(@RequestBody Map<String, String> body) {
        String sparql = body.getOrDefault("sparql", "");
        if (sparql.isBlank()) {
            return Map.of("error", "Missing 'sparql' field");
        }
        return service.executeSparql(sparql);
    }

    @PostMapping("/query/natural")
    public Map<String, Object> naturalQuery(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "");
        if (question.isBlank()) {
            return Map.of("error", "Missing 'question' field");
        }
        return service.naturalLanguageQuery(question);
    }

    @GetMapping("/query/{name}")
    public Map<String, Object> namedQuery(@PathVariable String name) {
        return service.executeNamedQuery(name);
    }

    /* ── Validation ──────────────────────────────────────────── */

    @PostMapping("/validate")
    public Map<String, Object> validate() {
        return service.runValidation();
    }

    /* ── Mapping Suggestions ─────────────────────────────────── */

    @GetMapping("/mappings")
    public List<Map<String, Object>> mappingSuggestions() {
        return service.getMappingSuggestions();
    }

    /* ── Schema Diff ─────────────────────────────────────────── */

    @GetMapping("/ontology/diff")
    public Map<String, Object> ontologyDiff() {
        return service.getOntologyDiff();
    }

    /* ── Onboarding ──────────────────────────────────────────── */

    @PostMapping("/onboard/r2o")
    public Map<String, Object> onboardR2o(@RequestBody Map<String, String> body) {
        String exampleName = body.getOrDefault("exampleName", "example-college");
        String mode = body.getOrDefault("mode", "assist");
        return service.runR2oOnboarding(exampleName, mode);
    }

    @PostMapping("/onboard/sql")
    public Map<String, Object> onboardFromSql(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "uploaded-" + System.currentTimeMillis());
        String schemaSql = body.getOrDefault("schemaSql", "");
        String dataSql = body.getOrDefault("dataSql", "");
        if (schemaSql.isBlank() || dataSql.isBlank()) {
            return Map.of("status", "error", "message", "Both schemaSql and dataSql are required");
        }
        return service.runR2oFromSql(name, schemaSql, dataSql);
    }

    /* ── Onboarding Steps ──────────────────────────────────────── */

    @PostMapping("/onboard/parse")
    public Map<String, Object> onboardParse(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "uploaded-" + System.currentTimeMillis());
        String schemaSql = body.getOrDefault("schemaSql", "");
        String dataSql = body.getOrDefault("dataSql", "");
        if (schemaSql.isBlank() || dataSql.isBlank()) {
            return Map.of("status", "error", "message", "Both schemaSql and dataSql are required");
        }
        return onboardingService.parseSql(name, schemaSql, dataSql);
    }

    @PostMapping("/onboard/suggest")
    public Map<String, Object> onboardSuggest(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "");
        if (name.isBlank()) return Map.of("status", "error", "message", "name is required");
        return onboardingService.suggestMappings(name);
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/onboard/approve")
    public Map<String, Object> onboardApprove(@RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "");
        if (name.isBlank()) return Map.of("status", "error", "message", "name is required");
        var mappings = (List<Map<String, Object>>) body.getOrDefault("approvedMappings", List.of());
        return onboardingService.approveMappings(name, mappings);
    }

    @PostMapping("/onboard/transform")
    public Map<String, Object> onboardTransform(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "");
        String schemaSql = body.getOrDefault("schemaSql", "");
        String dataSql = body.getOrDefault("dataSql", "");
        if (name.isBlank() || schemaSql.isBlank() || dataSql.isBlank()) {
            return Map.of("status", "error", "message", "name, schemaSql, and dataSql are required");
        }
        return onboardingService.transform(name, schemaSql, dataSql);
    }

    @PostMapping("/onboard/validate")
    public Map<String, Object> onboardValidate(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "");
        if (name.isBlank()) return Map.of("status", "error", "message", "name is required");
        return onboardingService.validateTriples(name);
    }

    @PostMapping("/onboard/publish")
    public Map<String, Object> onboardPublish(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "");
        if (name.isBlank()) return Map.of("status", "error", "message", "name is required");
        Map<String, Object> result = onboardingService.publish(name);
        // After publish, trigger pipeline refresh to update health metrics
        if ("published".equals(result.get("status"))) {
            try {
                // Update health metrics after successful publish
                service.getHealth(); // This will refresh counts
            } catch (Exception e) {
                // Non-critical, health will update on next call
            }
        }
        return result;
    }

    @GetMapping("/onboard/status/{name}")
    public Map<String, Object> onboardStatus(@PathVariable String name) {
        return Map.of("name", name, "hasModel", onboardingService.hasModel(name));
    }
}
