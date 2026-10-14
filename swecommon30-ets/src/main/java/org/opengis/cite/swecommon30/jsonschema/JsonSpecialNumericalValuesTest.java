package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.Set;

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
 * (/conf/json-simple-components): abstract test A.55.
 *
 * <p>The TestNG workflow remains in the ETS module. The test subject is read through the validator
 * module; no schema is run, because a special value found is always one the schema accepts.</p>
 */
public class JsonSpecialNumericalValuesTest {

    /** The special values Requirement 56 names. */
    private static final Set<String> SPECIAL_VALUES = Set.of("NaN", "-Infinity", "+Infinity");

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
     * Implements Abstract Test A.55 (/conf/json-simple-components/special-numerical-values).
     */
    @Test(description = "Implements Abstract Test A.55 (/conf/json-simple-components/special-numerical-values)")
    public void specialNumericalValues() {
        String abstractTest = "A.55";
        String conformancePath = "/conf/json-simple-components/special-numerical-values";

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

        // Requirement 56 is a permission: the special values "shall be allowed". A document cannot
        // break it, only use the values, so A.55 passes where they are used and is not applicable
        // where they are not. An unquoted NaN is not JSON and has already failed above.
        if (!containsSpecialValue(testSubject)) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject contains no NaN, -Infinity or +Infinity "
                            + "value of a Quantity or Time component (excluding Time components that use the "
                            + "ISO 8601 format).",
                    abstractTest, conformancePath));
        }
    }

    /**
     * Looks for a special value where Requirement 56 places it: "the inline or out-of-band value for
     * Quantity and Time components". The inline value is the component's own "value"; out-of-band
     * values are the JSON-encoded "values" blocks of DataArray and Matrix components in the document.
     */
    private boolean containsSpecialValue(JsonNode testSubject) {
        for (String type : new String[] { "Quantity", "Time" }) {
            for (JsonNode component : JsonUtils.findNodesByType(testSubject, type)) {
                if (isSpecialValueOf(component, component.get("value"))) {
                    return true;
                }
            }
        }
        for (String type : new String[] { "DataArray", "Matrix" }) {
            for (JsonNode array : JsonUtils.findNodesByType(testSubject, type)) {
                // "Special JSON Strings" only exist in JSON-encoded blocks. A block with no encoding
                // is JSON-encoded, as Requirement 85 A allows the JSONEncoding to be omitted.
                JsonNode encoding = array.get("encoding");
                boolean jsonEncoded = encoding == null || "JSONEncoding".equals(encoding.path("type").asText());
                if (jsonEncoded && array.path("values").isArray()
                        && containsSpecialValue(array, array.get("values"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Walks an encoded value by the JSON encoding rules of clause 10.2. Records and vectors are read
     * by their JSON shape (object by member name, array by position), so no encoding flag is
     * consulted. Anything that cannot be mapped to a Quantity or Time is ignored: whether a block
     * follows the encoding rules is not what A.55 checks.
     */
    private boolean containsSpecialValue(JsonNode component, JsonNode encoded) {
        if (component == null || encoded == null || encoded.isNull()) {
            return false;
        }
        switch (component.path("type").asText()) {
        case "Quantity":
        case "Time":
            return isSpecialValueOf(component, encoded);
        case "DataRecord":
            return containsSpecialMemberValue(component.path("fields"), encoded);
        case "Vector":
            return containsSpecialMemberValue(component.path("coordinates"), encoded);
        case "DataChoice":
            // Requirement 87 B: a single member named like the selected choice item.
            if (encoded.isObject() && encoded.size() == 1) {
                String itemName = encoded.fieldNames().next();
                return containsSpecialValue(member(component.path("items"), itemName), encoded.get(itemName));
            }
            return false;
        case "DataArray":
        case "Matrix":
            // Requirement 88 B: each array element is encoded like the element type.
            if (encoded.isArray()) {
                for (JsonNode element : encoded) {
                    if (containsSpecialValue(component.get("elementType"), element)) {
                        return true;
                    }
                }
            }
            return false;
        default:
            return false;
        }
    }

    /** Requirements 85 and 86, B (JSON object, by member name) and C (JSON array, by position). */
    private boolean containsSpecialMemberValue(JsonNode members, JsonNode encoded) {
        if (encoded.isObject()) {
            for (JsonNode member : members) {
                String name = member.path("name").asText();
                if (encoded.has(name) && containsSpecialValue(member, encoded.get(name))) {
                    return true;
                }
            }
        } else if (encoded.isArray()) {
            for (int i = 0; i < encoded.size() && i < members.size(); i++) {
                if (containsSpecialValue(members.get(i), encoded.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private JsonNode member(JsonNode members, String name) {
        for (JsonNode member : members) {
            if (name.equals(member.path("name").asText())) {
                return member;
            }
        }
        return null;
    }

    /**
     * Whether a value of a Quantity or Time is one of Requirement 56's special values. Time components
     * that use the ISO 8601 format are excepted by Requirement 56 and never count.
     */
    private boolean isSpecialValueOf(JsonNode component, JsonNode value) {
        if (value == null || !value.isTextual() || !SPECIAL_VALUES.contains(value.asText())) {
            return false;
        }
        return !("Time".equals(component.path("type").asText())
                && ISO_8601_UOM.equals(component.path("uom").path("href").asText()));
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
