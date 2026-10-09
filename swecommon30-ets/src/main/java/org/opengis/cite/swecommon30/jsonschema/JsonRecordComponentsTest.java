package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * Conformance tests for conformance class A.10, Record Components JSON Schema
 * (/conf/json-record-components).
 *
 * <p>The TestNG workflow remains in the ETS module. Validation of each DataRecord and Vector
 * instance is delegated to the validator module's offline validator.</p>
 */
public class JsonRecordComponentsTest {

    private static final String DATA_RECORD = "DataRecord";

    private static final String VECTOR = "Vector";

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
     * Implements Abstract Test A.61 (/conf/json-record-components/component-types).
     */
    @Test(description = "Implements Abstract Test A.61 (/conf/json-record-components/component-types)")
    public void recordComponentTypes() {
        String abstractTest = "A.61";
        String conformancePath = "/conf/json-record-components/component-types";

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

        // A.61 tests "documents containing instances of the following data component types:
        // DataRecord, Vector", so the whole document is searched, and a document holding either type
        // is tested.
        List<JsonNode> records = JsonUtils.findNodesByType(testSubject, DATA_RECORD);
        List<JsonNode> vectors = JsonUtils.findNodesByType(testSubject, VECTOR);
        if (records.isEmpty() && vectors.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject contains no DataRecord or Vector component.",
                    abstractTest, conformancePath));
        }

        // Clauses 9.2.1 and 9.2.2 give each type its own schema.
        String report;
        try {
            report = Stream.of(
                    ComponentInstanceReport.report(validator, DATA_RECORD, records, "DataRecord.json"),
                    ComponentInstanceReport.report(validator, VECTOR, vectors, "Vector.json"))
                    .filter(part -> !part.isEmpty())
                    .collect(Collectors.joining(System.lineSeparator()));
        } catch (RuntimeException e) {
            throw new IllegalStateException(executionError(abstractTest, conformancePath,
                    "Unable to load or execute schema 'DataRecord.json' or 'Vector.json': " + detailMessage(e)), e);
        }

        Assert.assertTrue(report.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The test subject's DataRecord and Vector components do not conform to DataRecord.json"
                        + " and Vector.json:" + System.lineSeparator() + report));
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
