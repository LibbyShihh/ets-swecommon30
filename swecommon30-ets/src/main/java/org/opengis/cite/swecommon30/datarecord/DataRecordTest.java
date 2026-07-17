package org.opengis.cite.swecommon30.physicalsystem;


import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.opengis.cite.swecommon30.BaseJsonSchemaValidatorTest;
import org.opengis.cite.swecommon30.CommonFixture;
import org.opengis.cite.swecommon30.SuiteAttribute;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;

public class DataRecordTest extends CommonFixture {
    private File testSubject;

    /**
     * Obtains the test subject from the ISuite context. The suite attribute
     * {@link org.opengis.cite.swecommon30.SuiteAttribute#TEST_SUBJECT} should
     * evaluate to a DOM Document node.
     *
     * @param testContext The test (group) context.
     */
    @BeforeClass
    public void obtainTestSubject(ITestContext testContext) {
        Object obj = testContext.getSuite().getAttribute(SuiteAttribute.TEST_SUBJECT.getName());
        this.testSubject = (File) obj;
    }

    /**
     * Sets the test subject. This method is intended to facilitate unit
     * testing.
     *
     * @param testSubject A Document node representing the test subject or
     *                    metadata about it.
     */
    public void setTestSubject(File testSubject) {
        this.testSubject = testSubject;
    }

    @Test(description = "Implements Abstract Test A.78 (/conf/general-encoding-rules/record-encoding-rule)")
    public void verifyDataRecord() {
        if (!testSubject.isFile()) {
            Assert.assertTrue(testSubject.isFile(), "No file selected. ");
        }

        String schemaToApply = SCHEMA_PATH + "DataRecord.json";
        StringBuffer sb = new StringBuffer();

        try {
            BaseJsonSchemaValidatorTest tester = new BaseJsonSchemaValidatorTest();


            JsonSchema schema = tester.getJsonSchemaFromUrl("https://raw.githubusercontent.com/opengeospatial/ets-swecommon30/refs/heads/main/src/main/resources/org/opengis/cite/swecommon30/jsonschema/sweCommon/3.0/json/DataRecord.json");
            JsonNode rootNode = tester.getNodeFromFile(testSubject);

            Set<ValidationMessage> errors = schema.validate(rootNode);

            Iterator<ValidationMessage> it = errors.iterator();
            while (it.hasNext()) {
                sb.append("Test subject has error " + it.next() + ".\n");
            }

        } catch (Exception e) {
            sb.append(e.getMessage());
            e.printStackTrace();
        }
        Assert.assertTrue(sb.toString().length() == 0, sb.toString());
    }
}
