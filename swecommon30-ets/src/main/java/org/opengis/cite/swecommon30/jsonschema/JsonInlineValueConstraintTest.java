package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.util.JsonUtils;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;

/**
 * Conformance tests for conformance class A.9, Basic Types and Simple Components JSON Schemas
 * (/conf/json-simple-components): abstract test A.57.
 *
 * <p>The TestNG workflow remains in the ETS module. No schema is run: the constraint schemas in
 * basicTypes.json check a constraint's shape, never whether the inline value satisfies it.</p>
 */
public class JsonInlineValueConstraintTest {

    /** The simple component types whose schema defines a constraint, in A.53's order. */
    private static final List<String> CONSTRAINED_TYPES = List.of(
            "Text", "Category", "Count", "Quantity", "Time",
            "CategoryRange", "CountRange", "QuantityRange", "TimeRange");

    /**
     * Trailing zeros are significant figures (clause 8.2.18), so decimals are read exactly: a default
     * mapper would read 12.2300 as the double 12.23.
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);

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
     * Implements Abstract Test A.57 (/conf/json-simple-components/inline-value-constraint-valid).
     */
    @Test(description = "Implements Abstract Test A.57 (/conf/json-simple-components/inline-value-constraint-valid)")
    public void inlineValueConstraintValid() {
        String abstractTest = "A.57";
        String conformancePath = "/conf/json-simple-components/inline-value-constraint-valid";

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

        // Req 58 covers every "JSON instance validating against the AbstractSimpleComponent.json schema",
        // which includes the elementCount of a DataArray or Matrix (basicTypes.json ElementCount). Every
        // inline value is checked, wherever the component sits in the document.
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (String type : CONSTRAINED_TYPES) {
            List<JsonNode> instances = JsonUtils.findNodesByType(testSubject, type);
            for (int i = 0; i < instances.size(); i++) {
                JsonNode instance = instances.get(i);
                List<String> reasons = new ArrayList<>();
                if (hasValueAndConstraint(instance) && compare(type, instance, reasons)) {
                    checked++;
                    if (!reasons.isEmpty()) {
                        violations.add(describe(type + " instance", instance, i, instances.size()) + " "
                                + String.join("; ", reasons));
                    }
                }
            }
        }
        for (String type : new String[] { "DataArray", "Matrix" }) {
            List<JsonNode> arrays = JsonUtils.findNodesByType(testSubject, type);
            for (int i = 0; i < arrays.size(); i++) {
                JsonNode elementCount = arrays.get(i).path("elementCount");
                List<String> reasons = new ArrayList<>();
                if (hasValueAndConstraint(elementCount) && compare("Count", elementCount, reasons)) {
                    checked++;
                    if (!reasons.isEmpty()) {
                        violations.add(describe("elementCount of " + type + " instance", arrays.get(i), i,
                                arrays.size()) + " " + String.join("; ", reasons));
                    }
                }
            }
        }

        // A.57's "(if any)": a component without both an inline value and a constraint has nothing to check,
        // and neither has one whose constraint holds nothing this test can read. Only a real comparison
        // counts toward a PASS.
        if (checked == 0) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject contains no simple component or "
                            + "elementCount with both an inline value and a constraint it can be compared with.",
                    abstractTest, conformancePath));
        }

        Assert.assertTrue(violations.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The test subject's inline values do not satisfy their constraints:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations)));
    }

    private boolean hasValueAndConstraint(JsonNode component) {
        return component.hasNonNull("value") && component.path("constraint").isObject();
    }

    /**
     * Compares a component's inline value with its constraint, adding why it does not satisfy it to
     * reasons. Each bound of a range value is checked on its own, like the scalar value.
     *
     * @return whether the constraint held anything the value could be compared with
     */
    private boolean compare(String type, JsonNode component, List<String> reasons) {
        JsonNode constraint = component.get("constraint");
        JsonNode value = component.get("value");
        String scalarType = type.replace("Range", "");
        boolean compared = false;
        if (type.endsWith("Range") && value.isArray()) {
            for (int i = 0; i < value.size(); i++) {
                compared |= check(scalarType, constraint, value.get(i), "value[" + i + "]", reasons);
            }
        } else {
            compared = check(scalarType, constraint, value, "value", reasons);
        }
        return compared;
    }

    private boolean check(String scalarType, JsonNode constraint, JsonNode value, String label, List<String> reasons) {
        boolean textual = "Text".equals(scalarType) || "Category".equals(scalarType);
        Boolean satisfied = textual ? satisfiesTokens(constraint, value) : satisfiesValues(constraint, value);
        if (Boolean.FALSE.equals(satisfied)) {
            reasons.add(label + " " + value + " does not satisfy constraint " + constraint);
        }
        boolean compared = satisfied != null;

        // Clause 8.2.18: significantFigures is "only applicable ... within the Quantity class" and "limits
        // the total number of digits that can be included in the number represented whether a scientific
        // notation is used or not". Leading zeros do not count and trailing zeros do, which is the
        // precision of the exact decimal.
        JsonNode limit = constraint.get("significantFigures");
        if ("Quantity".equals(scalarType) && limit != null && limit.isIntegralNumber() && value.isNumber()) {
            compared = true;
            int digits = value.decimalValue().precision();
            if (digits > limit.intValue()) {
                reasons.add(label + " " + value + " has " + digits + " significant figures, more than the "
                        + limit.intValue() + " allowed");
            }
        }
        return compared;
    }

    /**
     * Clause 8.2.17: "The value must then either be one of the enumerated tokens or match the pattern."
     * A match anywhere in the token is enough, as for a JSON Schema pattern. A pattern that does not
     * compile is ignored; whether the constraint is well formed is not what A.57 checks. Returns null
     * when no token or pattern can be read, as there is then nothing to compare.
     */
    private Boolean satisfiesTokens(JsonNode constraint, JsonNode value) {
        List<String> tokens = new ArrayList<>();
        for (JsonNode token : constraint.path("values")) {
            if (token.isTextual()) {
                tokens.add(token.asText());
            }
        }
        Pattern pattern = null;
        if (constraint.path("pattern").isTextual()) {
            try {
                pattern = Pattern.compile(constraint.get("pattern").asText());
            } catch (PatternSyntaxException e) {
                // Left to A.53 and A.54, which check the schema's "format": "regex".
            }
        }
        if (tokens.isEmpty() && pattern == null) {
            return null;
        }
        if (!value.isTextual()) {
            return false;
        }
        return tokens.contains(value.asText()) || (pattern != null && pattern.matcher(value.asText()).find());
    }

    /**
     * Clause 8.2.18: "To be valid, the value must either be one of the enumerated values or included in one
     * of the intervals." Clause 8.2.19 uses AllowedTimes the same way, with time values. An interval that is
     * not a pair of comparable bounds is ignored; whether the constraint is well formed is not what A.57
     * checks. Returns null when no value or interval can be read, as there is then nothing to compare.
     */
    private Boolean satisfiesValues(JsonNode constraint, JsonNode value) {
        List<Point> allowed = new ArrayList<>();
        for (JsonNode entry : constraint.path("values")) {
            Point point = Point.of(entry);
            if (point != null) {
                allowed.add(point);
            }
        }
        List<Point[]> intervals = new ArrayList<>();
        for (JsonNode interval : constraint.path("intervals")) {
            if (interval.isArray() && interval.size() == 2) {
                Point low = Point.of(interval.get(0));
                Point high = Point.of(interval.get(1));
                if (low != null && high != null && low.compare(high) != null) {
                    intervals.add(new Point[] { low, high });
                }
            }
        }
        if (allowed.isEmpty() && intervals.isEmpty()) {
            return null;
        }

        Point point = Point.of(value);
        if (point == null) {
            return false;
        }
        for (Point candidate : allowed) {
            if (point.sameAs(candidate)) {
                return true;
            }
        }
        for (Point[] interval : intervals) {
            if (point.within(interval[0], interval[1])) {
                return true;
            }
        }
        return false;
    }

    /** Names one instance, the way ComponentInstanceReport does. */
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

    /**
     * A number or an ISO 8601 date-time, or one of the special values. "-Infinity" lies below and
     * "+Infinity" (also written "Infinity") above every number and date-time; "NaN" lies nowhere and only
     * equals itself.
     */
    private static final class Point {

        private static final Point NAN = new Point(0, true, null, null);

        private static final Point MINUS_INFINITY = new Point(-1, false, null, null);

        private static final Point PLUS_INFINITY = new Point(1, false, null, null);

        private final int infinity;

        private final boolean nan;

        private final BigDecimal number;

        private final Instant instant;

        private Point(int infinity, boolean nan, BigDecimal number, Instant instant) {
            this.infinity = infinity;
            this.nan = nan;
            this.number = number;
            this.instant = instant;
        }

        /** Reads a value or bound, or returns null if it is neither a number, a date-time nor a special value. */
        static Point of(JsonNode node) {
            if (node.isNumber()) {
                return new Point(0, false, node.decimalValue(), null);
            }
            if (!node.isTextual()) {
                return null;
            }
            switch (node.asText()) {
            case "NaN":
                return NAN;
            case "-Infinity":
                return MINUS_INFINITY;
            case "+Infinity":
            case "Infinity":
                return PLUS_INFINITY;
            default:
                try {
                    return new Point(0, false, null, OffsetDateTime.parse(node.asText()).toInstant());
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        }

        /** Orders two points, or returns null when they cannot be compared (NaN, or a number and a date-time). */
        Integer compare(Point other) {
            if (nan || other.nan) {
                return null;
            }
            if (infinity != 0 || other.infinity != 0) {
                return Integer.compare(infinity, other.infinity);
            }
            if (number != null && other.number != null) {
                return number.compareTo(other.number);
            }
            if (instant != null && other.instant != null) {
                return instant.compareTo(other.instant);
            }
            return null;
        }

        boolean sameAs(Point other) {
            if (nan || other.nan) {
                return nan && other.nan;
            }
            Integer order = compare(other);
            return order != null && order == 0;
        }

        boolean within(Point low, Point high) {
            Integer fromLow = compare(low);
            Integer toHigh = compare(high);
            return fromLow != null && toHigh != null && fromLow >= 0 && toHigh <= 0;
        }
    }

    private JsonNode readTestSubject(String abstractTest, String conformancePath) {
        try {
            JsonNode testSubject = objectMapper.readTree(testSubjectFile);
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
