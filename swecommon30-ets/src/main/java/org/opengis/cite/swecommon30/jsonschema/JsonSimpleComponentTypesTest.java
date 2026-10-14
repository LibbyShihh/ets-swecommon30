package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
 * Conformance tests for conformance class A.9, Basic Types and Simple Components JSON Schemas
 * (/conf/json-simple-components): abstract test A.53.
 *
 * <p>The TestNG workflow remains in the ETS module. Validation of each simple component instance is
 * delegated to the validator module's offline validator.</p>
 */
public class JsonSimpleComponentTypesTest {

    private static final String SCHEMA_NAME = "sweCommon.json";

    /** The data component types A.53's test method lists, in its order. */
    private static final List<String> SIMPLE_COMPONENT_TYPES = List.of(
            "Boolean", "Text", "Category", "Count", "Quantity", "Time",
            "CategoryRange", "CountRange", "QuantityRange", "TimeRange");

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
     * Implements Abstract Test A.53 (/conf/json-simple-components/component-types).
     */
    @Test(description = "Implements Abstract Test A.53 (/conf/json-simple-components/component-types)")
    public void simpleComponentTypes() {
        String abstractTest = "A.53";
        String conformancePath = "/conf/json-simple-components/component-types";

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

        // A.53 tests "documents containing instances of the following data component types", so the
        // whole document is searched, and a document holding any one of them is tested.
        List<List<JsonNode>> instancesByType = SIMPLE_COMPONENT_TYPES.stream()
                .map(type -> JsonUtils.findNodesByType(testSubject, type))
                .collect(Collectors.toList());
        if (instancesByType.stream().allMatch(List::isEmpty)) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject contains no Boolean, Text, Category, "
                            + "Count, Quantity, Time, CategoryRange, CountRange, QuantityRange or TimeRange component.",
                    abstractTest, conformancePath));
        }

        // A.53 names no schema of its own. Clause 9.1 names the one schema for this class: "The entry
        // point schema used for validation is sweCommon.json".
        String report;
        try {
            report = IntStream.range(0, SIMPLE_COMPONENT_TYPES.size())
                    .mapToObj(i -> ComponentInstanceReport.report(validator, SIMPLE_COMPONENT_TYPES.get(i),
                            instancesByType.get(i), SCHEMA_NAME))
                    .filter(part -> !part.isEmpty())
                    .collect(Collectors.joining(System.lineSeparator()));
        } catch (RuntimeException e) {
            throw new IllegalStateException(executionError(abstractTest, conformancePath,
                    "Unable to load or execute schema '" + SCHEMA_NAME + "': " + detailMessage(e)), e);
        }

        Assert.assertTrue(report.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The test subject's simple components do not conform to " + SCHEMA_NAME + ":"
                        + System.lineSeparator() + report));
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
