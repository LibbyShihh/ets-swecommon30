package org.opengis.cite.swecommon30.jsonschema;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.opengis.cite.swecommon30.Namespaces;
import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.util.JsonUtils;
import org.opengis.cite.swecommon30.validation.GeoJsonGeometryValidator;
import org.opengis.cite.swecommon30.validation.SweCommonJsonSchemaValidator;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;

/**
 * Conformance tests for conformance class A.12, Block Components JSON Schema
 * (/conf/json-block-components): abstract test A.64.
 *
 * <p>The TestNG workflow remains in the ETS module. The test subject is read through the validator
 * module, and GeoJSON values are validated by its {@link GeoJsonGeometryValidator}.</p>
 */
public class JsonEncodedValuesTest {

    /** The block components whose inline values clause 9.4.4 names, in A.63's order. */
    private static final List<String> BLOCK_TYPES = List.of("DataArray", "Matrix", "DataStream");

    /** The special numerical values Table 3 lists for Quantity and Time. */
    private static final Set<String> SPECIAL_VALUES = Set.of("NaN", "-Infinity", "+Infinity");

    private static final String SPECIAL_VALUES_TEXT = "\"NaN\", \"-Infinity\" or \"+Infinity\"";

    /** A.84: each range value is encoded like its scalar component. */
    private static final Map<String, String> RANGE_SCALARS = Map.of("CategoryRange", "Category",
            "CountRange", "Count", "QuantityRange", "Quantity", "TimeRange", "Time");

    /** A.89: the GeoJSON geometry types allowed. */
    private static final Set<String> GEOJSON_TYPES = Set.of("Point", "LineString", "Polygon", "MultiPoint",
            "MultiLineString", "MultiPolygon");

    private final SweCommonJsonSchemaValidator validator = new SweCommonJsonSchemaValidator();

    private final GeoJsonGeometryValidator geoJsonValidator = new GeoJsonGeometryValidator();

    // Not ClientUtils.buildClient(): HttpURLConnection never follows an http to https redirect, which
    // the OGC definition server answers with.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(ProxySelector.getDefault())
            .build();

    /** CRS dimension by srs, so each srs is looked up once however many values use it. */
    private final Map<String, Integer> crsDimensions = new HashMap<>();

    /** Why an srs's dimension could not be found, by srs. */
    private final Map<String, String> crsLookupFailures = new HashMap<>();

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
        Map<String, String> uncheckedCrs = new LinkedHashMap<>();
        int tested = 0;
        for (String type : BLOCK_TYPES) {
            List<JsonNode> blocks = JsonUtils.findNodesByType(testSubject, type).stream()
                    .filter(this::hasInlineValues)
                    .collect(Collectors.toList());
            tested += blocks.size();
            for (int i = 0; i < blocks.size(); i++) {
                JsonNode block = blocks.get(i);
                List<String> violations = new ArrayList<>();
                checkArray(block, block.get("values"), "values", violations, uncheckedCrs);
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

        if (!uncheckedCrs.isEmpty()) {
            throw new SkipException(String.format(
                    "Test skipped [%s %s]: The inline values follow the JSON encoding rules as far as they could be "
                            + "checked, but A.89's dimension check could not be made for these srs:%s%s",
                    abstractTest, conformancePath, System.lineSeparator(),
                    uncheckedCrs.entrySet().stream().map(e -> "  " + e.getKey() + ": " + e.getValue())
                            .collect(Collectors.joining(System.lineSeparator()))));
        }
    }

    /** Values given inline: present, and not an href to values provided elsewhere. */
    private boolean hasInlineValues(JsonNode block) {
        JsonNode values = block.get("values");
        return values != null && !(values.isObject() && values.has("href"));
    }

    /**
     * Checks one encoded value against the component that describes it, by the test of conformance
     * class A.17 for that component's type. A descriptor given by href, or of a type no A.17 test
     * covers, is not followed.
     */
    private void check(JsonNode component, JsonNode value, String location, List<String> violations,
            Map<String, String> uncheckedCrs) {
        if (component == null || !component.isObject()) {
            return;
        }
        String type = component.path("type").asText();
        switch (type) {
        case "Boolean":
        case "Text":
        case "Category":
        case "Count":
        case "Quantity":
        case "Time":
            checkScalar(type, value, location, "A.83", violations);
            break;
        case "CategoryRange":
        case "CountRange":
        case "QuantityRange":
        case "TimeRange":
            if (!value.isArray() || value.size() != 2) {
                violations.add(violation(location, "A.84", "a JSON Array with two values for " + type, value));
            } else {
                for (int i = 0; i < 2; i++) {
                    checkScalar(RANGE_SCALARS.get(type), value.get(i), location + "[" + i + "]", "A.84",
                            violations);
                }
            }
            break;
        case "DataRecord":
            checkMembers(component, "fields", "A.85", value, location, violations, uncheckedCrs);
            break;
        case "Vector":
            checkMembers(component, "coordinates", "A.86", value, location, violations, uncheckedCrs);
            break;
        case "DataChoice":
            checkChoice(component, value, location, violations, uncheckedCrs);
            break;
        case "DataArray":
        case "Matrix":
            checkArray(component, value, location, violations, uncheckedCrs);
            break;
        case "Geometry":
            checkGeometry(component, value, location, violations, uncheckedCrs);
            break;
        default:
            break;
        }
    }

    /** Table 3: the JSON value type of each scalar component. */
    private void checkScalar(String type, JsonNode value, String location, String abstractTest,
            List<String> violations) {
        boolean special = value.isTextual() && SPECIAL_VALUES.contains(value.asText());
        String expected;
        switch (type) {
        case "Boolean":
            expected = value.isBoolean() ? null : "a boolean literal";
            break;
        case "Text":
        case "Category":
            expected = value.isTextual() ? null : "a JSON String";
            break;
        case "Count":
            expected = value.isNumber() ? null : "a JSON Number";
            break;
        case "Quantity":
            expected = value.isNumber() || special ? null : "a JSON Number, or " + SPECIAL_VALUES_TEXT;
            break;
        default: // Time
            expected = value.isNumber() || special || isIsoDateTime(value) ? null
                    : "an ISO 8601 date/time string, a JSON Number, or " + SPECIAL_VALUES_TEXT;
            break;
        }
        if (expected != null) {
            violations.add(violation(location, abstractTest, expected + " for " + type, value));
        }
    }

    private boolean isIsoDateTime(JsonNode value) {
        if (!value.isTextual()) {
            return false;
        }
        try {
            DateTimeFormatter.ISO_DATE_TIME.parse(value.asText());
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * A.85 (DataRecord fields) and A.86 (Vector coordinates): a JSON Object whose members are named
     * like the fields or coordinates, each valid by its descriptor. Only a record field marked
     * optional may be omitted; A.86 allows no omission.
     */
    private void checkMembers(JsonNode component, String membersKey, String abstractTest, JsonNode value,
            String location, List<String> violations, Map<String, String> uncheckedCrs) {
        String type = component.path("type").asText();
        if (!value.isObject()) {
            violations.add(violation(location, abstractTest, "a JSON Object for " + type, value));
            return;
        }
        Map<String, JsonNode> members = byName(component.path(membersKey));
        Iterator<String> names = value.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!members.containsKey(name)) {
                violations.add(String.format("%s: %s: member '%s' is not one of the %s's %s %s", location,
                        abstractTest, name, type, membersKey, members.keySet()));
            }
        }
        for (Map.Entry<String, JsonNode> member : members.entrySet()) {
            String name = member.getKey();
            if (value.has(name)) {
                check(member.getValue(), value.get(name), location + "." + name, violations, uncheckedCrs);
            } else if ("A.86".equals(abstractTest) || !member.getValue().path("optional").asBoolean(false)) {
                violations.add(String.format("%s: %s: member '%s' is missing%s", location, abstractTest, name,
                        "A.85".equals(abstractTest) ? "; only members of optional fields may be omitted" : ""));
            }
        }
    }

    /** A.87: a JSON Object with a single member, named like a choice item and valid by it. */
    private void checkChoice(JsonNode component, JsonNode value, String location, List<String> violations,
            Map<String, String> uncheckedCrs) {
        if (!value.isObject() || value.size() != 1) {
            violations.add(violation(location, "A.87", "a JSON Object with a single member for DataChoice", value));
            return;
        }
        Map<String, JsonNode> items = byName(component.path("items"));
        String name = value.fieldNames().next();
        if (!items.containsKey(name)) {
            violations.add(String.format("%s: A.87: member '%s' is not one of the DataChoice's items %s", location,
                    name, items.keySet()));
            return;
        }
        check(items.get(name), value.get(name), location + "." + name, violations, uncheckedCrs);
    }

    /**
     * A.88: a JSON Array whose elements are valid by the element type, and as long as a fixed size
     * says. A DataStream's inline values are wrapped the same way (clause 9.4.4), with no size.
     */
    private void checkArray(JsonNode component, JsonNode value, String location, List<String> violations,
            Map<String, String> uncheckedCrs) {
        String type = component.path("type").asText();
        String abstractTest = "DataStream".equals(type) ? "clause 9.4.4" : "A.88";
        if (!value.isArray()) {
            violations.add(violation(location, abstractTest, "a JSON Array for " + type, value));
            return;
        }
        JsonNode size = component.path("elementCount").path("value");
        if (size.isNumber() && value.size() != size.asInt()) {
            violations.add(String.format("%s: A.88: the %s has a fixed size of %d, but the JSON Array has %d elements",
                    location, type, size.asInt(), value.size()));
        }
        for (int i = 0; i < value.size(); i++) {
            check(component.get("elementType"), value.get(i), location + "[" + i + "]", violations, uncheckedCrs);
        }
    }

    /**
     * A.89: a JSON Object that is a valid GeoJSON geometry of an allowed type, whose positions have
     * as many coordinates as the descriptor's srs has dimensions.
     */
    private void checkGeometry(JsonNode component, JsonNode value, String location, List<String> violations,
            Map<String, String> uncheckedCrs) {
        if (!value.isObject()) {
            violations.add(violation(location, "A.89", "a JSON Object for Geometry", value));
            return;
        }
        JsonNode geomType = value.path("type");
        if (!GEOJSON_TYPES.contains(geomType.asText())) {
            violations.add(String.format("%s: A.89: %s is not Point, LineString, Polygon, MultiPoint, "
                    + "MultiLineString or MultiPolygon", location,
                    geomType.isMissingNode() ? "the GeoJSON type is missing, so it" : "GeoJSON type " + geomType));
            return;
        }
        Set<ValidationMessage> errors = geoJsonValidator.validate(value);
        if (!errors.isEmpty()) {
            errors.stream().map(ValidationMessage::getMessage).distinct().sorted()
                    .forEach(message -> violations.add(String.format(
                            "%s: A.89: not a valid GeoJSON geometry: %s", location, message)));
            return;
        }
        String srs = component.path("srs").asText();
        Integer dimension = crsDimension(srs);
        if (dimension == null) {
            uncheckedCrs.put(srs, crsLookupFailures.get(srs));
            return;
        }
        Set<Integer> lengths = new TreeSet<>();
        positionLengths(value.get("coordinates"), lengths);
        for (int length : lengths) {
            if (length != dimension) {
                violations.add(String.format("%s: A.89: positions have %d coordinates, but the CRS %s has %d dimensions",
                        location, length, srs, dimension));
            }
        }
    }

    /** Collects the length of every GeoJSON position, an array of numbers, under a coordinates value. */
    private void positionLengths(JsonNode coordinates, Set<Integer> lengths) {
        if (coordinates == null || !coordinates.isArray()) {
            return;
        }
        if (coordinates.size() > 0 && coordinates.get(0).isNumber()) {
            lengths.add(coordinates.size());
            return;
        }
        for (JsonNode child : coordinates) {
            positionLengths(child, lengths);
        }
    }

    /**
     * The number of axes of the CRS an srs identifies, read from its GML definition: its gml:axis
     * elements, or those of the coordinate system it references by xlink:href. Null if it cannot be
     * found, with the reason in {@link #crsLookupFailures}.
     */
    private Integer crsDimension(String srs) {
        if (crsDimensions.containsKey(srs) || crsLookupFailures.containsKey(srs)) {
            return crsDimensions.get(srs);
        }
        try {
            URI uri = new URI(srs);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IOException("not an http or https URI");
            }
            Document crs = fetchGml(uri);
            int axes = crs.getElementsByTagNameNS(Namespaces.GML, "axis").getLength();
            for (Node child = crs.getDocumentElement().getFirstChild(); axes == 0 && child != null;
                    child = child.getNextSibling()) {
                if (child instanceof Element && isCoordinateSystemProperty((Element) child)) {
                    String href = ((Element) child).getAttributeNS(Namespaces.XLINK, "href");
                    if (!href.isEmpty()) {
                        axes = fetchGml(uri.resolve(href)).getElementsByTagNameNS(Namespaces.GML, "axis").getLength();
                    }
                }
            }
            if (axes == 0) {
                throw new IOException("its GML definition lists no gml:axis");
            }
            crsDimensions.put(srs, axes);
            return axes;
        } catch (URISyntaxException | IllegalArgumentException e) {
            crsLookupFailures.put(srs, "not a URI");
        } catch (IOException e) {
            crsLookupFailures.put(srs, detailMessage(e));
        }
        return null;
    }

    /** gml:ellipsoidalCS, gml:cartesianCS and the like, or gml:coordinateSystem. */
    private boolean isCoordinateSystemProperty(Element element) {
        String name = element.getLocalName();
        return Namespaces.GML.equals(element.getNamespaceURI())
                && name != null && (name.endsWith("CS") || name.equals("coordinateSystem"));
    }

    private Document fetchGml(URI uri) throws IOException {
        HttpResponse<byte[]> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/gml+xml, application/xml;q=0.9, text/xml;q=0.8")
                    .GET()
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test execution error: Interrupted while requesting " + uri, e);
        } catch (IOException e) {
            throw new IOException("no HTTP response from " + uri + ": " + detailMessage(e), e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + " from " + uri);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // The CRS definition is remote input; no DTD, so no external entities.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(response.body()));
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException("the response from " + uri + " is not XML: " + detailMessage(e), e);
        }
    }

    private Map<String, JsonNode> byName(JsonNode members) {
        Map<String, JsonNode> byName = new LinkedHashMap<>();
        for (JsonNode member : members) {
            byName.put(member.path("name").asText(), member);
        }
        return byName;
    }

    private String violation(String location, String abstractTest, String expected, JsonNode found) {
        return String.format("%s: %s expects %s; found %s", location, abstractTest, expected, found(found));
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
