package org.opengis.cite.swecommon30.validation;

import java.net.URL;
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
 * Validates whole SWE Common JSON documents against the bundled schemas, such as the
 * {@code sweCommon.json} entry point, without network access.
 *
 * <p>It differs from {@link SweCommonJsonSchemaValidator} in three ways, each needed once a
 * document is validated against {@code sweCommon.json}:</p>
 * <ul>
 * <li>{@code Geometry.json} {@code $ref}s the GeoJSON schema online; the bundled copy is served
 * instead, so runs work offline and don't follow whatever geojson.org serves.</li>
 * <li>{@code format} is asserted. {@code basicTypes.json#/$defs/DateTimeNumberOrSpecial} is a
 * {@code oneOf} that only tells a special value such as {@code "+Infinity"} apart from a date-time
 * when {@code format: date-time} is checked; without it, valid Time values fail.</li>
 * <li>Repeated messages are reported once. The entry point's root {@code oneOf} reports the same
 * error through several branches.</li>
 * </ul>
 */
public class OfflineSweCommonJsonSchemaValidator extends SweCommonJsonSchemaValidator {

    private static final String SCHEMA_ROOT =
            "/org/opengis/cite/swecommon30/jsonschema/sweCommon/3.0/json/";

    private static final String GEOJSON_ROOT = "/org/opengis/cite/swecommon30/jsonschema/geojson/";

    private static final String GEOJSON_GEOMETRY = "https://geojson.org/schema/Geometry.json";

    /**
     * Validates a JSON document against a bundled SWE Common schema, offline and with
     * {@code format} asserted.
     *
     * @param document JSON document to validate
     * @param schemaName schema resource file name, such as {@code sweCommon.json}
     * @return validation messages; empty when the document is valid
     */
    @Override
    public Set<ValidationMessage> validate(JsonNode document, String schemaName) {
        return loadSchema(schemaName).validate(document);
    }

    /**
     * Formats validation messages for display in an ETS report, each distinct message once.
     *
     * @param componentName component name being validated
     * @param errors validation messages
     * @return formatted validation error text
     */
    @Override
    public String formatValidationErrors(String componentName, Set<ValidationMessage> errors) {
        return errors.stream()
                .map(ValidationMessage::getMessage)
                .distinct()
                .sorted()
                .collect(Collectors.joining(System.lineSeparator(),
                        "The " + componentName + " component does not conform to its schema:"
                                + System.lineSeparator(),
                        ""));
    }

    private JsonSchema loadSchema(String schemaName) {
        URL schemaUrl = resource(SCHEMA_ROOT + schemaName);
        URL geoJsonUrl = resource(GEOJSON_ROOT + "Geometry.json");
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaMappers(
                        mappers -> mappers.mapPrefix(GEOJSON_GEOMETRY, geoJsonUrl.toString())));
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                // "$: ..." locations, as the parent's default config prints them; the builder's
                // JSON-pointer default prints root errors as ": ...".
                .pathType(PathType.LEGACY)
                .build();
        return factory.getSchema(SchemaLocation.of(schemaUrl.toString()), config);
    }

    private URL resource(String path) {
        URL url = OfflineSweCommonJsonSchemaValidator.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("Schema resource not found: " + path);
        }
        return url;
    }
}
