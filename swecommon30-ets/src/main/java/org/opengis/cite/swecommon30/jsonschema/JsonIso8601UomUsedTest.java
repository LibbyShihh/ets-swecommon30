package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
 * (/conf/json-simple-components): abstract test A.60.
 *
 * <p>The TestNG workflow remains in the ETS module. The test subject is read through the validator
 * module; no schema is run.</p>
 */
public class JsonIso8601UomUsedTest {

    /** The URI Requirement 60 names as the value of "uom/href". */
    private static final String ISO_8601_UOM = "http://www.opengis.net/def/uom/ISO-8601/0/Gregorian";

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
     * Implements Abstract Test A.60 (/conf/json-simple-components/iso8601-uom-used).
     */
    @Test(description = "Implements Abstract Test A.60 (/conf/json-simple-components/iso8601-uom-used)")
    public void iso8601UomUsed() {
        String abstractTest = "A.60";
        String conformancePath = "/conf/json-simple-components/iso8601-uom-used";

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

        // The test method: "Check that the uom attribute is set to the URI ... if the inline value is set to
        // a ISO8601 string." Requirement 60 names the "Time" element, and the inline value is the Time's own
        // "value"; values in constraints, nil values and encoded blocks are not inline.
        List<JsonNode> instances = JsonUtils.findNodesByType(testSubject, "Time");
        List<String> failures = new ArrayList<>();
        boolean applicable = false;
        for (int i = 0; i < instances.size(); i++) {
            JsonNode value = instances.get(i).get("value");
            if (!isIso8601String(value)) {
                continue;
            }
            applicable = true;
            JsonNode href = instances.get(i).path("uom").path("href");
            if (!href.isTextual() || !ISO_8601_UOM.equals(href.asText())) {
                failures.add(describe("Time instance", instances.get(i), i, instances.size())
                        + " value '" + value.asText() + "' with "
                        + (href.isTextual() ? "uom/href '" + href.asText() + "'" : "no uom/href"));
            }
        }
        if (!applicable) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject contains no Time whose inline value "
                            + "is an ISO 8601 string.",
                    abstractTest, conformancePath));
        }
        Assert.assertTrue(failures.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The test subject has Time components whose inline value is an ISO 8601 string but whose "
                        + "uom/href is not " + ISO_8601_UOM + ":" + System.lineSeparator()
                        + String.join(System.lineSeparator(), failures)));
    }

    /** An ISO 8601 date-time string, the form the schema's "format": "date-time" gives a Time value. */
    private boolean isIso8601String(JsonNode value) {
        if (value == null) {
            return false;
        }
        try {
            OffsetDateTime.parse(value.asText());
            return true;
        } catch (DateTimeParseException e) {
            // OffsetDateTime rejects a leap second (23:59:60); ISO_INSTANT reads it.
        }
        try {
            DateTimeFormatter.ISO_INSTANT.parse(value.asText());
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
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
