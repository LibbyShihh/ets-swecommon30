# ETS SWE Common 3.0 Development

本文件說明 `ets-swecommon30` Repository 的技術結構與實作方式，包含 Java Class / package 的放置原則、JSON Schema、測試資料、本機測試方式與實作範例。

工作流程、Issue、Branch 與 Pull Request 的管理方式，請以交接文件為準。

---

## 1. 專案技術結構

與 Abstract Test 實作最相關的內容主要分成三個區域：

```text
src/main/java
→ Java Test、TestNG 執行邏輯與共用工具

src/main/resources
→ JSON Schema、TestNG 設定與執行時資源

src/test/resources
→ 開發階段使用的測試資料
```

目前主要結構：

```text
ets-swecommon30/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/opengis/cite/swecommon30/
│   │   │       ├── jsonschema/
│   │   │       ├── datarecord/
│   │   │       ├── util/
│   │   │       ├── BaseJsonSchemaValidatorTest.java
│   │   │       ├── SuiteFixtureListener.java
│   │   │       ├── SuiteAttribute.java
│   │   │       ├── TestNGController.java
│   │   │       └── ...
│   │   │
│   │   ├── resources/
│   │   │   └── org/opengis/cite/swecommon30/
│   │   │       ├── jsonschema/
│   │   │       ├── testng.xml
│   │   │       └── ...
│   │   │
│   │   └── config/
│   │       └── test-run-props.xml
│   │
│   └── test/
│       └── resources/
│           └── jsondata/
│               ├── valid/
│               └── invalid/
│
└── ...
```

---

## 2. Java Class 與 package 放置原則

主要 Java 程式位於：

```text
src/main/java/org/opengis/cite/swecommon30/
```

新增 Class 前，先確認這個 Class 的用途，再決定應放在哪一個 package。

### 2.1 `jsonschema/`

路徑：

```text
src/main/java/org/opengis/cite/swecommon30/jsonschema/
```

此 package 主要放置與 JSON Schema Validation 相關的 Abstract Test。

目前例如：

```text
CoreScalarComponentsTest.java
```

A.2～A.6 的實作位於此 Class 中。

這類 Test 的典型流程：

```text
讀取測試 JSON
        ↓
確認 Component Type
        ↓
載入對應 JSON Schema
        ↓
執行 Schema Validation
        ↓
PASS / FAIL
```

如果新的 Abstract Test 主要是在驗證 JSON structure、JSON Schema、Component Type 或 Schema validity，可以優先確認是否適合放在 `jsonschema/`。

但不要只因為輸入資料是 JSON，就直接將 Class 放進 `jsonschema/`。仍應先閱讀 Test Purpose / Test Method，並確認專案中是否已有更接近的實作。

### 2.2 `datarecord/`

路徑：

```text
src/main/java/org/opengis/cite/swecommon30/datarecord/
```

此目錄目前放置與 DataRecord 相關的測試實作，例如：

```text
DataRecordTest.java
```

若新的 Abstract Test 與既有某個功能 package 高度相關，應先檢查能否延伸既有 Test Class，再決定是否新增 Class 或 package。

### 2.3 `util/`

路徑：

```text
src/main/java/org/opengis/cite/swecommon30/util/
```

`util` 用來放置多個 Test 或 Class 共用的工具邏輯。

目前例如：

```text
ClientUtils.java
JsonUtils.java
URIUtils.java
ValidationUtils.java
XMLUtils.java
TestSuiteLogger.java
```

大致用途：

```text
ClientUtils
→ HTTP Client 相關功能

JsonUtils
→ JSON 相關共用處理

URIUtils
→ URI 解析與資源取得

ValidationUtils
→ 共用 Validation 邏輯

XMLUtils
→ XML 處理

TestSuiteLogger
→ Test Suite Logging
```

使用原則：

> 只有當某段邏輯會被多個 Test 或 Class 共用時，才考慮抽成 `util`。

如果某個 Method 目前只會被單一 Test Class 使用，原則上先保留在該 Test Class 中。

Abstract Test 本身不應放進 `util/`。

### 2.4 Root package 中的共用 Class

路徑：

```text
src/main/java/org/opengis/cite/swecommon30/
```

此層包含整個 ETS 共用的 Class，例如：

```text
BaseJsonSchemaValidatorTest.java
SuiteFixtureListener.java
SuiteAttribute.java
TestNGController.java
```

這些 Class 通常不是單一 Abstract Test 的實作，而是 Test Suite 執行流程使用的共用功能。

例如 Test Suite 會透過 `SuiteFixtureListener` 取得 `iut` 指定的測試資源，並將測試檔案放入 TestNG Suite Context。

Test Class 可透過：

```java
SuiteAttribute.TEST_SUBJ_FILE
```

取得測試資料。

新增 Abstract Test 時，不應隨意修改這些共用 Class。只有確認新的 Test 確實需要改變整體 Suite 行為時，才考慮修改此層程式。

---

## 3. 新增 Class 時的判斷方式

新增 Class 前，先依照以下流程判斷：

```text
Abstract Test
        ↓
閱讀 Test Purpose / Test Method
        ↓
確認 Test 要驗證什麼
        ↓
尋找專案中最相似的既有 Test
        ↓
確認能否加入既有 Class
        ↓
再決定是否新增 Class / package
```

可使用以下原則：

```text
JSON Schema Validation
→ 優先查看 jsonschema/

特定 Component / 功能 Test
→ 查看是否已有對應 package

多個 Test 共用工具邏輯
→ util/

JSON Schema Resource
→ src/main/resources/.../jsonschema/

開發測試用 JSON
→ src/test/resources/jsondata/
```

---

## 4. JSON Schema Resource 與用途

SWE Common 3.0 使用的 JSON Schema 主要位於：

```text
src/main/resources/
└── org/opengis/cite/swecommon30/
    └── jsonschema/
        └── sweCommon/
            └── 3.0/
                └── json/
```

目前包含例如：

```text
AbstractDataComponent.json
AbstractSimpleComponent.json

Boolean.json
Category.json
Count.json
Quantity.json
Text.json

DataChoice.json
DataRecord.json
DataArray.json
DataStream.json

Geometry.json
Matrix.json
Vector.json

Time.json
TimeRange.json

basicTypes.json
encodings.json
sweCommon.json
```

### 4.1 JSON Schema 的用途

JSON Schema 用來描述 JSON 資料允許的結構與限制。

例如可以定義：

```text
某個欄位是否為 required
欄位型別是 string / number / object / array
允許哪些 properties
值是否必須符合 enum
巢狀物件應符合哪一個 Schema
多種結構之間的 oneOf / anyOf / allOf 關係
```

因此對於可以直接由 JSON Schema 表達的 Requirement，ETS 不需要在 Java 中重新手寫相同的欄位驗證規則。

概念上：

```text
OGC Requirement
        ↓
OGC 提供的 JSON Schema
        ↓
Java 載入 Schema
        ↓
schema.validate(testSubject)
        ↓
PASS / FAIL
```

本專案中的 SWE Common 3.0 JSON Schema 通常由 OGC 的標準相關資源提供，並已收錄在 Repository 的 `src/main/resources` 中。

實作時應優先使用 Repository 內既有的 OGC Schema，不應為了單一 Issue 重新建立一份重複的 Schema，也不應直接修改 Schema 來配合 Java 實作。

### 4.2 Schema Validation 與 Java 的責任

若某個 Abstract Test 的驗證條件已完整包含在對應 JSON Schema 中，Java Test 通常只需要負責：

1. 取得測試資料。
2. 確認目前測試對象是否屬於該 Test 的範圍。
3. 載入對應 JSON Schema。
4. 執行 `schema.validate(...)`。
5. 將 Validation Error 轉換成 Test 的 PASS / FAIL 結果。

例如 A.2 Boolean：

```text
Boolean JSON
        ↓
載入 Boolean.json
        ↓
schema.validate(testSubject)
        ↓
PASS / FAIL
```

這種情況下，不需要另外在 Java 中重複撰寫：

```text
required 欄位檢查
property type 檢查
enum 檢查
巢狀 JSON 結構檢查
```

因為這些規則應由 `Boolean.json` 負責。

### 4.3 什麼情況需要由 Java 補充驗證

不是所有 Abstract Test 都能只靠 JSON Schema 完成。

如果 Test Method 要求的條件超出 Schema 能直接表達或目前 Schema 實際涵蓋的範圍，就需要在 Java 中補充驗證邏輯。

例如可能包含：

```text
URI / definition 是否真的可以解析
兩個欄位的值是否代表相同語意
跨欄位或跨資源的關聯檢查
外部資源是否可存取
特定 Unit / UCUM 規則
Encoded Value 與描述內容是否一致
需要執行額外運算後才能判斷的條件
```

此時流程會變成：

```text
輸入 JSON
        ↓
先執行 JSON Schema Validation
        ↓
Schema 驗證通過
        ↓
執行 Java 額外驗證邏輯
        ↓
PASS / FAIL
```

也就是：

> **Schema 能驗證的部分交給 Schema；只有 Schema 無法完成的 Requirement，才由 Java 補齊。**

這樣可以避免在 Java 與 JSON Schema 中重複維護相同規則。

---

## 5. 如何找到 Abstract Test 對應的 JSON Schema

不只依照檔名猜測應使用哪一個 Schema。

實作時依照以下順序確認：

1. 閱讀 Abstract Test 的 **Test Purpose**。
2. 閱讀 **Test Method**。
3. 確認要驗證的 Component / Representation。
4. 到 JSON Schema 目錄尋找可能對應的 Schema。
5. 打開 Schema，確認其中的規則是否符合 Test Method。
6. 若 Schema 使用 `$ref` 引用其他 Schema，也要確認引用關係。
7. 最後才決定 Java Test 應載入哪個 Schema。

確認 Schema 時可特別查看：

```text
required
properties
type
enum
oneOf
anyOf
allOf
$ref
```

例如：

```text
Boolean
→ Boolean.json

DataChoice
→ DataChoice.json

DataRecord
→ DataRecord.json

Geometry
→ Geometry.json
```

如果 Test Method 要求整份 SWE Common JSON 文件符合整體 Schema，則應確認是否需要使用：

```text
sweCommon.json
```

而不是只驗證單一 Component Schema。

---

## 6. Java Resource Path 與實際檔案位置

Java 從 Classpath 載入 Resource 時，使用的路徑與 Repository 中的實際路徑不同。

例如：

```text
Boolean.json
```

實際檔案位置：

```text
src/main/resources/
└── org/opengis/cite/swecommon30/
    └── jsonschema/
        └── sweCommon/
            └── 3.0/
                └── json/
                    └── Boolean.json
```

Java Resource Path：

```text
/org/opengis/cite/swecommon30/jsonschema/sweCommon/3.0/json/Boolean.json
```

目前 `CoreScalarComponentsTest.java` 使用：

```java
private static final String SCHEMA_ROOT =
        "/org/opengis/cite/swecommon30/jsonschema/sweCommon/3.0/json/";
```

再搭配 Schema 檔名載入對應 Resource。

---

## 7. JSON Example

Repository 內已有 SWE Common JSON Example：

```text
src/main/resources/
└── org/opengis/cite/swecommon30/
    └── jsonschema/
        └── sweCommon/
            └── 3.0/
                └── json/
                    └── examples/
                        └── spec/
```

例如：

```text
boolean1.json
boolean2.json
category1.json
category2.json
count1.json
quantity1.json
quantity2.json
text1.json
text2.json
choice1.json
record1.json
record2.json
array1.json
array2.json
matrix1.json
geometry1.json
geometry2.json
geometry3.json
...
```

這些 Example 可以用來：

- 理解 SWE Common JSON 的正確結構。
- 尋找某個 Component 的實際範例。
- 作為 Valid Test Data 的起點。
- 複製後修改，建立 Invalid Test Data。

注意：

> Example 是資料結構的參考，不代表一定完整覆蓋目前 Abstract Test 要驗證的 Requirement。

實際驗證條件仍應以 Requirement、Test Purpose 與 Test Method 為準。

---

## 8. 測試資料

開發階段使用的測試 JSON 可放在：

```text
src/test/resources/jsondata/
```

目前已有：

```text
src/test/resources/jsondata/
├── valid/
└── invalid/
```

如果後續要針對特定 Abstract Test 建立專用測試資料，可依 Abstract Test 分類。

例如：

```text
src/test/resources/jsondata/
└── a54/
    ├── valid/
    │   └── valid-simple-component.json
    └── invalid/
        ├── missing-required-property.json
        └── invalid-property-type.json
```

至少要確認：

```text
Valid JSON
→ PASS

Invalid JSON
→ FAIL
```

不能只確認合法資料可以通過，也要確認違反 Requirement 的資料確實會被 Test 擋下。

### Invalid Test Data 建立原則

建議每一份 Invalid JSON 只刻意破壞一個主要條件。

例如：

```text
Requirement：某欄位為 required
→ 移除該欄位

Requirement：欄位必須是 number
→ 改為 string

Requirement：type 必須為指定 Component
→ 提供錯誤的 type

Requirement：值必須符合 enum
→ 提供 enum 以外的值
```

這樣 Test FAIL 時，才能清楚判斷是哪一條驗證規則生效。

---

## 9. 本機測試方式

Test Suite 透過 `iut` 參數取得測試對象。

可以修改：

```text
src/main/config/test-run-props.xml
```

將 `iut` 指向本機 JSON 測試檔案。

例如 Windows：

```xml
<entry key="iut">file:///C:/workspace/ets-swecommon30/src/test/resources/jsondata/a54/valid/valid-simple-component.json</entry>
```

執行入口：

```text
org.opengis.cite.swecommon30.TestNGController
```

基本測試流程：

```text
準備 Valid JSON
        ↓
設定 iut
        ↓
執行 Test Suite
        ↓
確認目標 Abstract Test = PASS

準備 Invalid JSON
        ↓
設定 iut
        ↓
再次執行 Test Suite
        ↓
確認目標 Abstract Test = FAIL
```

如果結果為：

```text
SKIP
```

不能視為 PASS。需確認測試資料是否真的進入目前實作的驗證邏輯。

---

## 10. Implementation Example：Abstract Test A.2

以下使用已完成的 A.2 `/conf/core/boolean-rep-valid`，說明 OGC Abstract Test 如何對應到 Java Test 與 JSON Schema。

### 10.1 Requirement

```text
/req/core/boolean-rep-valid
```

### 10.2 Abstract Test

```text
A.2
/conf/core/boolean-rep-valid
```

概念關係：

```text
Requirement
/req/core/boolean-rep-valid

        ↓

Abstract Test A.2
/conf/core/boolean-rep-valid
```

### 10.3 Java Test

位置：

```text
src/main/java/org/opengis/cite/swecommon30/jsonschema/CoreScalarComponentsTest.java
```

對應 Method：

```java
/**
 * Implements Abstract Test A.2 (/conf/core/boolean-rep-valid).
 */
@Test(description = "Implements Abstract Test A.2 (/conf/core/boolean-rep-valid)")
public void Boolean() {
    assertScalarComponentConforms("Boolean", "Boolean.json");
}
```

這代表：

```text
Abstract Test A.2
        ↓
Java Test Method
        ↓
Boolean()
```

### 10.4 共用驗證邏輯

`Boolean()` 呼叫：

```java
assertScalarComponentConforms("Boolean", "Boolean.json");
```

其中：

```text
Boolean
→ 預期的 Component Type

Boolean.json
→ 要載入的 JSON Schema
```

主要流程：

```text
輸入 JSON
        ↓
讀取 testSubject
        ↓
確認 type = Boolean
        ↓
載入 Boolean.json
        ↓
建立 JsonSchema
        ↓
schema.validate(testSubject)
        ↓
Validation Error?
        ↓
PASS / FAIL
```

主要驗證程式：

```java
JsonSchema schema = loadSchema(schemaName);

Set<ValidationMessage> errors =
        schema.validate(testSubject);

Assert.assertTrue(
        errors.isEmpty(),
        formatValidationErrors(expectedType, errors)
);
```

### 10.5 JSON Schema

A.2 使用：

```text
Boolean.json
```

位置：

```text
src/main/resources/
└── org/opengis/cite/swecommon30/
    └── jsonschema/
        └── sweCommon/
            └── 3.0/
                └── json/
                    └── Boolean.json
```

### 10.6 完整對應關係

```text
OGC Requirement
/req/core/boolean-rep-valid

        ↓

Abstract Test A.2
/conf/core/boolean-rep-valid

        ↓

CoreScalarComponentsTest.java

        ↓

public void Boolean()

        ↓

assertScalarComponentConforms(
    "Boolean",
    "Boolean.json"
)

        ↓

Boolean.json

        ↓

JsonSchema.validate()

        ↓

PASS / FAIL
```

此範例可作為後續 JSON Schema Validation 類型 Abstract Test 的參考。

A.2 屬於「Schema 已能完成主要驗證」的情況，因此 Java 的主要工作是載入 `Boolean.json` 並執行 `schema.validate(...)`，不需要再以 Java 重複撰寫相同的 Schema 規則。

新的 Test 仍應以自己的 Requirement、Test Purpose 與 Test Method 為準；若 Schema 無法完整涵蓋該 Abstract Test，再由 Java 補充必要的驗證邏輯。
