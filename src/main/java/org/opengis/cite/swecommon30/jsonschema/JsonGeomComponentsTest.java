package org.opengis.cite.swecommon30.jsonschema;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opengis.cite.swecommon30.SuiteAttribute;
import org.opengis.cite.swecommon30.util.JsonSchemaUtils;
import org.opengis.cite.swecommon30.util.JsonUtils;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Conformance tests for conformance class A.13, Geometry Components JSON Schema
 * (/conf/json-geom-components).
 */
public class JsonGeomComponentsTest {

    private static final String GEOMETRY = "Geometry";

    private static final Set<String> TESTED_VALUE_TYPES = Set.of("Point", "LineString", "Polygon");

    private static final Set<String> UNTESTED_VALUE_TYPES = Set.of("MultiPoint", "MultiLineString", "MultiPolygon");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private File testSubjectFile;

    /**
     * Obtains the downloaded IUT document file reference from the suite context.
     *
     * @param testContext TestNG test context
     */
    @BeforeClass
    public void obtainTestSubject(ITestContext testContext) {
        Object subject = testContext.getSuite().getAttribute(SuiteAttribute.TEST_SUBJ_FILE.getName());
        Assert.assertTrue(subject instanceof File, "The test subject is not available as a file.");
        this.testSubjectFile = (File) subject;
    }

    /**
     * Implements Abstract Test A.66 (/conf/json-geom-components/component-types).
     */
    @Test(description = "Implements Abstract Test A.66 (/conf/json-geom-components/component-types)")
    public void geomComponentTypes() {
        JsonNode testSubject;
        try {
            testSubject = objectMapper.readTree(testSubjectFile);
        } catch (JsonProcessingException e) {
            Assert.fail("The test subject is not well-formed JSON: " + e.getMessage());
            return;
        } catch (IOException e) {
            throw new SkipException("The test subject could not be read: " + e.getMessage());
        }

        // A.66 tests "documents containing instances of the Geometry data component with the
        // following value types: Point, LineString, Polygon", so the whole document is searched and
        // only those Geometries are tested.
        List<JsonNode> geoms = JsonUtils.findNodesByType(testSubject, GEOMETRY).stream()
                .filter(this::hasTestedValueType)
                .collect(Collectors.toList());
        if (geoms.isEmpty()) {
            throw new SkipException("The test subject contains no Geometry component with a Point,"
                    + " LineString or Polygon value type.");
        }

        String report = JsonSchemaUtils.reportInstances(GEOMETRY, geoms,
                JsonSchemaUtils.loadSchema("sweCommon/3.0/json/Geometry.json"));
        Assert.assertTrue(report.isEmpty(),
                "The test subject's Geometry components do not conform to Geometry.json:"
                        + System.lineSeparator() + report);
    }

    /**
     * Whether a Geometry falls under A.66's value types. With a value, its GeoJSON type decides.
     * "value" is optional; without one, the Geometry is a descriptor whose constraint lists the
     * geometry types a value may take, and it is left out when that list names a Multi* type.
     */
    private boolean hasTestedValueType(JsonNode geom) {
        if (geom.has("value")) {
            return TESTED_VALUE_TYPES.contains(geom.path("value").path("type").asText());
        }
        for (JsonNode geomType : geom.path("constraint").path("geomTypes")) {
            if (UNTESTED_VALUE_TYPES.contains(geomType.asText())) {
                return false;
            }
        }
        return true;
    }
}
