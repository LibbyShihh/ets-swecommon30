package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
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
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Conformance tests for SWE Common scalar components.
 *
 * <p>Each abstract test maps to one {@code @Test} method, while the schema
 * loading and validation logic is shared.</p>
 */
public class CoreScalarComponentsTest {

    private static final String SCHEMA_ROOT =
            "/org/opengis/cite/swecommon30/jsonschema/sweCommon/3.0/json/";

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

        JsonSchema schema = loadSchema(schemaName);
        Set<ValidationMessage> errors = schema.validate(testSubject);
        Assert.assertTrue(errors.isEmpty(), formatValidationErrors(expectedType, errors));
    }

    private JsonSchema loadSchema(String schemaName) {
        URL schemaUrl = CoreScalarComponentsTest.class.getResource(SCHEMA_ROOT + schemaName);
        if (schemaUrl == null) {
            throw new IllegalStateException("Schema resource not found: " + SCHEMA_ROOT + schemaName);
        }
        try {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaUrl.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid schema resource URI: " + schemaUrl, e);
        }
    }

    private String formatValidationErrors(String componentName, Set<ValidationMessage> errors) {
        return errors.stream()
                .map(ValidationMessage::getMessage)
                .sorted()
                .collect(Collectors.joining(System.lineSeparator(),
                        "The " + componentName + " component does not conform to its schema:"
                                + System.lineSeparator(),
                        ""));
    }
}
