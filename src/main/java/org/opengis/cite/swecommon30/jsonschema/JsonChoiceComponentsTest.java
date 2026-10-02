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
 * Conformance tests for conformance class A.11, Choice Components JSON Schema
 * (/conf/json-choice-components).
 */
public class JsonChoiceComponentsTest {

    private static final String SCHEMA_ROOT = "/org/opengis/cite/swecommon30/jsonschema/";

    private static final String GEOJSON_GEOMETRY = "https://geojson.org/schema/Geometry.json";

    private static final String DATA_CHOICE = "DataChoice";

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
     * Implements Abstract Test A.62 (/conf/json-choice-components/component-types).
     */
    @Test(description = "Implements Abstract Test A.62 (/conf/json-choice-components/component-types)")
    public void choiceComponentTypes() {
        JsonNode testSubject;
        try {
            testSubject = objectMapper.readTree(testSubjectFile);
        } catch (JsonProcessingException e) {
            Assert.fail("The test subject is not well-formed JSON: " + e.getMessage());
            return;
        } catch (IOException e) {
            throw new SkipException("The test subject could not be read: " + e.getMessage());
        }

        // A.62 tests "documents containing instances of the DataChoice component", so the whole
        // document is searched; a DataChoice may sit inside a DataRecord, a DataStream or a
        // SensorML description.
        List<JsonNode> choices = JsonUtils.findNodesByType(testSubject, DATA_CHOICE);
        if (choices.isEmpty()) {
            throw new SkipException("The test subject contains no DataChoice component.");
        }

        JsonSchema schema = loadDataChoiceSchema();
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < choices.size(); i++) {
            JsonNode choice = choices.get(i);
            Set<ValidationMessage> errors = schema.validate(choice);
            if (!errors.isEmpty()) {
                // No trailing separator after the last block: TestNG appends " expected [true]
                // but found [false]" straight onto the message, and a dangling newline here would
                // push that suffix onto its own line instead.
                if (report.length() > 0) {
                    report.append(System.lineSeparator());
                }
                report.append(describe(choice, i, choices.size())).append(System.lineSeparator())
                      .append(formatValidationErrors(errors));
            }
        }
        Assert.assertTrue(report.length() == 0,
                "The test subject's DataChoice components do not conform to DataChoice.json:"
                        + System.lineSeparator() + report);
    }

    private JsonSchema loadDataChoiceSchema() {
        URL schemaUrl = resource("sweCommon/3.0/json/DataChoice.json");
        // A DataChoice item may be a Geometry, whose value $refs the GeoJSON schema online;
        // serve the bundled copy so runs work offline.
        URL geoJsonUrl = resource("geojson/Geometry.json");
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaMappers(
                        mappers -> mappers.mapPrefix(GEOJSON_GEOMETRY, geoJsonUrl.toString())));
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                // DateTimeNumberOrSpecial's oneOf only separates "+Infinity" from a date-time when
                // format is asserted.
                .formatAssertionsEnabled(true)
                // "$: ..." locations, as A.1 and A.54 report them; the builder's JSON-pointer default
                // prints root errors as ": ...".
                .pathType(PathType.LEGACY)
                .build();
        return factory.getSchema(SchemaLocation.of(schemaUrl.toString()), config);
    }

    private URL resource(String path) {
        URL url = JsonChoiceComponentsTest.class.getResource(SCHEMA_ROOT + path);
        if (url == null) {
            throw new IllegalStateException("Schema resource not found: " + SCHEMA_ROOT + path);
        }
        return url;
    }

    /** Names one instance, so a failure says which DataChoice in the document it is about. */
    private String describe(JsonNode choice, int index, int total) {
        String hint = "";
        for (String key : new String[] { "name", "label", "id" }) {
            if (choice.hasNonNull(key) && choice.get(key).isTextual()) {
                hint = " (" + key + ": '" + choice.get(key).asText() + "')";
                break;
            }
        }
        return "DataChoice instance " + (index + 1) + " of " + total + hint + ":";
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
