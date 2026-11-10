package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

/**
 * Conformance tests for conformance class A.12, Block Components JSON Schema
 * (/conf/json-block-components): abstract test A.64.
 *
 * <p>The TestNG workflow remains in the ETS module. The test subject is read through the validator
 * module, and the values are checked by its {@link JsonEncodingRulesValidator}.</p>
 */
public class JsonEncodedValuesTest {

    /** The block components whose inline values clause 9.4.4 names, in A.63's order. */
    private static final List<String> BLOCK_TYPES = List.of("DataArray", "Matrix", "DataStream");

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
     * Implements Abstract Test A.64 (/conf/json-block-components/encoded-values-valid).
     */
    @Test(description = "Implements Abstract Test A.64 (/conf/json-block-components/encoded-values-valid)")
    public void encodedValuesValid() {
        String abstractTest = "A.64";
        String conformancePath = "/conf/json-block-components/encoded-values-valid";

        // A.82: the inline values are part of the document, so they are valid JSON once it parses.
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

        // A.64 runs A.82-A.89 on "the inline values": the values of every block component in the
        // document that are given inline rather than by href. The declared encoding is not consulted,
        // because none of those tests looks at it.
        List<String> reports = new ArrayList<>();
        Map<String, String> unchecked = new LinkedHashMap<>();
        int tested = 0;
        for (String type : BLOCK_TYPES) {
            List<JsonNode> blocks = JsonUtils.findNodesByType(testSubject, type).stream()
                    .filter(this::hasInlineValues)
                    .collect(Collectors.toList());
            tested += blocks.size();
            for (int i = 0; i < blocks.size(); i++) {
                JsonNode block = blocks.get(i);
                List<String> violations = new ArrayList<>();
                encodingRules.validate(block, block.get("values"), "values", violations, unchecked);
                if (!violations.isEmpty()) {
                    reports.add(describe(type, block, i, blocks.size()) + System.lineSeparator()
                            + violations.stream().map(line -> "  " + line)
                                    .collect(Collectors.joining(System.lineSeparator())));
                }
            }
        }
        if (tested == 0) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject contains no DataArray, Matrix or "
                            + "DataStream with inline values.",
                    abstractTest, conformancePath));
        }

        Assert.assertTrue(reports.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The test subject's inline values do not follow the JSON encoding rules:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), reports)));

        if (!unchecked.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: The inline values follow the JSON encoding rules as far as they could be "
                            + "checked, but these could not be checked:%s%s",
                    abstractTest, conformancePath, System.lineSeparator(),
                    unchecked.entrySet().stream().map(e -> "  " + e.getKey() + ": " + e.getValue())
                            .collect(Collectors.joining(System.lineSeparator()))));
        }
    }

    /** Values given inline: present, and not an href to values provided elsewhere. */
    private boolean hasInlineValues(JsonNode block) {
        JsonNode values = block.get("values");
        return values != null && !(values.isObject() && values.has("href"));
    }

    /** Names one block, so a failure says which one in the document it is about. */
    private String describe(String typeName, JsonNode block, int index, int total) {
        String hint = "";
        for (String key : new String[] { "name", "label", "id" }) {
            if (block.hasNonNull(key) && block.get(key).isTextual()) {
                hint = " (" + key + ": '" + block.get(key).asText() + "')";
                break;
            }
        }
        return typeName + " instance " + (index + 1) + " of " + total + hint + ":";
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
