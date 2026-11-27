package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.validation.JsonEncodingRulesValidator;
import org.opengis.cite.swecommon30.validation.SweCommonJsonSchemaValidator;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Conformance tests for conformance class A.17, JSON Encoding Rules
 * (/conf/json-encoding-rules): abstract test A.89.
 *
 * <p>The values are checked against their descriptor by the validator module's
 * {@link JsonEncodingRulesValidator}.</p>
 */
public class JsonGeometryValidTest {

    private final SweCommonJsonSchemaValidator validator = new SweCommonJsonSchemaValidator();

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
     * Implements Abstract Test A.89 (/conf/json-encoding-rules/geometry-valid).
     *
     * @param testContext TestNG test context
     */
    @Test(description = "Implements Abstract Test A.89 (/conf/json-encoding-rules/geometry-valid)")
    public void geometryValid(ITestContext testContext) {
        String abstractTest = "A.89";
        String conformancePath = "/conf/json-encoding-rules/geometry-valid";

        JsonNode testSubject = readTestSubject(abstractTest, conformancePath);
        if (testSubject == null) {
            return;
        }

        JsonNode descriptor = EncodedValuesDescriptor.read(testContext, abstractTest, conformancePath);

        List<String> geometries = new ArrayList<>();
        JsonEncodingRulesValidator encodingRules = new JsonEncodingRulesValidator() {
            @Override
            public void validate(JsonNode component, JsonNode value, String location, List<String> violations,
                    Map<String, String> unchecked) {
                if (component != null && "Geometry".equals(component.path("type").asText())) {
                    geometries.add(location);
                }
                super.validate(component, value, location, violations, unchecked);
            }
        };
        List<String> violations = new ArrayList<>();
        Map<String, String> unchecked = new LinkedHashMap<>();
        encodingRules.validate(descriptor, testSubject, "values", violations, unchecked);

        List<String> geometryViolations = violations.stream()
                .filter(line -> geometries.stream().anyMatch(location -> line.startsWith(location + ":")
                        || line.startsWith(location + ".") || line.startsWith(location + "[")))
                .collect(Collectors.toList());
        Assert.assertTrue(geometryViolations.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The values of Geometry components are not valid GeoJSON geometries of an allowed type with as many "
                        + "coordinates as the CRS has dimensions:" + System.lineSeparator()
                        + geometryViolations.stream().map(line -> "  " + line)
                                .collect(Collectors.joining(System.lineSeparator()))));

        String noResponse = "no HTTP response from ";
        List<String> unreachable = unchecked.entrySet().stream()
                .filter(e -> e.getKey().startsWith("srs ") && e.getValue().contains(noResponse))
                .map(e -> "  Unable to retrieve definition from "
                        + e.getValue().substring(e.getValue().indexOf(noResponse) + noResponse.length()))
                .collect(Collectors.toList());
        Assert.assertTrue(unreachable.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The CRS definitions of the Geometry components could not be retrieved:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), unreachable)));

        // The CRS dimension is A.89's own check, so an srs that could not be looked up counts too.
        List<String> incomplete = unchecked.entrySet().stream()
                .map(e -> "  " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.toList());
        if (!incomplete.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: A.89 verification is incomplete because these could not be checked:%s%s",
                    abstractTest, conformancePath, System.lineSeparator(),
                    String.join(System.lineSeparator(), incomplete)));
        }

        if (geometries.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the encoded values contain no value of a Geometry "
                            + "component.",
                    abstractTest, conformancePath));
        }
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
