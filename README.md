# 玩在城院 · HyperOS 原生版（Kotlin + Compose + Miuix�?
浙大城市学院校园聚合入口�?*原生 Android 重写**�?藍本�?`[local]\wanzai`（Flutter 版，**唯讀參�?*，本專案不修改它）�?
---

## 這是什�?
把「玩在城院」從 Flutter 版重寫成原生：Kotlin + Jetpack Compose + **Miuix**
（小�?HyperOS 設計語言�?Compose 實作），對齊手機原生的觀感與效能�?
第一期（A1–A4）已完成：四個主頁、門戶登入與七個業務端點接真資料�?學在城院手機�?*免密登入**。詳見下方狀態表�?
---

## 快速開�?
```powershell
# 1) 環境變數（每次開�?shell 都要設）
$env:JAVA_HOME       = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
$env:ANDROID_HOME    = "[local]\android-sdk"
$env:ANDROID_SDK_ROOT= "[local]\android-sdk"
$env:GRADLE_USER_HOME= "[local]\.gradle-home"
Set-Location [local]

# 2) �?APK
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain "-Dorg.gradle.jvmargs=-Xmx2g"
#    �?app\build\outputs\apk\debug\app-debug.apk

# 3) 跑單元測�?.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain "-Dorg.gradle.jvmargs=-Xmx2g"
```

完整�?*可複�?*的構建說明（含每個版本為什麼是那個版本、常見坑）見
**[docs/BUILD-REPRODUCIBLE.md](docs/BUILD-REPRODUCIBLE.md)**�?
> ⚠️ 手機上會**同時裝著 Flutter 舊版**（`com.muyao.wanzaihzcu`）與本版
> （`com.muyao.wanzaihyperos`）。兩者包名不同、互不覆蓋�?> 本版 debug 的桌面標籤是�?*玩在城院（新版·測試）**」，用來區分�?
---

## 目前狀�?
| 單元 | 內容 | 狀�?|
|------|------|------|
| A0 | 構建鏈路（Gradle/AGP/Kotlin/Miuix 打通） | �?|
| A1 | Miuix UI 骨架 + 四頁導航 | �?|
| A2 | 共用介面與資料底座（限流、加密存儲、解析） | �?|
| A3 | 門戶登入與功能接入�? 端點真資料） | �?|
| A4 | 學在城院手機版免�?| 🟡 核心完成 |
| A5 | 整合與第一期交�?| �?本次 |

**測試**：`195 條單元測試，0 失敗�? 跳過` �?另有 7 條真機儀器化測試（Keystore）�?**驗收矩陣**（逐條對照計劃書，含未完成項）�?*[docs/ACCEPTANCE-MATRIX.md](docs/ACCEPTANCE-MATRIX.md)**

### A4 還沒做的（如實列出，不假裝完成）

- token 到期自動刷新／刷新單飛／輪換／刷新失�?—�?目前過期�?*安全降級**
  到學在城院自己的登入頁，不崩�?- WebView 權限按來源授予（目前不授予任何危險權限）�?- 掃碼（相機）、簽�?—�?�?*待驗**；沒有合法情境時不用入口截圖冒充成功�?
---

## 架構

```
Compose 頁面 (ui/)
      �?Repository (data/)           �?快取、StaleGuard、單�?      �?API (net/PortalApi)          �?23 個門戶端�?      �?HzcuClient (net/)            �?限流 + Cookie �?+ 脫敏 + 重試
      �?SessionStore (session/)      �?AES-256-GCM，密鑰在 Android Keystore
```

**組合�?*：`app/AppGraph.kt` —�?�?App **唯一**組裝網路層與會話層的地方�?它持有唯一�?`HzcuClient`（因為它持有 Cookie 罐；兩份客戶�?= 兩份�?= 「有時能登有時不能」）�?
### 幾條硬性紀律（改程式前請先讀�?
1. **密碼不持久化**。密碼只作參數穿過登入流程，不落盤、不進日誌�?2. **日誌只記 host + 參數名，永不記�?*（`net/Redaction.kt`）�?3. **限流**：全局 �?000ms、單端點 �?000ms�?*嚴格串行**（不是令牌桶）�?4. **未讀刷新 �?5 分鐘**；自動輪�?*預設關閉**�?5. **`total` 欄位不可�?* —�?只按**返回條數**分頁（實�?`total=20` �?`allrecords=192`）�?6. **`[local]\wanzai`（Flutter）唯讀** —�?只讀不改�?
---

## 文件導覽

| 文件 | 內容 |
|------|------|
| [ACCEPTANCE-MATRIX.md](docs/ACCEPTANCE-MATRIX.md) | **驗收矩陣**：A1–A4 逐條 + 證據 + 未完成項 |
| [BUILD-REPRODUCIBLE.md](docs/BUILD-REPRODUCIBLE.md) | **固定構建方式**：工具鏈版本與理由、命令、並存識�?|
| [A4-WALKTHROUGH.md](docs/A4-WALKTHROUGH.md) | 學在城院免登走查（含**真機才暴露的 bug** 與根因） |
| [A3-WALKTHROUGH.md](docs/A3-WALKTHROUGH.md) | 門戶登入與 7 端點接入走查 |
| [A2-WALKTHROUGH.md](docs/A2-WALKTHROUGH.md) | 資料底座走查 |
| [A1-WALKTHROUGH.md](docs/A1-WALKTHROUGH.md) | UI 骨架走查 |
| [BUILD-KOTLIN.md](docs/BUILD-KOTLIN.md) | **踩坑台賬**（�?.x）。動 Compose/Miuix 前值得先掃一�?|
| [PLAN-GPT.md](docs/PLAN-GPT.md) | 統籌計劃書（含各期狀態標註） |
| [API-PORTAL.md](docs/API-PORTAL.md) | 門�?23 端點契約（含 form/JSON 差異、`rows` 位置�?|

截圖�?`docs/shots/`（已�?PII 處理，見 A4 走查 §10）�?
---

## 憑據處理（紅線）

- 聯調用的帳號密碼**只放 `.secrets/`**，該目錄已在 `.gitignore` 中�?- **一次真機聯調結束後應把整個目錄刪掉�?*
- 倉庫內任何位置（含程式碼、測試、文檔、註解）**不得出現真實學號或密�?*�?  已用 `git grep` 與未跟蹤檔案全量掃描核對，零命中�?- 測試用的帳號密碼�?*假資�?*（本機假伺服器不校驗），與真實憑據無關�?