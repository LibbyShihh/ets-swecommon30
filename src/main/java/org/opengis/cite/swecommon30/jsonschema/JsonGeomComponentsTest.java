package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.util.JsonUtils;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Conformance tests for conformance class A.13, Geometry Components JSON Schema
 * (/conf/json-geom-components).
 */
public class JsonGeomComponentsTest {

    private static final String SCHEMA_ROOT = "/org/opengis/cite/swecommon30/jsonschema/";

    private static final String GEOJSON_GEOMETRY = "https://geojson.org/schema/Geometry.json";

    private static final String GEOMETRY = "Geometry";

    private static final Set<String> TESTED_VALUE_TYPES = Set.of("Point", "LineString", "Polygon");

    private static final Set<String> UNTESTED_VALUE_TYPES = Set.of("MultiPoint", "MultiLineString", "MultiPolygon");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private File testSubjectFile;

    /**
     * Obtains the downloaded IUT document file reference from the suite context.
     *
     * @param testContext TestNG test context
     */
    @BeforeClass
    public void obtainTestSubject(ITestContext testContext) {
        Object subject = testContext.getSuite().getAttribute(SuiteAttribute.TEST_SUBJ_FILE.getName());
        Assert.assertTrue(subject instanceof File, "The test subject is not available as a file.");
        this.testSubjectFile = (File) subject;
    }

    /**
     * Implements Abstract Test A.66 (/conf/json-geom-components/component-types).
     */
    @Test(description = "Implements Abstract Test A.66 (/conf/json-geom-components/component-types)")
    public void geomComponentTypes() {
        JsonNode testSubject;
        try {
            testSubject = objectMapper.readTree(testSubjectFile);
        } catch (JsonProcessingException e) {
            Assert.fail("The test subject is not well-formed JSON: " + e.getMessage());
            return;
        } catch (IOException e) {
            throw new SkipException("The test subject could not be read: " + e.getMessage());
        }

        // A.66 tests "documents containing instances of the Geometry data component with the
        // following value types: Point, LineString, Polygon", so the whole document is searched and
        // only those Geometries are tested.
        List<JsonNode> geoms = JsonUtils.findNodesByType(testSubject, GEOMETRY).stream()
                .filter(this::hasTestedValueType)
                .collect(Collectors.toList());
        if (geoms.isEmpty()) {
            throw new SkipException("The test subject contains no Geometry component with a Point,"
                    + " LineString or Polygon value type.");
        }

        JsonSchema schema = loadGeometrySchema();
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < geoms.size(); i++) {
            JsonNode geom = geoms.get(i);
            Set<ValidationMessage> errors = schema.validate(geom);
            if (!errors.isEmpty()) {
                // No trailing separator after the last block: TestNG appends " expected [true]
                // but found [false]" straight onto the message, and a dangling newline here would
                // push that suffix onto its own line instead.
                if (report.length() > 0) {
                    report.append(System.lineSeparator());
                }
                report.append(describe(geom, i, geoms.size())).append(System.lineSeparator())
                      .append(formatValidationErrors(errors));
            }
        }
        Assert.assertTrue(report.length() == 0,
                "The test subject's Geometry components do not conform to Geometry.json:"
                        + System.lineSeparator() + report);
    }

    /**
     * Whether a Geometry falls under A.66's value types. With a value, its GeoJSON type decides.
     * "value" is optional; without one, the Geometry is a descriptor whose constraint lists the
     * geometry types a value may take, and it is left out when that list names a Multi* type.
     */
    private boolean hasTestedValueType(JsonNode geom) {
        if (geom.has("value")) {
            return TESTED_VALUE_TYPES.contains(geom.path("value").path("type").asText());
        }
        for (JsonNode geomType : geom.path("constraint").path("geomTypes")) {
            if (UNTESTED_VALUE_TYPES.contains(geomType.asText())) {
                return false;
            }
        }
        return true;
    }

    private JsonSchema loadGeometrySchema() {
        URL schemaUrl = resource("sweCommon/3.0/json/Geometry.json");
        // Geometry's value $refs the GeoJSON schema online; serve the bundled copy so runs work
        // offline.
        URL geoJsonUrl = resource("geojson/Geometry.json");
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaMappers(
                        mappers -> mappers.mapPrefix(GEOJSON_GEOMETRY, geoJsonUrl.toString())));
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                // Same configuration as A.54 and A.62: "srs" and "definition" are format "uri", and
                // only an asserted format rejects a value that isn't one.
                .formatAssertionsEnabled(true)
                // "$: ..." locations, as A.1 and A.54 report them; the builder's JSON-pointer default
                // prints root errors as ": ...".
                .pathType(PathType.LEGACY)
                .build();
        return factory.getSchema(SchemaLocation.of(schemaUrl.toString()), config);
    }

    private URL resource(String path) {
        URL url = JsonGeomComponentsTest.class.getResource(SCHEMA_ROOT + path);
        if (url == null) {
            throw new IllegalStateException("Schema resource not found: " + SCHEMA_ROOT + path);
        }
        return url;
    }

    /** Names one instance, so a failure says which Geometry in the document it is about. */
    private String describe(JsonNode geom, int index, int total) {
        String hint = "";
        for (String key : new String[] { "name", "label", "id" }) {
            if (geom.hasNonNull(key) && geom.get(key).isTextual()) {
                hint = " (" + key + ": '" + geom.get(key).asText() + "')";
                break;
            }
        }
        return "Geometry instance " + (index + 1) + " of " + total + hint + ":";
    }

    private String formatValidationErrors(Set<ValidationMessage> errors) {
        return errors.stream()
                .map(ValidationMessage::getMessage)
                .distinct()
                .sorted()
                .map(message -> "  " + message)
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
