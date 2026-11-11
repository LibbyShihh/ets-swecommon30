package org.opengis.cite.swecommon30.jsonschema;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

import org.opengis.cite.swecommon30.TestRunArg;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the descriptor of the encoded values under test, given by the
 * {@link TestRunArg#DESCRIPTOR descriptor} test run argument, for the tests of conformance class
 * A.17 (/conf/json-encoding-rules), A.83 to A.89.
 */
public final class EncodedValuesDescriptor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Follows http to https redirects, which ClientUtils.buildClient() does not.
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(ProxySelector.getDefault())
            .build();

    private EncodedValuesDescriptor() {
    }

    /**
     * Retrieves and parses the descriptor. Skips the test when no descriptor is given or it cannot
     * be used, and fails it when there is no response from the descriptor URL.
     *
     * @param testContext TestNG test context
     * @param abstractTest abstract test, e.g. "A.83"
     * @param conformancePath conformance path of the abstract test
     * @return the descriptor, a JSON object with a type
     */
    public static JsonNode read(ITestContext testContext, String abstractTest, String conformancePath) {
        String descriptor = testContext.getSuite().getParameter(TestRunArg.DESCRIPTOR.toString());
        if (descriptor == null || descriptor.isBlank()) {
            throw new SkipException(String.format("Test skipped [%s %s]: No descriptor was provided, so the encoded "
                    + "values cannot be checked against the encoding rules.", abstractTest, conformancePath));
        }
        descriptor = descriptor.trim();

        URI uri;
        try {
            uri = new URI(descriptor);
        } catch (URISyntaxException e) {
            throw incomplete(abstractTest, conformancePath, descriptor, "is not a URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);

        byte[] body = null;
        if (scheme.equals("file")) {
            try {
                body = Files.readAllBytes(Path.of(uri));
            } catch (IOException | IllegalArgumentException e) {
                Assert.fail(unableToRetrieve(abstractTest, conformancePath, uri, e));
            }
        } else if (scheme.equals("http") || scheme.equals("https")) {
            HttpResponse<byte[]> response = null;
            try {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException | IllegalArgumentException e) {
                Assert.fail(unableToRetrieve(abstractTest, conformancePath, uri, e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(String.format("Test execution error [%s %s]: Interrupted while "
                        + "requesting %s", abstractTest, conformancePath, uri), e);
            }
            if (response.statusCode() != 200) {
                String redirected = response.uri().equals(uri) ? "" : " after redirect to " + response.uri();
                throw incomplete(abstractTest, conformancePath, descriptor,
                        "answered HTTP " + response.statusCode() + redirected);
            }
            body = response.body();
        } else {
            throw incomplete(abstractTest, conformancePath, descriptor, "is not an http, https or file URI");
        }

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            throw incomplete(abstractTest, conformancePath, descriptor, "is not JSON: " + detailMessage(e));
        } catch (IOException e) {
            throw incomplete(abstractTest, conformancePath, descriptor, "could not be read: " + detailMessage(e));
        }
        JsonNode type = root == null ? null : root.get("type");
        if (type == null || !type.isTextual() || type.asText().isBlank()) {
            throw incomplete(abstractTest, conformancePath, descriptor,
                    "is not a JSON object with a non-empty string 'type'");
        }
        return root;
    }

    private static SkipException incomplete(String abstractTest, String conformancePath, String descriptor,
            String reason) {
        return new SkipException(String.format("Test skipped [%s %s]: %s verification is incomplete because the "
                + "descriptor at %s %s.", abstractTest, conformancePath, abstractTest, descriptor, reason));
    }

    private static String unableToRetrieve(String abstractTest, String conformancePath, URI uri, Exception e) {
        return String.format("Data validation failure [%s %s]: Unable to retrieve definition from %s: %s",
                abstractTest, conformancePath, uri, detailMessage(e));
    }

    private static String detailMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
