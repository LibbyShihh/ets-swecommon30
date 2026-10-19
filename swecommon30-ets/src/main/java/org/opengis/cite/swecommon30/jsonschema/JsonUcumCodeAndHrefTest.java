package org.opengis.cite.swecommon30.jsonschema;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.shared.JenaException;
import org.fhir.ucum.Decimal;
import org.fhir.ucum.Pair;
import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumException;
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
 * (/conf/json-simple-components): abstract test A.58.
 *
 * <p>The TestNG workflow remains in the ETS module. The test subject is read through the validator
 * module; no schema is run. Each uom/href is requested over the network, except one in the OGC UCUM
 * namespace, whose path is the code itself.</p>
 */
public class JsonUcumCodeAndHrefTest {

    /** The types whose schema defines "uom" as basicTypes.json UnitReference. */
    private static final List<String> UOM_TYPES = List.of("Quantity", "QuantityRange", "Time", "TimeRange");

    /** QUDT's statement of a unit's UCUM code, as on https://qudt.org/vocab/unit/DEG_C. */
    private static final String QUDT_UCUM_CODE = "http://qudt.org/schema/qudt/ucumCode";

    private static final String OGC_UCUM_PATH = "/def/uom/UCUM/0/";

    private final SweCommonJsonSchemaValidator validator = new SweCommonJsonSchemaValidator();

    // Not ClientUtils.buildClient(): HttpURLConnection never follows an http to https redirect, which is
    // how http://qudt.org answers for its unit definitions.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(ProxySelector.getDefault())
            .build();

    private UcumEssenceService ucum;

    private File testSubjectFile;

    /**
     * Obtains the downloaded IUT document file from the suite context, and loads the UCUM essence.
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

        try (InputStream essence = UcumEssenceService.class.getResourceAsStream("/ucum-essence.xml")) {
            this.ucum = new UcumEssenceService(essence);
        } catch (UcumException | IOException e) {
            throw new IllegalStateException("Test execution error: Unable to load the UCUM essence.", e);
        }
    }

    /**
     * Implements Abstract Test A.58 (/conf/json-simple-components/ucum-code-and-href-represent-same-unit).
     */
    @Test(description = "Implements Abstract Test A.58 "
            + "(/conf/json-simple-components/ucum-code-and-href-represent-same-unit)")
    public void ucumCodeAndHrefRepresentSameUnit() {
        String abstractTest = "A.58";
        String conformancePath = "/conf/json-simple-components/ucum-code-and-href-represent-same-unit";

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

        // Each distinct href is looked up once, and its stated codes or its problem reused.
        Map<String, List<String>> codesByHref = new HashMap<>();
        Map<String, String> problemByHref = new HashMap<>();
        int pairs = 0;
        int compared = 0;
        List<String> uncompared = new ArrayList<>();
        List<String> mismatches = new ArrayList<>();
        for (String type : UOM_TYPES) {
            for (JsonNode component : JsonUtils.findNodesByType(testSubject, type)) {
                JsonNode uom = component.path("uom");
                if (!uom.path("code").isTextual() || !uom.path("href").isTextual()) {
                    continue;
                }
                pairs++;
                String code = uom.get("code").asText();
                String href = uom.get("href").asText();
                String shown = describe(type, component) + " (uom/code '" + code + "', uom/href " + href + ")";

                // The test method's "if the uom/code property is set to a UCUM code".
                String notUcum = ucum.validate(code);
                if (notUcum != null) {
                    uncompared.add(shown + ": the code is not a UCUM code (" + notUcum + ")");
                    continue;
                }

                if (!codesByHref.containsKey(href) && !problemByHref.containsKey(href)) {
                    List<String> codes = new ArrayList<>();
                    String problem = statedCodes(href, codes, abstractTest, conformancePath);
                    if (problem != null) {
                        problemByHref.put(href, problem);
                    } else {
                        codesByHref.put(href, codes);
                    }
                }
                if (problemByHref.containsKey(href)) {
                    uncompared.add(shown + ": " + problemByHref.get(href));
                    continue;
                }

                List<String> stated = codesByHref.get(href);
                List<String> reasons = new ArrayList<>();
                Boolean same = sameUnit(code, stated, reasons);
                if (same == null) {
                    uncompared.add(shown + ": " + String.join("; ", reasons));
                    continue;
                }
                compared++;
                if (!same) {
                    mismatches.add(shown + ": the definition states the UCUM code "
                            + String.join(" or ", quoted(stated)) + ", a different unit");
                }
            }
        }

        if (pairs == 0) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; the test subject contains no Quantity, QuantityRange, "
                            + "Time or TimeRange whose uom has both a code and an href.",
                    abstractTest, conformancePath));
        }
        // A pair whose two units can be neither confirmed nor refuted to be the same is left out. Only a real
        // comparison counts toward a PASS.
        if (compared == 0) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: Not applicable; no uom code and href in the test subject could be "
                            + "compared:%s%s",
                    abstractTest, conformancePath, System.lineSeparator(),
                    String.join(System.lineSeparator(), uncompared)));
        }

        Assert.assertTrue(mismatches.isEmpty(), dataValidationError(abstractTest, conformancePath,
                "The test subject has uom codes and hrefs that represent different units:"
                        + System.lineSeparator() + String.join(System.lineSeparator(), mismatches)));
    }

    /**
     * Finds the UCUM codes the unit definition at href states, into codes. Returns null when it states at
     * least one, otherwise why none could be found. An href in the OGC UCUM namespace names its code in its
     * path, and is not requested.
     */
    private String statedCodes(String href, List<String> codes, String abstractTest, String conformancePath) {
        URI uri;
        try {
            uri = new URI(href);
        } catch (URISyntaxException e) {
            return "the href is not a URI";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return "the href is not an HTTP URL, so its definition cannot be requested";
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if ("www.opengis.net".equalsIgnoreCase(uri.getHost()) && path.startsWith(OGC_UCUM_PATH)
                && path.length() > OGC_UCUM_PATH.length()) {
            codes.add(path.substring(OGC_UCUM_PATH.length()));
            return null;
        }

        HttpResponse<byte[]> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "text/turtle, application/rdf+xml;q=0.9, application/n-triples;q=0.8")
                    .GET()
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException | IllegalArgumentException e) {
            return "no HTTP response: " + e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(executionError(abstractTest, conformancePath,
                    "Interrupted while requesting " + uri), e);
        }
        if (response.statusCode() != 200) {
            String redirected = response.uri().equals(uri) ? "" : " after redirect to " + response.uri();
            return "HTTP " + response.statusCode() + redirected;
        }

        String mediaType = response.headers().firstValue("Content-Type").orElse("")
                .split(";")[0].trim().toLowerCase(Locale.ROOT);
        String lang;
        if (mediaType.equals("text/turtle")) {
            lang = "TURTLE";
        } else if (mediaType.equals("application/rdf+xml")) {
            lang = "RDF/XML";
        } else if (mediaType.equals("application/n-triples")) {
            lang = "N-TRIPLE";
        } else {
            return "the definition was served as '" + mediaType + "', which is not Turtle, RDF/XML or N-Triples";
        }
        Model model = ModelFactory.createDefaultModel();
        try {
            model.read(new ByteArrayInputStream(response.body()), response.uri().toString(), lang);
        } catch (JenaException e) {
            return "the definition could not be read as " + lang + ": " + detailMessage(e);
        }

        // Only what the definition says about the href's own resource. QUDT serves https but names its units
        // with http, so the scheme is not compared.
        Set<String> resource = new HashSet<>(
                List.of(withoutScheme(href), withoutScheme(response.uri().toString())));
        StmtIterator statements = model.listStatements(null, model.createProperty(QUDT_UCUM_CODE), (RDFNode) null);
        while (statements.hasNext()) {
            Statement statement = statements.next();
            if (statement.getSubject().isURIResource() && statement.getObject().isLiteral()
                    && resource.contains(withoutScheme(statement.getSubject().getURI()))) {
                codes.add(statement.getObject().asLiteral().getLexicalForm());
            }
        }
        return codes.isEmpty() ? "the definition states no UCUM code (qudt:ucumCode) for " + href : null;
    }

    private String withoutScheme(String uri) {
        return uri.replaceFirst("(?i)^https?://", "");
    }

    /**
     * Whether code names the same unit as one of the stated codes: identical, or equal once both are reduced
     * to UCUM canonical form, so m/s is m.s-1. Returns null when no stated code matched and at least one
     * could not be reduced, with why in reasons.
     */
    private Boolean sameUnit(String code, List<String> stated, List<String> reasons) {
        boolean undecided = false;
        for (String other : stated) {
            if (other.equals(code)) {
                return true;
            }
            try {
                Pair mine = ucum.getCanonicalForm(new Pair(new Decimal(1), code));
                Pair theirs = ucum.getCanonicalForm(new Pair(new Decimal(1), other));
                if (mine.getCode().equals(theirs.getCode()) && mine.getValue().comparesTo(theirs.getValue()) == 0) {
                    return true;
                }
            } catch (UcumException e) {
                undecided = true;
                reasons.add("the UCUM library cannot compare '" + code + "' with the stated '" + other + "' ("
                        + detailMessage(e) + ")");
            }
        }
        return undecided ? null : false;
    }

    private List<String> quoted(List<String> codes) {
        List<String> quoted = new ArrayList<>();
        for (String code : codes) {
            quoted.add("'" + code + "'");
        }
        return quoted;
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
