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
 * Conformance tests for conformance class A.17, JSON Encoding Rules
 * (/conf/json-encoding-rules): abstract test A.83.
 *
 * <p>The values are checked against their descriptor by the validator module's
 * {@link JsonEncodingRulesValidator}.</p>
 */
public class JsonScalarValueValidTest {

    private static final List<String> SCALAR_TYPES = List.of("Boolean", "Text", "Category", "Count", "Quantity",
            "Time");

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
     * Implements Abstract Test A.83 (/conf/json-encoding-rules/scalar-value-valid).
     *
     * @param testContext TestNG test context
     */
    @Test(description = "Implements Abstract Test A.83 (/conf/json-encoding-rules/scalar-value-valid)")
    public void scalarValueValid(ITestContext testContext) {
        String abstractTest = "A.83";
        String conformancePath = "/conf/json-encoding-rules/scalar-value-valid";

        JsonNode testSubject = readTestSubject(abstractTest, conformancePath);
        if (testSubject == null) {
            return;
        }

        JsonNode descriptor = EncodedValuesDescriptor.read(testContext, abstractTest, conformancePath);

        List<String> violations = new ArrayList<>();
        Map<String, String> unchecked = new LinkedHashMap<>();
        encodingRules.validate(descriptor, testSubject, "values", violations, unchecked);

        List<String> scalarViolations = violations.stream()
                .filter(line -> line.contains(": A.83 "))
                .collect(Collectors.toList());
        Assert.assertTrue(scalarViolations.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The values of scalar components are not encoded with the data type Table 3 specifies:"
                        + System.lineSeparator() + scalarViolations.stream().map(line -> "  " + line)
                                .collect(Collectors.joining(System.lineSeparator()))));

        // An srs only matters to A.89; a component given by href may hide scalars.
        List<String> incomplete = unchecked.entrySet().stream()
                .filter(e -> e.getKey().startsWith("descriptor "))
                .map(e -> "  " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.toList());
        if (!incomplete.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: A.83 verification is incomplete because these could not be checked:%s%s",
                    abstractTest, conformancePath, System.lineSeparator(),
                    String.join(System.lineSeparator(), incomplete)));
        }

        if (SCALAR_TYPES.stream().allMatch(type -> JsonUtils.findNodesByType(descriptor, type).isEmpty())) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the descriptor describes no Boolean, Text, Category, "
                            + "Count, Quantity or Time component.",
                    abstractTest, conformancePath));
        }
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
