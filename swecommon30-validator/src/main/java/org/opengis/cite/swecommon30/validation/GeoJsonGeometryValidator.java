package org.opengis.cite.swecommon30.validation;

import java.net.URL;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Validates a single GeoJSON geometry, such as an encoded Geometry value, against the bundled
 * copy of the GeoJSON {@code Geometry.json} schema, without network access.
 *
 * <p>{@link SweCommonJsonSchemaValidator} and {@link OfflineSweCommonJsonSchemaValidator} only load
 * schemas from the SWE Common schema folder, so neither can validate a bare GeoJSON value.</p>
 */
public class GeoJsonGeometryValidator {

    private static final String GEOJSON_GEOMETRY = "/org/opengis/cite/swecommon30/jsonschema/geojson/Geometry.json";

    private JsonSchema schema;

    /**
     * Validates a JSON value against the GeoJSON Geometry schema.
     *
     * @param geometry JSON value to validate
     * @return validation messages; empty when the value is a valid GeoJSON geometry
     */
    public Set<ValidationMessage> validate(JsonNode geometry) {
        if (schema == null) {
            schema = loadSchema();
        }
        return schema.validate(geometry);
    }

    private JsonSchema loadSchema() {
        URL url = GeoJsonGeometryValidator.class.getResource(GEOJSON_GEOMETRY);
        if (url == null) {
            throw new IllegalStateException("Schema resource not found: " + GEOJSON_GEOMETRY);
        }
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                // "$: ..." locations, as the other validators print them.
                .pathType(PathType.LEGACY)
                .build();
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(SchemaLocation.of(url.toString()), config);
    }
}
