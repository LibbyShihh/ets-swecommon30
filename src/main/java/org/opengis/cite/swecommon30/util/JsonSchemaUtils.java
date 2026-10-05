package org.opengis.cite.swecommon30.util;

import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Schema loading and per-instance reporting shared by the SWE Common JSON Schema conformance tests.
 */
public final class JsonSchemaUtils {

    private static final String SCHEMA_ROOT = "/org/opengis/cite/swecommon30/jsonschema/";

    private static final String GEOJSON_GEOMETRY = "https://geojson.org/schema/Geometry.json";

    private JsonSchemaUtils() {
    }

    /**
     * Loads a bundled schema with the configuration every JSON Schema conformance test uses.
     *
     * @param path schema path relative to the jsonschema resource folder, e.g.
     *            "sweCommon/3.0/json/DataChoice.json"
     * @return the compiled schema
     */
    public static JsonSchema loadSchema(String path) {
        URL schemaUrl = resource(path);
        // Geometry.json $refs the GeoJSON schema online; serve the bundled copy so runs work offline.
        URL geoJsonUrl = resource("geojson/Geometry.json");
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaMappers(
                        mappers -> mappers.mapPrefix(GEOJSON_GEOMETRY, geoJsonUrl.toString())));
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                // DateTimeNumberOrSpecial's oneOf only separates "+Infinity" from a date-time when
                // format is asserted, and only an asserted format rejects a "srs" or "definition"
                // that isn't a URI.
                .formatAssertionsEnabled(true)
                // "$: ..." locations, as A.1 reports them; the builder's JSON-pointer default prints
                // root errors as ": ...".
                .pathType(PathType.LEGACY)
                .build();
        return factory.getSchema(SchemaLocation.of(schemaUrl.toString()), config);
    }

    /**
     * Validates each instance and reports the ones that fail, one block per instance in document
     * order.
     *
     * @param typeName component type named in each block header, e.g. "DataChoice"
     * @param instances the instances found in the test subject
     * @param schema the schema each instance must conform to
     * @return an empty string if every instance is valid, otherwise the failure blocks
     */
    public static String reportInstances(String typeName, List<JsonNode> instances, JsonSchema schema) {
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < instances.size(); i++) {
            JsonNode instance = instances.get(i);
            Set<ValidationMessage> errors = schema.validate(instance);
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

    private static URL resource(String path) {
        URL url = JsonSchemaUtils.class.getResource(SCHEMA_ROOT + path);
        if (url == null) {
            throw new IllegalStateException("Schema resource not found: " + SCHEMA_ROOT + path);
        }
        return url;
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
