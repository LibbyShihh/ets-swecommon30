# ETS SWE Common 3.0 Development

本文件說明 `ets-swecommon30` Repository 的技術結構與實作方式，包含 Java Class / package 的放置原則、JSON Schema、測試資料、本機測試方式與實作範例。

工作流程、Issue、Branch、Pull Request 與合併責任，請以 [`ONBOARDING_GUIDE.md`](ONBOARDING_GUIDE.md) 為準；module 依賴、validator 使用與打包方式可參考 [`swecommon-validation-module.md`](swecommon-validation-module.md)；本文件僅說明技術開發與實作細節。

---

## 1. 專案技術結構

Issue #9 後，本 Repository 是由根目錄 Maven parent 管理的 multi-module 專案。兩個 module 的責任必須分開：

| Module | 責任 |
|---|---|
| `swecommon30-ets` | TEAM Engine、TestNG、IUT 取得、Suite Context、OGC Abstract Test、測試結果與錯誤提示 |
| `swecommon30-validator` | 可重用的 JSON 解析、JSON Schema 載入與驗證、Validation Error 格式化，以及 SWE Common JSON Schema Resource |

依賴方向固定為：

```text
swecommon30-ets
        ↓ depends on
swecommon30-validator
```

`SweCommonJsonSchemaValidator` 不應依賴 ETS、TestNG 或 TEAM Engine；特定 Abstract Test 的 PASS / FAIL / SKIP 語意也不應放進 validator module。

目前主要結構：

```text
ets-swecommon30/
├── pom.xml                              ← Maven parent，packaging = pom
├── swecommon30-validator/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       │   └── org/opengis/cite/swecommon30/validation/
│       │       └── SweCommonJsonSchemaValidator.java
│       └── main/resources/
│           └── org/opengis/cite/swecommon30/jsonschema/
│               └── sweCommon/3.0/json/
│                   ├── Boolean.json
│                   ├── Category.json
│                   ├── Count.json
│                   ├── Quantity.json
│                   ├── Text.json
│                   └── ...
│
└── swecommon30-ets/
    ├── pom.xml
    ├── src/
    │   ├── main/java/
    │   │   └── org/opengis/cite/swecommon30/
    │   │       ├── jsonschema/          ← OGC Abstract Test implementations
    │   │       ├── datarecord/
    │   │       ├── util/
    │   │       ├── SuiteFixtureListener.java
    │   │       ├── SuiteAttribute.java
    │   │       ├── TestNGController.java
    │   │       └── ...
    │   ├── main/resources/              ← ETS runtime resources
    │   ├── main/config/                 ← ETS and TEAM Engine configuration
    │   │   └── test-run-props.xml
    │   └── test/resources/
    │       └── jsondata/                ← ETS development test data
    │           ├── valid/
    │           └── invalid/
    └── ...
```

新增程式前，先判斷它屬於 ETS 執行流程，還是可被不同 ETS 重用的 validation 能力；不要因為輸入是 JSON，就自動將程式放入同一個 module。

---

## 2. Java Class 與 package 放置原則

ETS 的 Abstract Test 與執行流程 Java 程式位於：

```text
swecommon30-ets/src/main/java/org/opengis/cite/swecommon30/
```

可重用的 validation Java 程式位於：

```text
swecommon30-validator/src/main/java/org/opengis/cite/swecommon30/
```

新增 Class 前，先確認這個 Class 的用途與 module 責任，再決定應放在哪一個 package。

### 2.1 `jsonschema/`

路徑：

```text
swecommon30-ets/src/main/java/org/opengis/cite/swecommon30/jsonschema/
```

此 package 主要放置與 JSON Schema Validation 相關的 OGC Abstract Test。它負責 ETS 測試流程、IUT 與 Component Type 判斷，以及 TestNG 的 PASS / FAIL / SKIP 結果語意；可重用的 Schema 驗證能力則由 `swecommon30-validator` 提供。

目前例如：

```text
CoreScalarComponentsTest.java
```

A.2～A.6 的實作位於此 Class 中。

這類 Test 的典型流程：

```text
ETS 取得測試 JSON
        ↓
確認 Component Type 與測試適用性
        ↓
呼叫 swecommon30-validator
        ↓
validator 載入並執行對應 JSON Schema
        ↓
ETS 回報 PASS / FAIL / SKIP
```

如果新的 Abstract Test 主要是在驗證 JSON structure、JSON Schema、Component Type 或 Schema validity，可以優先確認是否適合放在 `swecommon30-ets` 的 `jsonschema/`。

但不要只因為輸入資料是 JSON，就直接將 Class 放進 `jsonschema/`。若是可被多個 ETS 重用的 JSON 解析、Schema 載入、Schema validation 或 Validation Error 格式化，應放在 `swecommon30-validator`，而不是 ETS 的 `util/`。仍應先閱讀 Test Purpose / Test Method，並確認專案中是否已有更接近的實作。

### 2.2 `datarecord/`

路徑：

```text
swecommon30-ets/src/main/java/org/opengis/cite/swecommon30/datarecord/
```

此目錄目前放置與 DataRecord 相關的測試實作，例如：

```text
DataRecordTest.java
```

若新的 Abstract Test 與既有某個功能 package 高度相關，應先檢查能否延伸既有 Test Class，再決定是否新增 Class 或 package。

### 2.3 `util/`

路徑：

```text
swecommon30-ets/src/main/java/org/opengis/cite/swecommon30/util/
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
→ ETS 內共用的既有 Validation 輔助邏輯；可重用 JSON Schema validation 應放在 validator module

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
swecommon30-ets/src/main/java/org/opengis/cite/swecommon30/
```

此層包含整個 ETS 共用的 Class，例如：

```text
SuiteFixtureListener.java
SuiteAttribute.java
TestNGController.java
```

這些 Class 通常不是單一 Abstract Test 的實作，而是 Test Suite 執行流程使用的共用功能。可重用的 `BaseJsonSchemaValidatorTest` 與 `SweCommonJsonSchemaValidator` 則屬於 `swecommon30-validator` module，不能放入 ETS 的 root package。

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
OGC Abstract Test / TestNG 流程
→ swecommon30-ets/src/main/java/.../

JSON Schema Validation 的可重用邏輯
→ swecommon30-validator/src/main/java/.../validation/

特定 Component / 功能 Test
→ swecommon30-ets 中是否已有對應 package

ETS 內多個 Test 共用的執行工具
→ swecommon30-ets/src/main/java/.../util/

JSON Schema Resource
→ swecommon30-validator/src/main/resources/.../jsonschema/

ETS 開發測試用 JSON
→ swecommon30-ets/src/test/resources/jsondata/
```

---

## 4. JSON Schema Resource 與用途

SWE Common 3.0 使用的 JSON Schema 主要位於 validator module：

```text
swecommon30-validator/src/main/resources/
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
swecommon30-validator 載入 Schema
        ↓
validator.validate(testSubject, schemaName)
        ↓
swecommon30-ets 回報 PASS / FAIL / SKIP
```

本專案中的 SWE Common 3.0 JSON Schema 通常由 OGC 的標準相關資源提供，並已收錄在 `swecommon30-validator/src/main/resources` 中。Schema 會隨 validator JAR 提供給使用它的 ETS module。

實作時應優先使用 Repository 內既有的 OGC Schema，不應為了單一 Issue 重新建立一份重複的 Schema，也不應直接修改 Schema 來配合 Java 實作。若缺少 Schema 無法表達的 Requirement，應先確認是否需要在對應的 ETS Abstract Test 補充 Java 驗證，而不是把 ETS-specific 邏輯放入 validator。

### 4.2 Schema Validation 與 Java 的責任

若某個 Abstract Test 的驗證條件已完整包含在對應 JSON Schema 中，責任通常分成兩層：

`swecommon30-ets` 的 Abstract Test 負責：

1. 透過 TestNG 與 Suite Context 取得測試資料。
2. 確認目前測試對象是否屬於該 Test 的範圍。
3. 將 Abstract Test 編號、Conformance URI 與預期 Component Type 放入結果訊息。
4. 將資料驗證失敗、執行問題與 Not applicable SKIP 分開回報。

`swecommon30-validator` 的共用元件負責：

1. 讀取 JSON。
2. 載入對應 JSON Schema。
3. 執行 `validate(document, schemaName)`。
4. 格式化 `ValidationMessage`。

ETS 不應在每個 Abstract Test 重複實作相同的 JSON Schema 載入與驗證流程。

例如 A.2 Boolean：

```text
Boolean JSON
        ↓
swecommon30-ets 確認 type = Boolean
        ↓
swecommon30-validator 載入 Boolean.json
        ↓
validator.validate(testSubject, "Boolean.json")
        ↓
ETS PASS / FAIL / SKIP
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
4. 到 `swecommon30-validator/src/main/resources/.../jsonschema/` 尋找可能對應的 Schema。
5. 打開 Schema，確認其中的規則是否符合 Test Method。
6. 若 Schema 使用 `$ref` 引用其他 Schema，也要確認引用關係。
7. 最後才決定 ETS Abstract Test 要傳給 validator 的 Schema 檔名。

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
swecommon30-validator/src/main/resources/
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

`CoreScalarComponentsTest.java` 不直接負責載入 Schema；它將 Schema 檔名傳給 validator。`SweCommonJsonSchemaValidator` 以 validator JAR 內的 Resource Root 載入對應 Resource：

```java
private static final String SCHEMA_ROOT =
        "/org/opengis/cite/swecommon30/jsonschema/sweCommon/3.0/json/";
```

ETS 只需要傳入例如 `Boolean.json`，不應在 ETS module 另存一份相同的 SWE Common Schema。

---

## 7. JSON Example

Repository 內已有 SWE Common JSON Example，位於 validator module：

```text
swecommon30-validator/src/main/resources/
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

開發階段由 ETS 使用的測試 JSON 可放在：

```text
swecommon30-ets/src/test/resources/jsondata/
```

目前已有：

```text
swecommon30-ets/src/test/resources/jsondata/
├── valid/
└── invalid/
```

如果後續要針對特定 Abstract Test 建立專用測試資料，可依 Abstract Test 分類。

例如：

```text
swecommon30-ets/src/test/resources/jsondata/
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

Test Suite 透過 `iut` 參數取得測試對象。這是 `swecommon30-ets` 的執行設定，不屬於 validator module。

可以修改：

```text
swecommon30-ets/src/main/config/test-run-props.xml
```

將 `iut` 指向本機 JSON 測試檔案。

例如 Windows：

```xml
<entry key="iut">file:///C:/workspace/ets-swecommon30/swecommon30-ets/src/test/resources/jsondata/a54/valid/valid-simple-component.json</entry>
```

執行入口位於 ETS module：

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
swecommon30-ets/src/main/java/org/opengis/cite/swecommon30/jsonschema/CoreScalarComponentsTest.java
```

對應 Method：

```java
/**
 * Implements Abstract Test A.2 (/conf/core/boolean-rep-valid).
 */
@Test(description = "Implements Abstract Test A.2 (/conf/core/boolean-rep-valid)")
public void Boolean() {
    assertScalarComponentConforms(
            "A.2",
            "/conf/core/boolean-rep-valid",
            "Boolean",
            "Boolean.json");
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

`Boolean()` 呼叫 ETS 內的共用測試方法：

```java
assertScalarComponentConforms(
        "A.2",
        "/conf/core/boolean-rep-valid",
        "Boolean",
        "Boolean.json");
```

四個參數分別是：

```text
A.2
→ Abstract Test 編號

/conf/core/boolean-rep-valid
→ Conformance Test URI path

Boolean
→ 預期的 Component Type

Boolean.json
→ 傳給 validator 的 Schema 檔名
```

主要流程：

```text
輸入 JSON
        ↓
swecommon30-ets 讀取 testSubject
        ↓
確認 type = Boolean
        ↓
ETS 呼叫 validator.validate(testSubject, "Boolean.json")
        ↓
validator 載入 validator JAR 內的 Boolean.json
        ↓
validator 回傳 ValidationMessage
        ↓
ETS 將結果轉換為 PASS / FAIL；不適用的 Component 則為 SKIP
```

共用 validator API 的使用方式：

```java
JsonNode testSubject = validator.readJson(testSubjectFile);
Set<ValidationMessage> errors =
        validator.validate(testSubject, "Boolean.json");

Assert.assertTrue(
        errors.isEmpty(),
        dataValidationError(
                "A.2",
                "/conf/core/boolean-rep-valid",
                validator.formatValidationErrors("Boolean", errors))
);
```

`CoreScalarComponentsTest` 負責 Abstract Test 的上下文與結果語意；`SweCommonJsonSchemaValidator` 負責可重用的 JSON 讀取、Schema 載入、Schema validation 與錯誤格式化。

### 10.5 JSON Schema

A.2 使用：

```text
Boolean.json
```

位置：

```text
swecommon30-validator/src/main/resources/
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

swecommon30-ets/
CoreScalarComponentsTest.java

        ↓

public void Boolean()

        ↓

assertScalarComponentConforms(
    "A.2",
    "/conf/core/boolean-rep-valid",
    "Boolean",
    "Boolean.json"
)

        ↓

swecommon30-validator/
SweCommonJsonSchemaValidator

        ↓

Boolean.json

        ↓

validator.validate(testSubject, "Boolean.json")

        ↓

ETS PASS / FAIL / SKIP
```

此範例可作為後續 JSON Schema Validation 類型 Abstract Test 的參考。

A.2 屬於「Schema 已能完成主要驗證」的情況，因此 `swecommon30-ets` 的主要工作是傳遞 Abstract Test 上下文與 `Boolean.json` 檔名，並由 `swecommon30-validator` 執行 `validate(...)`；不需要在 ETS 或 validator 以外再重複撰寫相同的 Schema 規則。

新的 Test 仍應以自己的 Requirement、Test Purpose 與 Test Method 為準；若 Schema 無法完整涵蓋該 Abstract Test，再由對應的 ETS Abstract Test 補充必要的驗證邏輯。可重用的補充 validation 能力若會被多個 ETS 使用，才考慮放入 validator module。
