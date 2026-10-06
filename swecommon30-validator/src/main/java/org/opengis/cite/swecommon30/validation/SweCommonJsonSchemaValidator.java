package org.opengis.cite.swecommon30.validation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Validates SWE Common JSON documents against the bundled SWE Common schemas.
 */
public class SweCommonJsonSchemaValidator {

    private static final String SCHEMA_ROOT =
            "/org/opengis/cite/swecommon30/jsonschema/sweCommon/3.0/json/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Reads a JSON document from a file.
     *
     * @param jsonFile JSON document file
     * @return parsed JSON document
     * @throws IOException if the file cannot be read as JSON
     */
    public JsonNode readJson(File jsonFile) throws IOException {
        return objectMapper.readTree(jsonFile);
    }

    /**
     * Validates a JSON document against a bundled SWE Common schema.
     *
     * @param document JSON document to validate
     * @param schemaName schema resource file name, such as {@code Boolean.json}
     * @return validation messages; empty when the document is valid
     */
    public Set<ValidationMessage> validate(JsonNode document, String schemaName) {
        return loadSchema(schemaName).validate(document);
    }

    /**
     * Formats validation messages for display in an ETS report.
     *
     * @param componentName component name being validated
     * @param errors validation messages
     * @return formatted validation error text
     */
    public String formatValidationErrors(String componentName, Set<ValidationMessage> errors) {
        return errors.stream()
                .map(ValidationMessage::getMessage)
                .sorted()
                .collect(Collectors.joining(System.lineSeparator(),
                        "The " + componentName + " component does not conform to its schema:"
                                + System.lineSeparator(),
                        ""));
    }

    private JsonSchema loadSchema(String schemaName) {
        URL schemaUrl = SweCommonJsonSchemaValidator.class.getResource(SCHEMA_ROOT + schemaName);
        if (schemaUrl == null) {
            throw new IllegalStateException("Schema resource not found: " + SCHEMA_ROOT + schemaName);
        }
        try {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaUrl.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid schema resource URI: " + schemaUrl, e);
        }
    }
}
