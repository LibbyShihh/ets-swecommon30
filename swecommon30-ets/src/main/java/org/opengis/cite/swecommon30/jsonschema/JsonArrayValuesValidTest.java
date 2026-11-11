package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.util.JsonUtils;
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
 * (/conf/json-encoding-rules): abstract test A.88.
 *
 * <p>The values are checked against their descriptor by the validator module's
 * {@link JsonEncodingRulesValidator}.</p>
 */
public class JsonArrayValuesValidTest {

    private static final Set<String> ARRAY_TYPES = Set.of("DataArray", "Matrix");

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
     * Implements Abstract Test A.88 (/conf/json-encoding-rules/array-values-valid).
     *
     * @param testContext TestNG test context
     */
    @Test(description = "Implements Abstract Test A.88 (/conf/json-encoding-rules/array-values-valid)")
    public void arrayValuesValid(ITestContext testContext) {
        String abstractTest = "A.88";
        String conformancePath = "/conf/json-encoding-rules/array-values-valid";

        JsonNode testSubject = readTestSubject(abstractTest, conformancePath);
        if (testSubject == null) {
            return;
        }

        JsonNode descriptor = EncodedValuesDescriptor.read(testContext, abstractTest, conformancePath);

        List<String> arrayLocations = new ArrayList<>();
        JsonEncodingRulesValidator encodingRules = new JsonEncodingRulesValidator() {
            @Override
            public void validate(JsonNode component, JsonNode value, String location, List<String> violations,
                    Map<String, String> unchecked) {
                if (component != null && ARRAY_TYPES.contains(component.path("type").asText())) {
                    arrayLocations.add(location);
                }
                super.validate(component, value, location, violations, unchecked);
            }
        };
        List<String> violations = new ArrayList<>();
        Map<String, String> unchecked = new LinkedHashMap<>();
        encodingRules.validate(descriptor, testSubject, "values", violations, unchecked);

        // An array is valid only if every element is valid by the element descriptor.
        List<String> arrayViolations = violations.stream()
                .filter(line -> arrayLocations.stream().anyMatch(location -> line.startsWith(location + ":")
                        || line.startsWith(location + ".") || line.startsWith(location + "[")))
                .collect(Collectors.toList());
        Assert.assertTrue(arrayViolations.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The values of DataArray and Matrix components are not JSON Arrays of the fixed size whose elements "
                        + "are valid according to the element descriptor:" + System.lineSeparator() + arrayViolations
                                .stream().map(line -> "  " + line).collect(Collectors.joining(System.lineSeparator()))));

        Set<String> arraySrs = new HashSet<>();
        for (String type : ARRAY_TYPES) {
            for (JsonNode array : JsonUtils.findNodesByType(descriptor, type)) {
                for (JsonNode geometry : JsonUtils.findNodesByType(array, "Geometry")) {
                    arraySrs.add("srs " + geometry.path("srs").asText());
                }
            }
        }
        // A component given by href may hide arrays; an srs matters only to a Geometry within an array.
        List<String> incomplete = unchecked.entrySet().stream()
                .filter(e -> e.getKey().startsWith("descriptor ") || arraySrs.contains(e.getKey()))
                .map(e -> "  " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.toList());
        if (!incomplete.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: A.88 verification is incomplete because these could not be checked:%s%s",
                    abstractTest, conformancePath, System.lineSeparator(),
                    String.join(System.lineSeparator(), incomplete)));
        }

        if (arrayLocations.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the encoded values contain no value of a DataArray or Matrix "
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
