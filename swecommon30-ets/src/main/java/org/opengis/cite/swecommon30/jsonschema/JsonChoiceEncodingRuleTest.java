package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
 * (/conf/general-encoding-rules): abstract test A.79.
 *
 * <p>The values are decoded by the JSON encoding rules, walking them along their descriptor with
 * the validator module's {@link JsonEncodingRulesValidator}.</p>
 */
public class JsonChoiceEncodingRuleTest {

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
     * Implements Abstract Test A.79 (/conf/general-encoding-rules/choice-encoding-rule).
     *
     * @param testContext TestNG test context
     */
    @Test(description = "Implements Abstract Test A.79 (/conf/general-encoding-rules/choice-encoding-rule)")
    public void choiceEncodingRule(ITestContext testContext) {
        String abstractTest = "A.79";
        String conformancePath = "/conf/general-encoding-rules/choice-encoding-rule";

        JsonNode testSubject = readTestSubject(abstractTest, conformancePath);
        if (testSubject == null) {
            return;
        }

        JsonNode descriptor = EncodedValuesDescriptor.read(testContext, abstractTest, conformancePath);

        List<String> choices = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        JsonEncodingRulesValidator encodingRules = new JsonEncodingRulesValidator() {
            @Override
            public void validate(JsonNode component, JsonNode value, String location, List<String> walked,
                    Map<String, String> unchecked) {
                if (component != null && "DataChoice".equals(component.path("type").asText())) {
                    choices.add(location);
                    checkChoice(component, value, location, violations);
                }
                super.validate(component, value, location, walked, unchecked);
            }
        };
        Map<String, String> unchecked = new LinkedHashMap<>();
        encodingRules.validate(descriptor, testSubject, "values", new ArrayList<>(), unchecked);

        Assert.assertTrue(violations.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The values of DataChoice components do not include a value identifying the selected item and "
                        + "values for that item:" + System.lineSeparator() + violations.stream()
                                .map(line -> "  " + line).collect(Collectors.joining(System.lineSeparator()))));

        // A component given by href may hide choices; an srs is never needed to decode a choice.
        List<String> incomplete = unchecked.entrySet().stream()
                .filter(e -> e.getKey().startsWith("descriptor "))
                .map(e -> "  " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.toList());
        if (!incomplete.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: A.79 verification is incomplete because these could not be checked:%s%s",
                    abstractTest, conformancePath, System.lineSeparator(),
                    String.join(System.lineSeparator(), incomplete)));
        }

        if (choices.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the encoded values contain no value of a DataChoice "
                            + "component.",
                    abstractTest, conformancePath));
        }
    }

    /** Clause 10.2.4: a JSON Object's single member names the selected item, and its value holds the item's values. */
    private void checkChoice(JsonNode component, JsonNode value, String location, List<String> violations) {
        if (!value.isObject() || value.size() == 0) {
            violations.add(String.format("%s: A.79: expects a JSON Object with a member naming the selected item; "
                    + "found %s", location, found(value)));
            return;
        }
        List<String> names = new ArrayList<>();
        value.fieldNames().forEachRemaining(names::add);
        if (names.size() > 1) {
            violations.add(String.format("%s: A.79: members %s do not identify a single selected item", location,
                    names));
            return;
        }
        String name = names.get(0);
        Set<String> items = StreamSupport.stream(component.path("items").spliterator(), false)
                .map(item -> item.path("name").asText())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!items.contains(name)) {
            violations.add(String.format("%s: A.79: member '%s' does not identify one of the DataChoice's items %s",
                    location, name, items));
        } else if (value.get(name).isNull()) {
            violations.add(String.format("%s: A.79: no values are given for the selected item '%s'; found null",
                    location, name));
        }
    }

    private String found(JsonNode value) {
        if (value.isObject()) {
            return "a JSON Object with " + value.size() + (value.size() == 1 ? " member" : " members");
        }
        if (value.isArray()) {
            return "a JSON Array of " + value.size() + (value.size() == 1 ? " value" : " values");
        }
        return value.toString();
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
