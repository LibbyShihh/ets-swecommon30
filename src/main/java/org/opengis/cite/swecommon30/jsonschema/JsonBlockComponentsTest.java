package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
 * Conformance tests for conformance class A.12, Block Components JSON Schema
 * (/conf/json-block-components).
 */
public class JsonBlockComponentsTest {

    private static final String DATA_ARRAY = "DataArray";

    private static final String MATRIX = "Matrix";

    private static final String DATA_STREAM = "DataStream";

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
     * Implements Abstract Test A.63 (/conf/json-block-components/component-types).
     */
    @Test(description = "Implements Abstract Test A.63 (/conf/json-block-components/component-types)")
    public void blockComponentTypes() {
        JsonNode testSubject;
        try {
            testSubject = objectMapper.readTree(testSubjectFile);
        } catch (JsonProcessingException e) {
            Assert.fail("The test subject is not well-formed JSON: " + e.getMessage());
            return;
        } catch (IOException e) {
            throw new SkipException("The test subject could not be read: " + e.getMessage());
        }

        // A.63 tests "documents containing instances of the following data component types:
        // DataArray, Matrix, DataStream", so the whole document is searched, and a document holding
        // any one of the three is tested.
        List<JsonNode> arrays = JsonUtils.findNodesByType(testSubject, DATA_ARRAY);
        List<JsonNode> matrices = JsonUtils.findNodesByType(testSubject, MATRIX);
        List<JsonNode> streams = JsonUtils.findNodesByType(testSubject, DATA_STREAM);
        if (arrays.isEmpty() && matrices.isEmpty() && streams.isEmpty()) {
            throw new SkipException("The test subject contains no DataArray, Matrix or DataStream component.");
        }

        // Clauses 9.4.1, 9.4.2 and 9.4.3 give each type its own schema.
        String arrayReport = JsonSchemaUtils.reportInstances(DATA_ARRAY, arrays,
                JsonSchemaUtils.loadSchema("sweCommon/3.0/json/DataArray.json"));
        String matrixReport = JsonSchemaUtils.reportInstances(MATRIX, matrices,
                JsonSchemaUtils.loadSchema("sweCommon/3.0/json/Matrix.json"));
        String streamReport = JsonSchemaUtils.reportInstances(DATA_STREAM, streams,
                JsonSchemaUtils.loadSchema("sweCommon/3.0/json/DataStream.json"));
        // Only the non-empty reports are joined, so reportInstances' "no trailing separator" contract
        // survives with three of them.
        List<String> parts = new ArrayList<>();
        for (String part : List.of(arrayReport, matrixReport, streamReport)) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        Assert.assertTrue(parts.isEmpty(),
                "The test subject's DataArray, Matrix and DataStream components do not conform to"
                        + " DataArray.json, Matrix.json and DataStream.json:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), parts));
    }
}
