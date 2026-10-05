package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.util.JsonSchemaUtils;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;

/**
 * Conformance tests for conformance class A.9, Basic Types and Simple Components JSON Schemas
 * (/conf/json-simple-components).
 */
public class JsonSimpleComponentsTest {

    /** The root "type" values sweCommon.json accepts: every component, plus DataStream. */
    private static final Set<String> SWE_COMMON_TYPES = Set.of(
            "Boolean", "Count", "Quantity", "Time", "Category", "Text",
            "CountRange", "QuantityRange", "TimeRange", "CategoryRange",
            "DataRecord", "Vector", "DataArray", "Matrix", "DataChoice", "Geometry", "DataStream");

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
     * Implements Abstract Test A.54 (/conf/json-simple-components/schema-valid).
     */
    @Test(description = "Implements Abstract Test A.54 (/conf/json-simple-components/schema-valid)")
    public void schemaValid() {
        JsonNode testSubject;
        try {
            testSubject = objectMapper.readTree(testSubjectFile);
        } catch (JsonProcessingException e) {
            Assert.fail("The test subject is not well-formed JSON: " + e.getMessage());
            return;
        } catch (IOException e) {
            throw new SkipException("The test subject could not be read: " + e.getMessage());
        }

        String actualType = testSubject.path("type").asText();
        // SKIP (not FAIL) for other roots such as SensorML. Whether to FAIL, or to extract SWE Common
        // components embedded in them, is pending confirmation with OGC (see issue #9).
        if (!SWE_COMMON_TYPES.contains(actualType)) {
            throw new SkipException(String.format(
                    "The test subject's root type is '%s', not a SWE Common component or DataStream.",
                    actualType));
        }

        Set<ValidationMessage> errors = JsonSchemaUtils.loadSchema("sweCommon/3.0/json/sweCommon.json")
                .validate(testSubject);
        Assert.assertTrue(errors.isEmpty(), formatValidationErrors(errors));
    }

    private String formatValidationErrors(Set<ValidationMessage> errors) {
        return errors.stream()
                .map(ValidationMessage::getMessage)
                .distinct()
                .sorted()
                .collect(Collectors.joining(System.lineSeparator(),
                        "The JSON document is not valid against sweCommon.json:" + System.lineSeparator(),
                        ""));
    }
}
