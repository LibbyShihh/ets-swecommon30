package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.util.JsonUtils;
import org.opengis.cite.swecommon30.validation.SweCommonJsonSchemaValidator;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Conformance tests for conformance class A.9, Basic Types and Simple Components JSON Schemas
 * (/conf/json-simple-components): abstract test A.59.
 *
 * <p>The TestNG workflow remains in the ETS module. The test subject is read through the validator
 * module; no schema is run, and no unit definition is requested over the network.</p>
 */
public class JsonUcumCodeUsedTest {

    /** The types whose schema defines "uom". */
    private static final List<String> UOM_TYPES = List.of("Quantity", "QuantityRange", "Time", "TimeRange");

    /** The OGC's two forms of the URI of a UCUM code: its definition server's UCUM register, and the URN. */
    private static final List<String> UCUM_URI_PREFIXES = List.of(
            "http://www.opengis.net/def/uom/UCUM/",
            "urn:ogc:def:uom:UCUM:");

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
     * Implements Abstract Test A.59 (/conf/json-simple-components/ucum-code-used).
     */
    @Test(description = "Implements Abstract Test A.59 (/conf/json-simple-components/ucum-code-used)")
    public void ucumCodeUsed() {
        String abstractTest = "A.59";
        String conformancePath = "/conf/json-simple-components/ucum-code-used";

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

        // The test method: "If the uom attribute is set to a URI of a UCUM code, check that the specified
        // unit cannot be represented by UCUM." A unit named by a UCUM code can always be represented by
        // UCUM, so every uom that meets the condition fails, whether or not a uom/code is also given.
        List<String> failures = new ArrayList<>();
        for (String type : UOM_TYPES) {
            List<JsonNode> instances = JsonUtils.findNodesByType(testSubject, type);
            for (int i = 0; i < instances.size(); i++) {
                String href = instances.get(i).path("uom").path("href").asText();
                if (UCUM_URI_PREFIXES.stream().anyMatch(href::startsWith)) {
                    failures.add(describe(type + " instance", instances.get(i), i, instances.size())
                            + " uom/href '" + href + "'");
                }
            }
        }
        if (failures.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject contains no Quantity, QuantityRange, "
                            + "Time or TimeRange whose uom/href is the URI of a UCUM code.",
                    abstractTest, conformancePath));
        }
        Assert.assertTrue(failures.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The test subject has units of measure given as the URI of a UCUM code, which can be represented "
                        + "by UCUM:" + System.lineSeparator() + String.join(System.lineSeparator(), failures)));
    }

    /** Names one instance, so a failure says which one in the document it is about. */
    private String describe(String prefix, JsonNode instance, int index, int total) {
        String hint = "";
        for (String key : new String[] { "name", "label", "id" }) {
            if (instance.hasNonNull(key) && instance.get(key).isTextual()) {
                hint = " (" + key + ": '" + instance.get(key).asText() + "')";
                break;
            }
        }
        return prefix + " " + (index + 1) + " of " + total + hint + ":";
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
