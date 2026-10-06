package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.validation.SweCommonJsonSchemaValidator;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;

/**
 * Conformance tests for SWE Common scalar components.
 *
 * <p>The TestNG workflow remains in the ETS module. Reusable JSON schema
 * validation is delegated to the SWE Common validator module.</p>
 */
public class CoreScalarComponentsTest {

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
     * Implements Abstract Test A.2 (/conf/core/boolean-rep-valid).
     */
    @Test(description = "Implements Abstract Test A.2 (/conf/core/boolean-rep-valid)")
    public void Boolean() {
        assertScalarComponentConforms("A.2", "/conf/core/boolean-rep-valid", "Boolean", "Boolean.json");
    }

    /**
     * Implements Abstract Test A.3 (/conf/core/categorical-rep-valid).
     */
    @Test(description = "Implements Abstract Test A.3 (/conf/core/categorical-rep-valid)")
    public void Category() {
        assertScalarComponentConforms("A.3", "/conf/core/categorical-rep-valid", "Category", "Category.json");
    }

    /**
     * Implements Abstract Test A.4 (/conf/core/numerical-rep-valid).
     */
    @Test(description = "Implements Abstract Test A.4 (/conf/core/numerical-rep-valid)")
    public void Quantity() {
        assertScalarComponentConforms("A.4", "/conf/core/numerical-rep-valid", "Quantity", "Quantity.json");
    }

    /**
     * Implements Abstract Test A.5 (/conf/core/countable-rep-valid).
     */
    @Test(description = "Implements Abstract Test A.5 (/conf/core/countable-rep-valid)")
    public void Count() {
        assertScalarComponentConforms("A.5", "/conf/core/countable-rep-valid", "Count", "Count.json");
    }

    /**
     * Implements Abstract Test A.6 (/conf/core/textual-rep-valid).
     */
    @Test(description = "Implements Abstract Test A.6 (/conf/core/textual-rep-valid)")
    public void Text() {
        assertScalarComponentConforms("A.6", "/conf/core/textual-rep-valid", "Text", "Text.json");
    }

    private void assertScalarComponentConforms(String abstractTest, String conformancePath,
            String expectedType, String schemaName) {
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

        String actualType = typeNode.asText();
        if (!expectedType.equals(actualType)) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject is a '%s' component, "
                            + "but this test requires '%s'.",
                    abstractTest, conformancePath, actualType, expectedType));
        }

        Set<ValidationMessage> errors;
        try {
            errors = validator.validate(testSubject, schemaName);
        } catch (RuntimeException e) {
            throw new IllegalStateException(executionError(abstractTest, conformancePath,
                    "Unable to load or execute schema '" + schemaName + "': " + detailMessage(e)), e);
        }

        String validationMessage = dataValidationError(abstractTest, conformancePath,
                "The test subject does not conform to schema '" + schemaName + "'."
                        + System.lineSeparator() + validator.formatValidationErrors(expectedType, errors));
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
