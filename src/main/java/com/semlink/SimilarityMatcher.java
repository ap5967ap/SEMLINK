package com.semlink;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SimilarityMatcher {
    private static final Set<String> CLASS_TYPES = Set.of(
        OWL.Class.getURI()
    );

    private static final Set<String> PROPERTY_TYPES = Set.of(
        OWL.ObjectProperty.getURI(),
        OWL.DatatypeProperty.getURI()
    );

    private static final Map<String, String> SYNONYM_HINTS = Map.ofEntries(
        Map.entry("learner", "student"),
        Map.entry("module", "course"),
        Map.entry("registeredat", "studiesat"),
        Map.entry("pupil", "student"),
        Map.entry("studentinfo", "student"),
        Map.entry("campuscollege", "college"),
        Map.entry("affiliatedcollege", "college"),
        Map.entry("paper", "course"),
        Map.entry("track", "program"),
        Map.entry("facultyarea", "department"),
        Map.entry("division", "department")
    );

    private final String aicteNamespace;

    public SimilarityMatcher(String aicteNamespace) {
        this.aicteNamespace = aicteNamespace;
    }

    public String generateSuggestions(Model aicteModel, Map<String, Model> ontologyModels) {
        List<Term> aicteClasses = collectTerms(aicteModel, CLASS_TYPES, "class", true);
        List<Term> aicteProperties = collectTerms(aicteModel, PROPERTY_TYPES, "property", true);

        StringBuilder builder = new StringBuilder();
        builder.append("ontology\tkind\tlocal_term\tsuggested_aicte_term\tscore\tmethod\n");

        for (Map.Entry<String, Model> entry : ontologyModels.entrySet()) {
            if ("aicte".equals(entry.getKey())) {
                continue;
            }

            List<Term> localClasses = collectTerms(entry.getValue(), CLASS_TYPES, "class", false);
            List<Term> localProperties = collectTerms(entry.getValue(), PROPERTY_TYPES, "property", false);

            appendSuggestions(builder, entry.getKey(), localClasses, aicteClasses);
            appendSuggestions(builder, entry.getKey(), localProperties, aicteProperties);
        }

        return builder.toString();
    }

    private void appendSuggestions(StringBuilder builder, String ontologyKey, List<Term> localTerms, List<Term> aicteTerms) {
        for (Term localTerm : localTerms) {
            Suggestion suggestion = bestSuggestion(localTerm, aicteTerms);
            if (suggestion.score() >= 0.45) {
                builder.append(ontologyKey).append('\t')
                    .append(localTerm.kind()).append('\t')
                    .append(localTerm.localName()).append('\t')
                    .append(suggestion.aicteTerm()).append('\t')
                    .append(String.format(Locale.ROOT, "%.2f", suggestion.score())).append('\t')
                    .append(suggestion.method()).append('\n');
            }
        }
    }

    private Suggestion bestSuggestion(Term localTerm, List<Term> aicteTerms) {
        return aicteTerms.stream()
            .map(aicteTerm -> score(localTerm, aicteTerm))
            .max(Comparator.comparingDouble(Suggestion::score))
            .orElse(new Suggestion(localTerm.localName(), "none", 0.0, "no-match"));
    }

    private Suggestion score(Term localTerm, Term aicteTerm) {
        String normalizedLocal = normalize(localTerm.localName());
        String normalizedAicte = normalize(aicteTerm.localName());

        double levenshteinScore = similarity(normalizedLocal, normalizedAicte);
        boolean hinted = normalizedAicte.equals(SYNONYM_HINTS.get(normalizedLocal));
        double finalScore = hinted ? Math.max(levenshteinScore, 0.92) : levenshteinScore;
        String method = hinted ? "synonym-boost+levenshtein" : "levenshtein";

        return new Suggestion(localTerm.localName(), aicteTerm.localName(), finalScore, method);
    }

    private List<Term> collectTerms(Model model, Set<String> acceptedTypes, String kind, boolean restrictToAicteNamespace) {
        Map<String, Term> terms = new LinkedHashMap<>();
        for (String typeUri : acceptedTypes) {
            ExtendedIterator<Resource> iterator = model.listResourcesWithProperty(RDF.type, model.createResource(typeUri));
            try {
                iterator.forEachRemaining(resource -> {
                    if (!resource.isURIResource()) {
                        return;
                    }
                    if (restrictToAicteNamespace && !resource.getURI().startsWith(aicteNamespace)) {
                        return;
                    }
                    String localName = resource.getLocalName();
                    if (localName != null && !localName.isBlank()) {
                        terms.putIfAbsent(resource.getURI(), new Term(resource.getURI(), localName, kind));
                    }
                });
            } finally {
                iterator.close();
            }
        }

        List<Term> values = new ArrayList<>(terms.values());
        values.sort(Comparator.comparing(Term::localName));
        return values;
    }

    private String normalize(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1 $2")
            .replaceAll("[^A-Za-z0-9]+", "")
            .toLowerCase(Locale.ROOT);
    }

    private double similarity(String left, String right) {
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) {
            return 1.0;
        }
        int distance = levenshtein(left, right);
        return 1.0 - ((double) distance / maxLength);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                    Math.min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + substitution
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[right.length()];
    }

    private record Term(String uri, String localName, String kind) {
    }

    private record Suggestion(String localTerm, String aicteTerm, double score, String method) {
    }
}
