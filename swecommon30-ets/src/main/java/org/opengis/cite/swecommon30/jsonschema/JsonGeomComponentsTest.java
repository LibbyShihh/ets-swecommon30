package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.util.JsonUtils;
import org.opengis.cite.swecommon30.validation.ComponentInstanceReport;
import org.opengis.cite.swecommon30.validation.OfflineSweCommonJsonSchemaValidator;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Conformance tests for conformance class A.13, Geometry Components JSON Schema
 * (/conf/json-geom-components).
 *
 * <p>The TestNG workflow remains in the ETS module. Validation of each Geometry instance is
 * delegated to the validator module's offline validator.</p>
 */
public class JsonGeomComponentsTest {

    private static final String GEOMETRY = "Geometry";

    private static final String SCHEMA_NAME = "Geometry.json";

    private static final Set<String> TESTED_VALUE_TYPES = Set.of("Point", "LineString", "Polygon");

    private static final Set<String> UNTESTED_VALUE_TYPES = Set.of("MultiPoint", "MultiLineString", "MultiPolygon");

    private final OfflineSweCommonJsonSchemaValidator validator = new OfflineSweCommonJsonSchemaValidator();

    private File testSubjectFile;

    /**
     * Obtains the downloaded IUT document file from the suite context.
     * JSON parsing is intentionally deferred to the individual abstract test so
     * malformed JSON is reported as a data validation failure, not as a setup skip.
     *
     * @param testContext TestNG test context
     */
    @BeforeClass
    public void obtainTestSubject(ITestContext testContext) {
        if (testContext == null || testContext.getSuite() == null) {
            throw new IllegalStateException(
                    "Test execution error: The TestNG suite context is not available.");
        }

        Object subject = testContext.getSuite().getAttribute(SuiteAttribute.TEST_SUBJ_FILE.getName());
        if (!(subject instanceof File)) {
            throw new IllegalStateException(
                    "Test execution error: The IUT file reference is not available in the TestNG suite context.");
        }
        this.testSubjectFile = (File) subject;
    }

    /**
     * Implements Abstract Test A.66 (/conf/json-geom-components/component-types).
     */
    @Test(description = "Implements Abstract Test A.66 (/conf/json-geom-components/component-types)")
    public void geomComponentTypes() {
        String abstractTest = "A.66";
        String conformancePath = "/conf/json-geom-components/component-types";

        JsonNode testSubject = readTestSubject(abstractTest, conformancePath);
        if (testSubject == null) {
            return;
        }

        if (!testSubject.isObject()) {
            Assert.fail(dataValidationError(abstractTest, conformancePath,
                    "The test subject JSON root must be an object."));
            return;
        }

        JsonNode typeNode = testSubject.get("type");
        if (typeNode == null || !typeNode.isTextual() || typeNode.asText().isBlank()) {
            Assert.fail(dataValidationError(abstractTest, conformancePath,
                    "The test subject must contain a non-empty string root property 'type'."));
            return;
        }

        // A.66 tests "documents containing instances of the Geometry data component with the
        // following value types: Point, LineString, Polygon", so the whole document is searched and
        // only those Geometries are tested.
        List<JsonNode> geoms = JsonUtils.findNodesByType(testSubject, GEOMETRY).stream()
                .filter(this::hasTestedValueType)
                .collect(Collectors.toList());
        if (geoms.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject contains no Geometry component "
                            + "with a Point, LineString or Polygon value type.",
                    abstractTest, conformancePath));
        }

        String report;
        try {
            report = ComponentInstanceReport.report(validator, GEOMETRY, geoms, SCHEMA_NAME);
        } catch (RuntimeException e) {
            throw new IllegalStateException(executionError(abstractTest, conformancePath,
                    "Unable to load or execute schema '" + SCHEMA_NAME + "': " + detailMessage(e)), e);
        }

        Assert.assertTrue(report.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The test subject's Geometry components do not conform to " + SCHEMA_NAME + ":"
                        + System.lineSeparator() + report));
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

    private JsonNode readTestSubject(String abstractTest, String conformancePath) {
        try {
            JsonNode testSubject = validator.readJson(testSubjectFile);
            if (testSubject == null || testSubject.isNull()) {
                Assert.fail(dataValidationError(abstractTest, conformancePath,
                        "The test subject is empty or contains a JSON null value."));
                return null;
            }
            return testSubject;
        } catch (JsonProcessingException e) {
            Assert.fail(dataValidationError(abstractTest, conformancePath,
                    "The test subject is not well-formed JSON: " + detailMessage(e)));
            return null;
        } catch (IOException e) {
            throw new IllegalStateException(executionError(abstractTest, conformancePath,
                    "Unable to read the test subject file: " + detailMessage(e)), e);
        }
    }

    private String executionError(String abstractTest, String conformancePath, String detail) {
        return String.format("Test execution error [%s %s]: %s", abstractTest, conformancePath, detail);
    }

    private String dataValidationError(String abstractTest, String conformancePath, String detail) {
        return String.format("Data validation failure [%s %s]: %s", abstractTest, conformancePath, detail);
    }

    private String detailMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
