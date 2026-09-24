package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.stream.Collectors;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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
 * Conformance tests for conformance class A.9, Basic Types and Simple Components JSON Schemas
 * (/conf/json-simple-components).
 */
public class JsonSimpleComponentsTest {

    private static final String SCHEMA_ROOT = "/org/opengis/cite/swecommon30/jsonschema/";

    private static final String GEOJSON_GEOMETRY = "https://geojson.org/schema/Geometry.json";

    /** The root "type" values sweCommon.json accepts: every component, plus DataStream. */
    private static final Set<String> SWE_COMMON_TYPES = Set.of(
            "Boolean", "Count", "Quantity", "Time", "Category", "Text",
            "CountRange", "QuantityRange", "TimeRange", "CategoryRange",
            "DataRecord", "Vector", "DataArray", "Matrix", "DataChoice", "Geometry", "DataStream");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode testSubject;

    /**
     * Obtains the downloaded IUT document from the suite context.
     *
     * @param testContext TestNG test context
     */
    @BeforeClass
    public void obtainTestSubject(ITestContext testContext) {
        Object subject = testContext.getSuite().getAttribute(SuiteAttribute.TEST_SUBJ_FILE.getName());
        Assert.assertTrue(subject instanceof File, "The test subject is not available as a file.");
        File testSubjectFile = (File) subject;
        try {
            this.testSubject = objectMapper.readTree(testSubjectFile);
        } catch (IOException e) {
            throw new SkipException("The test subject could not be read as JSON: " + e.getMessage());
        }
    }

    /**
     * Implements Abstract Test A.54 (/conf/json-simple-components/schema-valid).
     */
    @Test(description = "Implements Abstract Test A.54 (/conf/json-simple-components/schema-valid)")
    public void schemaValid() {
        Assert.assertNotNull(testSubject, "No JSON document was supplied.");

        String actualType = testSubject.path("type").asText();
        // SKIP (not FAIL) for other roots such as SensorML. Whether to FAIL, or to extract SWE Common
        // components embedded in them, is pending confirmation with OGC (see issue #9).
        if (!SWE_COMMON_TYPES.contains(actualType)) {
            throw new SkipException(String.format(
                    "The test subject's root type is '%s', not a SWE Common component or DataStream.",
                    actualType));
        }

        Set<ValidationMessage> errors = loadSweCommonSchema().validate(testSubject);
        Assert.assertTrue(errors.isEmpty(), formatValidationErrors(errors));
    }

    private JsonSchema loadSweCommonSchema() {
        URL schemaUrl = resource("sweCommon/3.0/json/sweCommon.json");
        // Geometry.json $refs the GeoJSON schema online; serve the bundled copy so runs work offline.
        URL geoJsonUrl = resource("geojson/Geometry.json");
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaMappers(
                        mappers -> mappers.mapPrefix(GEOJSON_GEOMETRY, geoJsonUrl.toString())));
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                // DateTimeNumberOrSpecial's oneOf only separates "+Infinity" from a date-time when format is asserted.
                .formatAssertionsEnabled(true)
                // "$: ..." locations, as A.1 reports them; the builder's JSON-pointer default prints root errors as ": ...".
                .pathType(PathType.LEGACY)
                .build();
        return factory.getSchema(SchemaLocation.of(schemaUrl.toString()), config);
    }

    private URL resource(String path) {
        URL url = JsonSimpleComponentsTest.class.getResource(SCHEMA_ROOT + path);
        if (url == null) {
            throw new IllegalStateException("Schema resource not found: " + SCHEMA_ROOT + path);
        }
        return url;
    }

    private String formatValidationErrors(Set<ValidationMessage> errors) {
        return errors.stream()
                .map(ValidationMessage::getMessage)
                .distinct()
                .sorted()
                .collect(Collectors.joining(System.lineSeparator(),
                        "The JSON document is not valid against sweCommon.json:" + System.lineSeparator(),
                        ""));
    }
}
