package org.opengis.cite.swecommon30.validation;

import java.io.ByteArrayInputStream;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;

/**
 * Checks JSON encoded values against the data component that describes them, by the tests of
 * conformance class A.17 (/conf/json-encoding-rules), A.83 to A.89.
 *
 * <p>Used by A.64 for the inline values of block components, and meant for A.82 to A.89 on
 * separately supplied JSON data.</p>
 */
public class JsonEncodingRulesValidator {

    private static final String GML = "http://www.opengis.net/gml/3.2";

    private static final String XLINK = "http://www.w3.org/1999/xlink";

    /** The special numerical values Table 3 lists for Quantity and Time. */
    private static final Set<String> SPECIAL_VALUES = Set.of("NaN", "-Infinity", "+Infinity");

    private static final String SPECIAL_VALUES_TEXT = "\"NaN\", \"-Infinity\" or \"+Infinity\"";

    /** A.84: each range value is encoded like its scalar component. */
    private static final Map<String, String> RANGE_SCALARS = Map.of("CategoryRange", "Category",
            "CountRange", "Count", "QuantityRange", "Quantity", "TimeRange", "Time");

    /** A.89: the GeoJSON geometry types allowed. */
    private static final Set<String> GEOJSON_TYPES = Set.of("Point", "LineString", "Polygon", "MultiPoint",
            "MultiLineString", "MultiPolygon");

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

    /**
     * Checks one encoded value against the component that describes it, by the test of conformance
     * class A.17 for that component's type. A descriptor given by href is not resolved, and is
     * reported as unchecked; one of a type no A.17 test covers is not followed.
     *
     * @param component data component that describes the value
     * @param value encoded value to check
     * @param location where the value is, e.g. "values[1].temp", to start each violation with
     * @param violations receives each rule the value breaks, tagged with its abstract test
     * @param unchecked receives each check that could not be made, with the reason
     */
    public void validate(JsonNode component, JsonNode value, String location, List<String> violations,
            Map<String, String> unchecked) {
        if (component == null || !component.isObject()) {
            return;
        }
        if (component.has("href")) {
            unchecked.put("descriptor " + component.get("href").asText(),
                    "given by href, which is not resolved, so the values it describes were not checked");
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
            checkMembers(component, "fields", "A.85", value, location, violations, unchecked);
            break;
        case "Vector":
            checkMembers(component, "coordinates", "A.86", value, location, violations, unchecked);
            break;
        case "DataChoice":
            checkChoice(component, value, location, violations, unchecked);
            break;
        case "DataArray":
        case "Matrix":
        case "DataStream":
            checkArray(component, value, location, violations, unchecked);
            break;
        case "Geometry":
            checkGeometry(component, value, location, violations, unchecked);
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
            // ISO_DATE_TIME rejects a leap second (23:59:60); ISO_INSTANT reads it.
        }
        try {
            DateTimeFormatter.ISO_INSTANT.parse(value.asText());
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * A.85 (DataRecord fields) and A.86 (Vector coordinates): a JSON Object whose members are named
     * like the fields or coordinates, each valid by its descriptor. Only a record field marked
     * optional may be omitted or null (Requirement 85 B); A.86 allows neither.
     */
    private void checkMembers(JsonNode component, String membersKey, String abstractTest, JsonNode value,
            String location, List<String> violations, Map<String, String> unchecked) {
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
            boolean optional = "A.85".equals(abstractTest) && member.getValue().path("optional").asBoolean(false);
            if (optional && value.path(name).isNull()) {
                continue;
            } else if (value.has(name)) {
                validate(member.getValue(), value.get(name), location + "." + name, violations, unchecked);
            } else if (!optional) {
                violations.add(String.format("%s: %s: member '%s' is missing%s", location, abstractTest, name,
                        "A.85".equals(abstractTest) ? "; only members of optional fields may be omitted" : ""));
            }
        }
    }

    /** A.87: a JSON Object with a single member, named like a choice item and valid by it. */
    private void checkChoice(JsonNode component, JsonNode value, String location, List<String> violations,
            Map<String, String> unchecked) {
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
        validate(items.get(name), value.get(name), location + "." + name, violations, unchecked);
    }

    /**
     * A.88: a JSON Array whose elements are valid by the element type, and as long as a fixed size
     * says. A DataStream's inline values are wrapped the same way (clause 9.4.4), with no size.
     */
    private void checkArray(JsonNode component, JsonNode value, String location, List<String> violations,
            Map<String, String> unchecked) {
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
            validate(component.get("elementType"), value.get(i), location + "[" + i + "]", violations, unchecked);
        }
    }

    /**
     * A.89: a JSON Object that is a valid GeoJSON geometry of an allowed type, whose positions have
     * as many coordinates as the descriptor's srs has dimensions.
     */
    private void checkGeometry(JsonNode component, JsonNode value, String location, List<String> violations,
            Map<String, String> unchecked) {
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
            unchecked.put("srs " + srs, "A.89's dimension check could not be made: " + crsLookupFailures.get(srs));
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
            int axes = crs.getElementsByTagNameNS(GML, "axis").getLength();
            for (Node child = crs.getDocumentElement().getFirstChild(); axes == 0 && child != null;
                    child = child.getNextSibling()) {
                if (child instanceof Element && isCoordinateSystemProperty((Element) child)) {
                    String href = ((Element) child).getAttributeNS(XLINK, "href");
                    if (!href.isEmpty()) {
                        axes = fetchGml(uri.resolve(href)).getElementsByTagNameNS(GML, "axis").getLength();
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
        return GML.equals(element.getNamespaceURI())
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

    private String detailMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
