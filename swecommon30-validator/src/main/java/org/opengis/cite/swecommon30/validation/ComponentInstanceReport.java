package org.opengis.cite.swecommon30.validation;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;

/**
 * Validates every instance of one component type found in a document, and reports the ones that
 * fail, one block per instance in document order.
 */
public final class ComponentInstanceReport {

    private ComponentInstanceReport() {
    }

    /**
     * Validates each instance and reports the ones that fail.
     *
     * @param validator validator that checks each instance
     * @param typeName component type named in each block header, e.g. "DataChoice"
     * @param instances the instances found in the test subject, in document order
     * @param schemaName schema each instance must conform to, e.g. "DataChoice.json"
     * @return an empty string if every instance is valid, otherwise the failure blocks
     */
    public static String report(SweCommonJsonSchemaValidator validator, String typeName,
            List<JsonNode> instances, String schemaName) {
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < instances.size(); i++) {
            JsonNode instance = instances.get(i);
            Set<ValidationMessage> errors = validator.validate(instance, schemaName);
            if (!errors.isEmpty()) {
                // No trailing separator after the last block: TestNG appends " expected [true]
                // but found [false]" straight onto the message, and a dangling newline here would
                // push that suffix onto its own line instead.
                if (report.length() > 0) {
                    report.append(System.lineSeparator());
                }
                report.append(describe(typeName, instance, i, instances.size())).append(System.lineSeparator())
                      .append(formatValidationErrors(errors));
            }
        }
        return report.toString();
    }

    /** Names one instance, so a failure says which one in the document it is about. */
    private static String describe(String typeName, JsonNode instance, int index, int total) {
        String hint = "";
        for (String key : new String[] { "name", "label", "id" }) {
            if (instance.hasNonNull(key) && instance.get(key).isTextual()) {
                hint = " (" + key + ": '" + instance.get(key).asText() + "')";
                break;
            }
        }
        return typeName + " instance " + (index + 1) + " of " + total + hint + ":";
    }

    private static String formatValidationErrors(Set<ValidationMessage> errors) {
        return errors.stream()
                .map(ValidationMessage::getMessage)
                .distinct()
                .sorted()
                .map(message -> "  " + message)
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
