# SWE Common Validation Module

The SWE Common project provides a reusable validation module:
`swecommon30-validator`.

ETS projects such as Connected Systems ETS or SensorML ETS can depend on this
module when they need SWE Common validation logic. The validator module contains
reusable validation code, such as JSON Schema validation and Java-based
validation helpers. It does not contain TestNG `@Test` methods or the TEAM
Engine execution workflow.

## Build The Validator

From the SWE Common repository root:

```bash
mvn clean install -DskipTests
```

This produces the validator jar:

```text
swecommon30-validator/target/swecommon30-validator-0.1-SNAPSHOT.jar
```

It also produces the SWE Common ETS artifacts:

```text
swecommon30-ets/target/ets-swecommon30-0.1-SNAPSHOT.jar
swecommon30-ets/target/ets-swecommon30-0.1-SNAPSHOT-deps.zip
```

Confirm that the SWE Common ETS dependency archive includes the validator jar.

Linux/macOS:

```bash
jar tf swecommon30-ets/target/ets-swecommon30-0.1-SNAPSHOT-deps.zip | grep swecommon30-validator
```

Windows:

```bat
jar tf swecommon30-ets\target\ets-swecommon30-0.1-SNAPSHOT-deps.zip | findstr swecommon30-validator
```

If the command outputs `swecommon30-validator-0.1-SNAPSHOT.jar`, the validator
jar has been included in the SWE Common ETS runtime dependencies.

## Add The Dependency

External ETS projects should specify the validator artifact version they intend
to use. The validator version is managed by the SWE Common parent POM. Use the
`<version>` value from the root `pom.xml`:

```text
pom.xml
```

```xml
<artifactId>swecommon30-parent</artifactId>
<version>...</version>
```

External ETS projects can use that version when declaring the validator
dependency:

```xml
<dependency>
  <groupId>org.opengis.cite</groupId>
  <artifactId>swecommon30-validator</artifactId>
  <version><!-- SWE Common parent POM version --></version>
</dependency>
```

Inside the SWE Common multi-module build, `${project.version}` can be used
because `swecommon30-validator` and `swecommon30-ets` share the same parent
version:

```xml
<dependency>
  <groupId>org.opengis.cite</groupId>
  <artifactId>swecommon30-validator</artifactId>
  <version>${project.version}</version>
</dependency>
```

The consuming ETS keeps its own TestNG and TEAM Engine integration:

- TestNG `@Test` methods
- suite context handling
- pass/fail/skip decisions
- TEAM Engine reports

Reusable SWE Common validation logic is delegated to the validator module:

```java
private final SweCommonJsonSchemaValidator validator = new SweCommonJsonSchemaValidator();

Set<ValidationMessage> errors = validator.validate(testSubject, "Boolean.json");
Assert.assertTrue(errors.isEmpty(), validator.formatValidationErrors("Boolean", errors));
```

## Updating The Validator Dependency

When the validator module changes, ETS projects that depend on it must rebuild
against the updated validator jar.

During development, external ETS developers can clone the SWE Common project,
check out the branch that contains the validator changes, and install the latest
validator locally:

```bash
git clone https://github.com/opengeospatial/ets-swecommon30.git
cd ets-swecommon30
git checkout issue-9-swecommon-validation-module
mvn clean install
```

This installs `swecommon30-validator` into the local Maven cache.

Then go back to the consuming ETS project, such as Connected Systems ETS or
SensorML ETS, and rebuild it against that local validator artifact:

```bash
cd <consuming-ets-project>
mvn clean install
```

The longer-term sharing and versioning workflow can be decided separately.

## ETS Usage Example

The `swecommon30-ets` module includes an example implementation for SWE Common
Core Concepts A.2-A.6:

- A.2 `/conf/core/boolean-rep-valid`
- A.3 `/conf/core/categorical-rep-valid`
- A.4 `/conf/core/numerical-rep-valid`
- A.5 `/conf/core/countable-rep-valid`
- A.6 `/conf/core/textual-rep-valid`

The test methods stay in:

```text
swecommon30-ets/src/main/java/org/opengis/cite/swecommon30/jsonschema/CoreScalarComponentsTest.java
```

The reusable schema validation logic is in:

```text
swecommon30-validator/src/main/java/org/opengis/cite/swecommon30/validation/SweCommonJsonSchemaValidator.java
```

SWE Common JSON schemas are bundled in the validator jar under:

```text
org/opengis/cite/swecommon30/jsonschema/sweCommon/3.0/json/
```

## Run The SWE Common ETS With TEAM Engine

From the SWE Common ETS module:

```bash
cd swecommon30-ets
mvn clean package -Pdocker -DskipTests
mvn docker:build -Pdocker
mvn docker:run -Pdocker
```

Open:

```text
http://localhost:8081/teamengine
```
