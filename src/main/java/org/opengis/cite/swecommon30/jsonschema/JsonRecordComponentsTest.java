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
 * Conformance tests for conformance class A.10, Record Components JSON Schema
 * (/conf/json-record-components).
 */
public class JsonRecordComponentsTest {

    private static final String DATA_RECORD = "DataRecord";

    private static final String VECTOR = "Vector";

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
     * Implements Abstract Test A.61 (/conf/json-record-components/component-types).
     */
    @Test(description = "Implements Abstract Test A.61 (/conf/json-record-components/component-types)")
    public void recordComponentTypes() {
        JsonNode testSubject;
        try {
            testSubject = objectMapper.readTree(testSubjectFile);
        } catch (JsonProcessingException e) {
            Assert.fail("The test subject is not well-formed JSON: " + e.getMessage());
            return;
        } catch (IOException e) {
            throw new SkipException("The test subject could not be read: " + e.getMessage());
        }

        // A.61 tests "documents containing instances of the following data component types:
        // DataRecord, Vector", so the whole document is searched, and a document holding either type
        // is tested.
        List<JsonNode> records = JsonUtils.findNodesByType(testSubject, DATA_RECORD);
        List<JsonNode> vectors = JsonUtils.findNodesByType(testSubject, VECTOR);
        if (records.isEmpty() && vectors.isEmpty()) {
            throw new SkipException("The test subject contains no DataRecord or Vector component.");
        }

        // Clauses 9.2.1 and 9.2.2 give each type its own schema.
        String recordReport = JsonSchemaUtils.reportInstances(DATA_RECORD, records,
                JsonSchemaUtils.loadSchema("sweCommon/3.0/json/DataRecord.json"));
        String vectorReport = JsonSchemaUtils.reportInstances(VECTOR, vectors,
                JsonSchemaUtils.loadSchema("sweCommon/3.0/json/Vector.json"));
        String report = recordReport.isEmpty() || vectorReport.isEmpty()
                ? recordReport + vectorReport
                : recordReport + System.lineSeparator() + vectorReport;
        Assert.assertTrue(report.isEmpty(),
                "The test subject's DataRecord and Vector components do not conform to DataRecord.json"
                        + " and Vector.json:" + System.lineSeparator() + report);
    }
}
