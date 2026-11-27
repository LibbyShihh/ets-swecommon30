package org.opengis.cite.swecommon30.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A {@link JsonEncodingRulesValidator} that returns each violation with the abstract test it breaks,
 * taken from the type of the component whose value breaks it.
 */
public class TaggedJsonEncodingRulesValidator extends JsonEncodingRulesValidator {

    private static final Map<String, String> RULE_BY_TYPE = Map.ofEntries(Map.entry("Boolean", "A.83"),
            Map.entry("Text", "A.83"), Map.entry("Category", "A.83"), Map.entry("Count", "A.83"),
            Map.entry("Quantity", "A.83"), Map.entry("Time", "A.83"), Map.entry("CategoryRange", "A.84"),
            Map.entry("CountRange", "A.84"), Map.entry("QuantityRange", "A.84"), Map.entry("TimeRange", "A.84"),
            Map.entry("DataRecord", "A.85"), Map.entry("Vector", "A.86"), Map.entry("DataChoice", "A.87"),
            Map.entry("DataArray", "A.88"), Map.entry("Matrix", "A.88"), Map.entry("DataStream", "clause 9.4.4"),
            Map.entry("Geometry", "A.89"));

    private List<Violation> collected = new ArrayList<>();

    /**
     * Checks encoded values against the component that describes them.
     *
     * @param component data component that describes the value
     * @param value encoded value to check
     * @param location where the value is, e.g. "values"
     * @param unchecked receives each check that could not be made, with the reason
     * @return each rule the values break, with its abstract test
     */
    public List<Violation> validate(JsonNode component, JsonNode value, String location,
            Map<String, String> unchecked) {
        collected = new ArrayList<>();
        validate(component, value, location, new ArrayList<>(), unchecked);
        return collected;
    }

    @Override
    public void validate(JsonNode component, JsonNode value, String location, List<String> violations,
            Map<String, String> unchecked) {
        List<String> own = new ArrayList<>();
        super.validate(component, value, location, own, unchecked);
        if (!own.isEmpty()) {
            String ruleId = RULE_BY_TYPE.get(component.path("type").asText());
            own.forEach(message -> collected.add(new Violation(ruleId, message)));
        }
    }

    /** A rule an encoded value breaks: its abstract test, and the message saying where and how. */
    public static final class Violation {

        private final String ruleId;

        private final String message;

        public Violation(String ruleId, String message) {
            this.ruleId = ruleId;
            this.message = message;
        }

        public String ruleId() {
            return ruleId;
        }

        public String message() {
            return message;
        }
    }
}
