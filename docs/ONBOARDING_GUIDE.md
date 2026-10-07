# ETS SWE Common 3.0 開發交接文件

## 1. 專案說明

本專案為 **OGC SWE Common Data Model 3.0 Executable Test Suite（ETS）** 的實作。Issue #9 後，Repository 採用 Maven multi-module 架構：

```text
根目錄 pom.xml（Maven parent）
├── swecommon30-validator
│   └── 可重用的 JSON 解析、JSON Schema 驗證與 SWE Common Schema
└── swecommon30-ets
    └── TEAM Engine、TestNG、IUT、Abstract Test 與測試結果
```

`swecommon30-ets` 依賴 `swecommon30-validator`；validator 不應依賴 ETS、TestNG 或 TEAM Engine。程式碼與資源的實際放置原則請參考 [`DEVELOPMENT_GUIDE.md`](DEVELOPMENT_GUIDE.md) 與 [`swecommon-validation-module.md`](swecommon-validation-module.md)。

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

### Branch 角色與責任

目前分支架構如下：

```text
main
  ↓
dev
  ↓
獨立 Issue Branch
```

各 Branch 的用途：

| Branch | 用途 | 合併責任 |
|---|---|---|
| `main` | 已完成整合與確認的穩定版本 | 由 Libby 或 Luke 控制合併 |
| `dev` | 各 Issue 完成後的整合分支，也是新 Issue Branch 的唯一建立基準 | 由 Libby 或 Luke 審核並合併 PR |
| 獨立 Issue Branch | 僅處理一個 Issue 的開發與測試 | 開發者建立並發送 PR；不自行合併 |

Libby 與 Luke 擁有相同的 Pull Request 審核與合併權限；開發者可將 Pull Request assign 給其中任一人。

所有新的開發工作都必須從目前的 `dev` 建立獨立 Issue Branch。不得以其他 Issue Branch 或已完成的歷史 Branch 作為新的開發基準。

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
1 個獨立 Issue Branch
        ↓
1 個 Pull Request 至 dev
```

必須遵守以下規則：

1. 每個 Issue Branch 必須直接從 `dev` 建立。
2. 不同 Issue 原則上不混在同一個 Branch 中開發。
3. 開發者完成工作後，只能建立目標為 `dev` 的 Pull Request。
4. 開發者不得直接將 Issue Branch 合併至 `dev` 或 `main`。
5. Pull Request 由 Libby 或 Luke 審核並決定是否合併至 `dev`。
6. 已合併的 Issue Branch 必須保留，不得因合併完成而刪除。
7. `dev` 整合確認後，才由 Libby 或 Luke 將 `dev` 合併至 `main`；Issue Branch 不得直接合併至 `main`。

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

[`DEVELOPMENT_GUIDE.md`](DEVELOPMENT_GUIDE.md)

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
dev
```

建立新的獨立 Issue Branch。

例如 A.54：

```bash
git switch dev
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

每個 Issue Branch 都必須直接從 `dev` 建立。

正確：

```text
dev
├── a54-schema-valid
├── a62-choice-component-types
└── a61-record-component-types
```

不要：

```text
dev
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

[`DEVELOPMENT_GUIDE.md`](DEVELOPMENT_GUIDE.md)

其中包含：

- Java 專案結構
- Class / package 放置原則
- `util/` 的用途
- JSON Schema 路徑與判斷方式
- 測試資料建立方式
- 本機測試方式
- A.2 完整 Implementation Example

核心原則：

> **Standard 決定「要驗證什麼」，`DEVELOPMENT_GUIDE.md` 說明目前 Repository 中「如何實作與測試」。**

---

## 6. 實作與測試

確認 Requirement、Test Purpose 與 Test Method 後，再開始修改程式。

實作時：

1. 先參考專案中相似的既有 Test。
2. 只修改目前 Issue 需要的範圍。
3. 不確定 Class / package 或共用邏輯放置方式時，先查看 [`DEVELOPMENT_GUIDE.md`](DEVELOPMENT_GUIDE.md)。
4. 既有實作只能作為邏輯參考；新增或調整功能時，必須符合目前 Repository 的 module 與 package 架構。

實作完成後必須進行測試。

至少確認：

```text
Valid Test Data
→ PASS

Invalid Test Data
→ FAIL
```

測試資料來源、建立方式與本機執行方式請參考：

[`DEVELOPMENT_GUIDE.md`](DEVELOPMENT_GUIDE.md)

---

## 7. 完成 Issue、Pull Request 與合併

完成實作與測試後，開發者應依下列順序處理：

1. Commit 並 push 自己的 Issue Branch。
2. 在 GitHub 建立 Pull Request。
3. 將 Pull Request 的 target / base branch 設為 `dev`。
4. 在 Pull Request 說明中連結對應 Issue，並列出實作範圍與測試／驗證結果。
5. 將 Pull Request assign 給 Libby 或 Luke 審核。
6. 等待 Libby 或 Luke 決定是否合併至 `dev`；開發者不得自行合併。
7. Pull Request 合併後，更新或關閉對應 Issue。
8. 已合併的 Issue Branch 必須保留，不得刪除。

不得建立以下 Pull Request：

```text
Issue Branch
→ main
```

正確流程是：

```text
Issue Branch
→ Pull Request to dev
→ Libby or Luke review and merge
→ dev integration verification
→ Libby or Luke-controlled merge from dev to main
```

`dev` 合併至 `main` 屬於整合與穩定版本管理流程，僅由 Libby 或 Luke 在整合確認後處理。

---

## 8. 使用 AI 協助開發

可以將以下資料一起提供給 AI：

1. 本交接文件。
2. [`DEVELOPMENT_GUIDE.md`](DEVELOPMENT_GUIDE.md)。
3. 目前被 Assign 的 Issue。
4. 對應的 OGC Abstract Test。
5. 目前 Issue Branch。
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

## 9. 開發流程總覽

```text
Issue #1 Development Plan
        ↓
找到目前指定的 Abstract Test
        ↓
Convert to issue
        ↓
從 dev 建立獨立 Issue Branch
        ↓
關聯 Issue / Branch
        ↓
閱讀 Requirement / Abstract Test
        ↓
確認 Test Purpose / Test Method
        ↓
參考 DEVELOPMENT_GUIDE.md
        ↓
實作
        ↓
Valid / Invalid Test
        ↓
Commit and push Issue Branch
        ↓
Merge latest dev into Issue Branch
        ↓
Resolve conflicts if any
        ↓
Run full test suite
        ↓
確認所有測試通過
        ↓
建立 Pull Request to dev
        ↓
Assign Libby or Luke for review
        ↓
Libby or Luke review and merge to dev
        ↓
更新或關閉 Issue
        ↓
保留已合併的 Issue Branch
        ↓
dev 整合確認後，由 Libby 或 Luke 合併至 main
```
