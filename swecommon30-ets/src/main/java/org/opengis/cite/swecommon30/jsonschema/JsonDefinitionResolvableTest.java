package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
 * (/conf/json-simple-components): abstract test A.56.
 *
 * <p>The TestNG workflow remains in the ETS module. The test subject is read through the validator
 * module; no schema is run. Each HTTP definition is requested over the network.</p>
 */
public class JsonDefinitionResolvableTest {

    /** Every type whose schema inherits AbstractDataComponent.json, where "definition" is defined. */
    private static final List<String> DATA_COMPONENT_TYPES = List.of(
            "Boolean", "Text", "Category", "Count", "Quantity", "Time",
            "CategoryRange", "CountRange", "QuantityRange", "TimeRange",
            "DataRecord", "Vector", "DataChoice", "DataArray", "Matrix", "Geometry");

    private final SweCommonJsonSchemaValidator validator = new SweCommonJsonSchemaValidator();

    // Not ClientUtils.buildClient(): HttpURLConnection never follows an http to https redirect, which
    // is how qudt.org and mmisw.org answer for definitions that do resolve.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(ProxySelector.getDefault())
            .build();

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
     * Implements Abstract Test A.56 (/conf/json-simple-components/definition-resolvable).
     */
    @Test(description = "Implements Abstract Test A.56 (/conf/json-simple-components/definition-resolvable)")
    public void definitionResolvable() {
        String abstractTest = "A.56";
        String conformancePath = "/conf/json-simple-components/definition-resolvable";

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

        // Each distinct definition is checked once, and reported with every component that uses it.
        Map<JsonNode, List<String>> usersByDefinition = new LinkedHashMap<>();
        for (String type : DATA_COMPONENT_TYPES) {
            for (JsonNode component : JsonUtils.findNodesByType(testSubject, type)) {
                if (component.has("definition")) {
                    usersByDefinition.computeIfAbsent(component.get("definition"), key -> new ArrayList<>())
                            .add(describe(type, component));
                }
            }
        }
        if (usersByDefinition.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject contains no data component with a "
                            + "'definition'.",
                    abstractTest, conformancePath));
        }

        // The test method allows "an HTTP URL that resolves to a document (response code 200)" or "a URN
        // that can be resolved ... using a URN resolver provided separately". No URN resolver is
        // provided to this suite, so a URN can be neither confirmed nor refuted and is left out.
        List<String> urns = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<JsonNode, List<String>> entry : usersByDefinition.entrySet()) {
            JsonNode definition = entry.getKey();
            URI uri = parseUri(definition);
            String scheme = uri == null || uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String problem;
            if (scheme.equals("urn")) {
                urns.add(definition.asText());
                continue;
            } else if (scheme.equals("http") || scheme.equals("https")) {
                problem = resolve(uri, abstractTest, conformancePath);
            } else {
                problem = "neither an HTTP URL nor a URN";
            }
            if (problem != null) {
                String shown = definition.isTextual() ? definition.asText() : definition.toString();
                failures.add(shown + ": " + problem + " (used by " + String.join(", ", entry.getValue()) + ")");
            }
        }
        if (urns.size() == usersByDefinition.size()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; every definition in the test subject is a URN, and no "
                            + "URN resolver is provided: %s",
                    abstractTest, conformancePath, String.join(", ", urns)));
        }

        Assert.assertTrue(failures.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The test subject has definitions that are neither an HTTP URL resolving with response code 200 "
                        + "nor a URN:" + System.lineSeparator() + String.join(System.lineSeparator(), failures)));
    }

    private URI parseUri(JsonNode definition) {
        if (!definition.isTextual()) {
            return null;
        }
        try {
            return new URI(definition.asText());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Requests an HTTP definition, following redirects. Returns null when the final response is 200,
     * otherwise what went wrong. The content is not judged: the test method's criterion is the response
     * code, and any format is allowed ("RDF, GML, HTML, etc.").
     */
    private String resolve(URI uri, String abstractTest, String conformancePath) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "*/*")
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
                return null;
            }
            String redirected = response.uri().equals(uri) ? "" : " after redirect to " + response.uri();
            return "HTTP " + response.statusCode() + redirected;
        } catch (IOException | IllegalArgumentException e) {
            return "no HTTP response: " + e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(executionError(abstractTest, conformancePath,
                    "Interrupted while requesting " + uri), e);
        }
    }

    /** Names one component, so a failure says which one in the document it is about. */
    private String describe(String typeName, JsonNode component) {
        for (String key : new String[] { "name", "label", "id" }) {
            if (component.hasNonNull(key) && component.get(key).isTextual()) {
                return typeName + " (" + key + ": '" + component.get(key).asText() + "')";
            }
        }
        return typeName;
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
