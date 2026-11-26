package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.util.JsonUtils;
import org.opengis.cite.swecommon30.validation.ComponentInstanceReport;
import org.opengis.cite.swecommon30.validation.OfflineSweCommonJsonSchemaValidator;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Conformance tests for conformance class A.12, Block Components JSON Schema
 * (/conf/json-block-components): abstract test A.65.
 *
 * <p>The TestNG workflow remains in the ETS module. Validation of each DataArray, Matrix and
 * DataStream instance with referenced values is delegated to the validator module's offline
 * validator; no referenced value is requested over the network.</p>
 */
public class JsonReferencedEncodedValuesTest {

    private static final String DATA_ARRAY = "DataArray";

    private static final String MATRIX = "Matrix";

    private static final String DATA_STREAM = "DataStream";

    /** The test method's URL schemes for values/href. */
    private static final List<String> HREF_SCHEMES = List.of("http", "https", "data");

    private final OfflineSweCommonJsonSchemaValidator validator = new OfflineSweCommonJsonSchemaValidator();

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
     * Implements Abstract Test A.65 (/conf/json-block-components/referenced-encoded-values-valid).
     */
    @Test(description = "Implements Abstract Test A.65 (/conf/json-block-components/referenced-encoded-values-valid)")
    public void referencedEncodedValuesValid() {
        String abstractTest = "A.65";
        String conformancePath = "/conf/json-block-components/referenced-encoded-values-valid";

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

        // A.65 runs this conformance class's tests on "instances of the data component types DataArray,
        // Matrix and DataStream having the value/href attribute set to URLs with the following schemes:
        // http, https and data", so only those instances are tested. In JSON that attribute is values/href.
        List<JsonNode> arrays = withReferencedValues(JsonUtils.findNodesByType(testSubject, DATA_ARRAY));
        List<JsonNode> matrices = withReferencedValues(JsonUtils.findNodesByType(testSubject, MATRIX));
        List<JsonNode> streams = withReferencedValues(JsonUtils.findNodesByType(testSubject, DATA_STREAM));
        if (arrays.isEmpty() && matrices.isEmpty() && streams.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject contains no DataArray, Matrix "
                            + "or DataStream whose values/href is an http, https or data URL.",
                    abstractTest, conformancePath));
        }

        // Clauses 9.4.1, 9.4.2 and 9.4.3 give each type its own schema.
        String report;
        try {
            report = Stream.of(
                    ComponentInstanceReport.report(validator, DATA_ARRAY, arrays, "DataArray.json"),
                    ComponentInstanceReport.report(validator, MATRIX, matrices, "Matrix.json"),
                    ComponentInstanceReport.report(validator, DATA_STREAM, streams, "DataStream.json"))
                    .filter(part -> !part.isEmpty())
                    .collect(Collectors.joining(System.lineSeparator()));
        } catch (RuntimeException e) {
            throw new IllegalStateException(executionError(abstractTest, conformancePath,
                    "Unable to load or execute schema 'DataArray.json', 'Matrix.json' or 'DataStream.json': "
                            + detailMessage(e)), e);
        }

        Assert.assertTrue(report.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The test subject's DataArray, Matrix and DataStream components with referenced values do not "
                        + "conform to DataArray.json, Matrix.json and DataStream.json:" + System.lineSeparator()
                        + report));
    }

    /** Keeps the instances whose values/href is a URI in one of the test method's schemes. */
    private List<JsonNode> withReferencedValues(List<JsonNode> instances) {
        return instances.stream()
                .filter(instance -> HREF_SCHEMES.contains(scheme(instance.path("values").path("href").asText())))
                .collect(Collectors.toList());
    }

    private String scheme(String href) {
        try {
            String scheme = new URI(href).getScheme();
            return scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return "";
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
