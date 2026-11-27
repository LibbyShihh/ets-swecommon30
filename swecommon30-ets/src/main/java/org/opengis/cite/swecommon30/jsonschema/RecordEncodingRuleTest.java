package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opengis.cite.swecommon30.SuiteAttribute;
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
 * Conformance tests for conformance class A.16, General Encoding Rules
 * (/conf/general-encoding-rules): abstract test A.78.
 *
 * <p>The values are walked along their descriptor by the validator module's
 * {@link JsonEncodingRulesValidator}.</p>
 */
public class RecordEncodingRuleTest {

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
     * Implements Abstract Test A.78 (/conf/general-encoding-rules/record-encoding-rule).
     *
     * @param testContext TestNG test context
     */
    @Test(description = "Implements Abstract Test A.78 (/conf/general-encoding-rules/record-encoding-rule)")
    public void recordEncodingRule(ITestContext testContext) {
        String abstractTest = "A.78";
        String conformancePath = "/conf/general-encoding-rules/record-encoding-rule";

        JsonNode testSubject = readTestSubject(abstractTest, conformancePath);
        if (testSubject == null) {
            return;
        }

        JsonNode descriptor = EncodedValuesDescriptor.read(testContext, abstractTest, conformancePath);

        List<String> records = new ArrayList<>();
        List<String> orderViolations = new ArrayList<>();
        JsonEncodingRulesValidator encodingRules = new JsonEncodingRulesValidator() {
            @Override
            public void validate(JsonNode component, JsonNode value, String location, List<String> violations,
                    Map<String, String> unchecked) {
                String type = component == null ? "" : component.path("type").asText();
                if (("DataRecord".equals(type) || "Vector".equals(type)) && !component.has("href")
                        && value.isObject()) {
                    records.add(location);
                    String membersKey = "DataRecord".equals(type) ? "fields" : "coordinates";
                    List<String> names = new ArrayList<>();
                    component.path(membersKey).forEach(member -> names.add(member.path("name").asText()));
                    List<String> present = new ArrayList<>();
                    value.fieldNames().forEachRemaining(name -> {
                        if (names.contains(name)) {
                            present.add(name);
                        }
                    });
                    List<String> expected = names.stream().filter(present::contains).collect(Collectors.toList());
                    if (!present.equals(expected)) {
                        orderViolations.add(String.format("%s: A.78: the members are in the order %s, but the %s's "
                                + "%s are listed as %s", location, present, type, membersKey, names));
                    }
                }
                super.validate(component, value, location, violations, unchecked);
            }
        };
        List<String> violations = new ArrayList<>();
        Map<String, String> unchecked = new LinkedHashMap<>();
        encodingRules.validate(descriptor, testSubject, "values", violations, unchecked);

        Assert.assertTrue(orderViolations.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The values of DataRecord and Vector components are not encoded in the order in which the "
                        + "descriptor lists their fields or coordinates:" + System.lineSeparator()
                        + orderViolations.stream().map(line -> "  " + line)
                                .collect(Collectors.joining(System.lineSeparator()))));

        // A component given by href may hide records or vectors; an srs is not A.78's.
        List<String> incomplete = unchecked.entrySet().stream()
                .filter(e -> e.getKey().startsWith("descriptor "))
                .map(e -> "  " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.toList());
        if (!incomplete.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: A.78 verification is incomplete because these could not be checked:%s%s",
                    abstractTest, conformancePath, System.lineSeparator(),
                    String.join(System.lineSeparator(), incomplete)));
        }

        if (records.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the encoded values contain no DataRecord or Vector "
                            + "value encoded as a JSON Object.",
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
