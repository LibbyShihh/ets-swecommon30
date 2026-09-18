# ETS SWE Common 3.0 開發交接文件

## 1. 專案說明

本專案為 **OGC SWE Common Data Model 3.0 Executable Test Suite（ETS）** 的實作。

開發時主要會使用以下資料：

### OGC 標準文件

https://docs.ogc.org/is/24-014/24-014.html

標準文件主要定義：

- Requirement
- Conformance Class
- Abstract Test
- Test Purpose
- Test Method

### ETS GitHub 專案

官方 Repository：

https://github.com/opengeospatial/ets-swecommon30

目前開發使用的 Fork：

https://github.com/LibbyShihh/ets-swecommon30

目前開發主要以下列 Branch 作為基準：

```text
core-a2-a6-data-representation
```

本 Fork 的實作順序與開發進度統一由 Development Plan 管理：

https://github.com/LibbyShihh/ets-swecommon30/issues/1

Issue #1「Development Plan」是目前專案的主要開發清單，後續 Abstract Test 會依照 Phase 與指定順序進行實作。

---

## 2. 基本開發規則

目前開發原則為：

```text
1 個 Abstract Test
        ↓
1 個 Issue
        ↓
1 個 Feature Branch
```

不同 Issue 原則上不混在同一個 Branch 中開發。

例如：

```text
A.54
→ a54-schema-valid

A.62
→ a62-choice-component-types

A.61
→ a61-record-component-types
```

程式架構、Class / package、JSON Schema、測試資料與本機測試方式，統一參考 Repository 中的：

```text
DEVELOPMENT.md
```

---

## 3. 開始新的 Issue

每次只處理目前指定要開始的 Abstract Test。

開始前：

1. 開啟 Issue #1「Development Plan」。
2. 依照目前指定的實作順序，找到準備開始的 Abstract Test。
3. 將滑鼠移到該 Checklist 項目上。
4. 使用該項目的選單，選擇 **Convert to issue**。
5. Convert 後，該 Abstract Test 會成為一個獨立 Issue。
6. 將該 Issue Assign 給實作者。

後續與該 Abstract Test 有關的討論、實作資訊，都應集中在對應 Issue 中。

---

## 4. 建立開發 Branch

每個 Issue 開始前，都要從：

```text
core-a2-a6-data-representation
```

建立新的 Feature Branch。

例如 A.54：

```bash
git switch core-a2-a6-data-representation
git pull
git switch -c a54-schema-valid
```

Branch 命名：

```text
aXX-簡短功能名稱
```

例如：

```text
a54-schema-valid
a62-choice-component-types
a61-record-component-types
```

每個 Feature Branch 都必須直接從：

```text
core-a2-a6-data-representation
```

建立。

正確：

```text
core-a2-a6-data-representation
├── a54-schema-valid
├── a62-choice-component-types
└── a61-record-component-types
```

不要：

```text
core-a2-a6-data-representation
        ↓
a54-schema-valid
        ↓
a62-choice-component-types
        ↓
a61-record-component-types
```

否則後面的 Branch 可能同時包含前一個 Issue 的修改。

Branch 建立後，要將 Branch 與對應 Issue 做關聯，方便從 Issue 追蹤開發狀態。

---

## 5. 開始實作前的確認

Branch 建立完成後，不要直接開始修改程式。

先確認：

1. 目前實作的是哪一個 Abstract Test。
2. Abstract Test 對應哪一個 Requirement。
3. Test Purpose 要驗證什麼。
4. Test Method 指定要如何驗證。
5. 預期什麼資料應該 PASS。
6. 預期什麼資料應該 FAIL。
7. 是否已找到可參考的既有實作。

基本關係：

```text
Requirement
        ↓
Abstract Test
        ↓
Test Purpose / Test Method
        ↓
ETS Implementation
        ↓
Test
```

程式面的詳細說明請參考：

```text
DEVELOPMENT.md
```

其中包含：

- Java 專案結構
- Class / package 放置原則
- `util/` 的用途
- JSON Schema 路徑與判斷方式
- 測試資料建立方式
- 本機測試方式
- A.2 完整 Implementation Example

核心原則：

> **Standard 決定「要驗證什麼」，DEVELOPMENT.md 說明目前 Repository 中「如何實作與測試」。**

---

## 6. 實作與測試

確認 Requirement、Test Purpose 與 Test Method 後，再開始修改程式。

實作時：

1. 先參考專案中相似的既有 Test。
2. 只修改目前 Issue 需要的範圍。
3. 不確定 Class / package 或共用邏輯放置方式時，先查看 `DEVELOPMENT.md`。

實作完成後必須進行測試。

至少確認：

```text
Valid Test Data
→ PASS

Invalid Test Data
→ FAIL
```

測試資料來源、建立方式與本機執行方式請參考：

```text
DEVELOPMENT.md
```

---

## 9. 完成 Issue 與更新 Development Plan

目前 Issue 完成後，將 Feature Branch 推送到 GitHub，回到：

```text
Issue #1 - Development Plan
```

確認對應 Abstract Test 已標記完成。

Development Plan：

https://github.com/LibbyShihh/ets-swecommon30/issues/1


---

## 9. 使用 AI 協助開發

可以將以下資料一起提供給 AI：

1. 本交接文件。
2. `DEVELOPMENT.md`。
3. 目前被 Assign 的 Issue。
4. 對應的 OGC Abstract Test。
5. 目前 Feature Branch。
6. 相關 Java Class。
7. 可能使用的 JSON Schema。

建議先要求 AI 協助確認：

```text
1. 對應的 Requirement 是什麼？
2. Test Purpose 是什麼？
3. Test Method 要求什麼？
4. 可以參考哪個既有 Java Test？
5. 預計修改或新增哪個 Class / Method？
6. 是否有對應 JSON Schema？
7. Valid / Invalid Test Data 應如何準備？
```

---

## 10. 開發流程總覽

```text
Issue #1 Development Plan
        ↓
找到目前指定的 Abstract Test
        ↓
Convert to issue
        ↓
從 core-a2-a6-data-representation 建立 Feature Branch
        ↓
關聯 Issue / Branch
        ↓
閱讀 Requirement / Abstract Test
        ↓
確認 Test Purpose / Test Method
        ↓
參考 DEVELOPMENT.md
        ↓
實作
        ↓
Valid / Invalid Test
        ↓
Push
        ↓
完成 Issue
        ↓
回 Issue #1 確認完成狀態
```
