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
            this.testSubject = validator.readJson(testSubjectFile);
        } catch (IOException e) {
            throw new SkipException("The test subject could not be read as JSON: " + e.getMessage());
        }
    }

    /**
     * Implements Abstract Test A.2 (/conf/core/boolean-rep-valid).
     */
    @Test(description = "Implements Abstract Test A.2 (/conf/core/boolean-rep-valid)")
    public void Boolean() {
        assertScalarComponentConforms("Boolean", "Boolean.json");
    }

    /**
     * Implements Abstract Test A.3 (/conf/core/categorical-rep-valid).
     */
    @Test(description = "Implements Abstract Test A.3 (/conf/core/categorical-rep-valid)")
    public void Category() {
        assertScalarComponentConforms("Category", "Category.json");
    }

    /**
     * Implements Abstract Test A.4 (/conf/core/numerical-rep-valid).
     */
    @Test(description = "Implements Abstract Test A.4 (/conf/core/numerical-rep-valid)")
    public void Quantity() {
        assertScalarComponentConforms("Quantity", "Quantity.json");
    }

    /**
     * Implements Abstract Test A.5 (/conf/core/countable-rep-valid).
     */
    @Test(description = "Implements Abstract Test A.5 (/conf/core/countable-rep-valid)")
    public void Count() {
        assertScalarComponentConforms("Count", "Count.json");
    }

    /**
     * Implements Abstract Test A.6 (/conf/core/textual-rep-valid).
     */
    @Test(description = "Implements Abstract Test A.6 (/conf/core/textual-rep-valid)")
    public void Text() {
        assertScalarComponentConforms("Text", "Text.json");
    }

    private void assertScalarComponentConforms(String expectedType, String schemaName) {
        Assert.assertNotNull(testSubject, "No JSON document was supplied.");

        String actualType = testSubject.path("type").asText();
        if (!expectedType.equals(actualType)) {
            throw new SkipException(
                    String.format("The test subject is a %s component, not a %s component.", actualType, expectedType));
        }

        Set<ValidationMessage> errors = validator.validate(testSubject, schemaName);
        Assert.assertTrue(errors.isEmpty(), validator.formatValidationErrors(expectedType, errors));
    }
}
