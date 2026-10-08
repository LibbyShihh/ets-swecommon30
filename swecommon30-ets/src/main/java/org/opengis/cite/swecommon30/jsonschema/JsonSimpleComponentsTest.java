package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.validation.OfflineSweCommonJsonSchemaValidator;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;

/**
 * Conformance tests for conformance class A.9, Basic Types and Simple Components JSON Schemas
 * (/conf/json-simple-components).
 *
 * <p>The TestNG workflow remains in the ETS module. Validation against {@code sweCommon.json} is
 * delegated to the validator module's offline validator.</p>
 */
public class JsonSimpleComponentsTest {

    private static final String SCHEMA_NAME = "sweCommon.json";

    /** The root "type" values sweCommon.json accepts: every component, plus DataStream. */
    private static final Set<String> SWE_COMMON_TYPES = Set.of(
            "Boolean", "Count", "Quantity", "Time", "Category", "Text",
            "CountRange", "QuantityRange", "TimeRange", "CategoryRange",
            "DataRecord", "Vector", "DataArray", "Matrix", "DataChoice", "Geometry", "DataStream");

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
     * Implements Abstract Test A.54 (/conf/json-simple-components/schema-valid).
     */
    @Test(description = "Implements Abstract Test A.54 (/conf/json-simple-components/schema-valid)")
    public void schemaValid() {
        String abstractTest = "A.54";
        String conformancePath = "/conf/json-simple-components/schema-valid";

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

        // Other roots, such as SensorML documents, are not SWE Common documents. Extracting SWE
        // Common components embedded in them is not something A.54's test method describes.
        String actualType = typeNode.asText();
        if (!SWE_COMMON_TYPES.contains(actualType)) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject is a '%s' document, "
                            + "but this test requires a SWE Common component or DataStream at the root.",
                    abstractTest, conformancePath, actualType));
        }

        Set<ValidationMessage> errors;
        try {
            errors = validator.validate(testSubject, SCHEMA_NAME);
        } catch (RuntimeException e) {
            throw new IllegalStateException(executionError(abstractTest, conformancePath,
                    "Unable to load or execute schema '" + SCHEMA_NAME + "': " + detailMessage(e)), e);
        }

        String validationMessage = dataValidationError(abstractTest, conformancePath,
                "The test subject does not conform to schema '" + SCHEMA_NAME + "'."
                        + System.lineSeparator() + validator.formatValidationErrors(actualType, errors));
        Assert.assertTrue(errors.isEmpty(), validationMessage);
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
