package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.util.JsonUtils;
import org.opengis.cite.swecommon30.validation.JsonEncodingRulesValidator;
import org.opengis.cite.swecommon30.validation.SweCommonJsonSchemaValidator;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

/**
 * Conformance tests for conformance class A.17, JSON Encoding Rules
 * (/conf/json-encoding-rules): abstract test A.85.
 *
 * <p>The values are checked against their descriptor by the validator module's
 * {@link JsonEncodingRulesValidator}.</p>
 */
public class JsonRecordObjectValidTest {

    /** One step of a violation's location: an array index, or a member name. */
    private static final Pattern STEP = Pattern.compile("(\\[\\d+\\])|\\.([^.\\[]+)");

    private final SweCommonJsonSchemaValidator validator = new SweCommonJsonSchemaValidator();

    private final JsonEncodingRulesValidator encodingRules = new JsonEncodingRulesValidator();

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
     * Implements Abstract Test A.85 (/conf/json-encoding-rules/record-object-valid).
     *
     * @param testContext TestNG test context
     */
    @Test(description = "Implements Abstract Test A.85 (/conf/json-encoding-rules/record-object-valid)")
    public void recordObjectValid(ITestContext testContext) {
        String abstractTest = "A.85";
        String conformancePath = "/conf/json-encoding-rules/record-object-valid";

        JsonNode testSubject = readTestSubject(abstractTest, conformancePath);
        if (testSubject == null) {
            return;
        }

        JsonNode descriptor = EncodedValuesDescriptor.read(testContext, abstractTest, conformancePath);

        List<String> violations = new ArrayList<>();
        Map<String, String> unchecked = new LinkedHashMap<>();
        encodingRules.validate(descriptor, testSubject, "values", violations, unchecked);

        // A field is valid by its descriptor only if everything nested in its value is.
        List<String> recordViolations = violations.stream()
                .filter(line -> line.contains(": A.85") || insideRecord(descriptor, line))
                .collect(Collectors.toList());
        Assert.assertTrue(recordViolations.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The values of DataRecord components are not JSON Objects whose members match the fields and are "
                        + "valid according to the field descriptors:" + System.lineSeparator() + recordViolations
                                .stream().map(line -> "  " + line).collect(Collectors.joining(System.lineSeparator()))));

        List<JsonNode> records = JsonUtils.findNodesByType(descriptor, "DataRecord");
        Set<String> recordSrs = new HashSet<>();
        for (JsonNode record : records) {
            for (JsonNode geometry : JsonUtils.findNodesByType(record, "Geometry")) {
                recordSrs.add("srs " + geometry.path("srs").asText());
            }
        }
        // A component given by href may hide records; an srs matters only to a Geometry within a record.
        List<String> incomplete = unchecked.entrySet().stream()
                .filter(e -> e.getKey().startsWith("descriptor ") || recordSrs.contains(e.getKey()))
                .map(e -> "  " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.toList());
        if (!incomplete.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: A.85 verification is incomplete because these could not be checked:%s%s",
                    abstractTest, conformancePath, System.lineSeparator(),
                    String.join(System.lineSeparator(), incomplete)));
        }

        if (records.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the descriptor describes no DataRecord component.",
                    abstractTest, conformancePath));
        }
    }

    /** Whether a violation's location, e.g. "values[0].wind.speed", passes through a DataRecord. */
    private boolean insideRecord(JsonNode descriptor, String violation) {
        String location = violation.substring(0, violation.indexOf(": "));
        Matcher step = STEP.matcher(location.substring("values".length()));
        JsonNode component = descriptor;
        while (step.find()) {
            if ("DataRecord".equals(component.path("type").asText())) {
                return true;
            }
            component = step.group(1) != null ? component.path("elementType") : member(component, step.group(2));
        }
        return false;
    }

    private JsonNode member(JsonNode component, String name) {
        for (String key : List.of("fields", "coordinates", "items")) {
            for (JsonNode member : component.path(key)) {
                if (name.equals(member.path("name").asText())) {
                    return member;
                }
            }
        }
        return MissingNode.getInstance();
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
