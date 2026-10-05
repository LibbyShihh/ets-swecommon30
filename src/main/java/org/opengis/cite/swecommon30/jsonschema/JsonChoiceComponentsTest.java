package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.util.JsonSchemaUtils;
import org.opengis.cite.swecommon30.util.JsonUtils;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Conformance tests for conformance class A.11, Choice Components JSON Schema
 * (/conf/json-choice-components).
 */
public class JsonChoiceComponentsTest {

    private static final String DATA_CHOICE = "DataChoice";

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
     * Implements Abstract Test A.62 (/conf/json-choice-components/component-types).
     */
    @Test(description = "Implements Abstract Test A.62 (/conf/json-choice-components/component-types)")
    public void choiceComponentTypes() {
        JsonNode testSubject;
        try {
            testSubject = objectMapper.readTree(testSubjectFile);
        } catch (JsonProcessingException e) {
            Assert.fail("The test subject is not well-formed JSON: " + e.getMessage());
            return;
        } catch (IOException e) {
            throw new SkipException("The test subject could not be read: " + e.getMessage());
        }

        // A.62 tests "documents containing instances of the DataChoice component", so the whole
        // document is searched; a DataChoice may sit inside a DataRecord, a DataStream or a
        // SensorML description.
        List<JsonNode> choices = JsonUtils.findNodesByType(testSubject, DATA_CHOICE);
        if (choices.isEmpty()) {
            throw new SkipException("The test subject contains no DataChoice component.");
        }

        String report = JsonSchemaUtils.reportInstances(DATA_CHOICE, choices,
                JsonSchemaUtils.loadSchema("sweCommon/3.0/json/DataChoice.json"));
        Assert.assertTrue(report.isEmpty(),
                "The test subject's DataChoice components do not conform to DataChoice.json:"
                        + System.lineSeparator() + report);
    }
}
