<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>Kizzy 的增強分支 — 使用 Kotlin 編寫的 Discord 豐富狀態管理器</h4>
</div>

<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-27-%23?&style=flat-square&color=5b5ef7">
<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">
<img src="https://img.shields.io/badge/package-com.my.kizzy.enhanced-5b5ef7?style=flat-square">
<img src="https://img.shields.io/badge/name-Kizzy%20Enhanced-5b5ef7?style=flat-square">
<div align="center">

[![README in English](https://img.shields.io/badge/README-in%20English-blue?style=for-the-badge)](README.md)
[![README на русском](https://img.shields.io/badge/README-на%20русском%20языке-blue?style=for-the-badge)](README_RU.md)
[![README in 日本語](https://img.shields.io/badge/README-in%20日本語-blue?style=for-the-badge)](README_JP.md)
[![README in 한국어](https://img.shields.io/badge/README-in%20한국어-blue?style=for-the-badge)](README_KR.md)
[![README in 中文（简体）](https://img.shields.io/badge/README-in%20中文（简体）-blue?style=for-the-badge)](README_ZH-HANS.md)

</div>
<p>
<img src="https://github.com/user-attachments/assets/f8402300-d628-4104-8f86-dc6021c9fbbe" width=60%/>
</p>
</div>

> [!NOTE]
> **Kizzy Enhanced** 是 [Kizzy](https://github.com/dead8309/Kizzy) by dead8309 的個人增強分支。分支特有的新增功能標記為 ⭐。

## 功能

- [x] 可點擊的按鈕
- [x] 偵測當前執行中的應用程式
- [x] 偵測當前播放的媒體
- [x] 選擇性時間戳
- [x] 自訂狀態
- [x] 儲存/載入 狀態設定
- [x] Material You 主題
- [x] 翻譯
- [x] 簡單設定
- [x] 控制台 RPC 模擬
- [x] 使用自己的圖片和連結建立自訂設定
- [x] 在應用程式內預覽 RPC
- [x] 螢幕關閉時仍在背景執行
- [x] GIF 支援
- [x] 外部 URL 支援 (可以提供網路上的圖片 URL，Discord 會顯示它)
- [x] 從圖庫使用圖片
- [x] ⭐ 平台偽裝 (桌面 / 網頁 / Android / iOS / Xbox / Playstation / VR)
- [x] ⭐ 自訂應用程式 ID — 使用你自己的 Discord 應用程式圖示和名稱
- [x] ⭐ 額外的範本預留位置 (`{{media_album}}`、`{{battery}}`、`{{playback}}` 等)
- [x] ⭐ 時間戳模式選擇器 (媒體/應用程式 預設 · 當前時間 · 自訂 ms)
- [x] ⭐ 主畫面重新設計 — 全寬卡片與開關
- [x] ⭐ 具有指數退避的自動重新連接閘道 (不會在 1-2 小時後斷線)
- [x] ⭐ 無中斷偵測迴圈 — 網路錯誤不再停止應用程式/遊戲偵測
- [x] ⭐ 2 秒應用程式切換輪詢 (原為 5 秒) 以實現更快的遊戲偵測

## 此分支的新功能

所有新增功能都位於 **實驗性 RPC** 畫面和主畫面上。

### ⭐ 平台偽裝
- 選擇你的狀態連線的平台 — **桌面、網頁、Android、iOS、Xbox、Playstation、VR** (從 Equicord 的 platformSpoofer 移植而來)。
- 在閘道 `IDENTIFY` 層級運作 (用戶端狀態)，因此會被 **platformindicators** 等外掛正確識別。
- 即時套用 — 服務會自動重新啟動，無需手動切換。

### ⭐ 自訂應用程式 ID (自訂圖示)
- 選擇器 **內建 / 自訂**。內建保留預設圖示；自訂顯示一個欄位讓你貼上自己的 Discord **應用程式 ID**，因此活動會顯示 **你自己的** 圖示和名稱。

### ⭐ 額外的範本預留位置
除了原有的範本之外，還有自動完成功能：
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — 電池電量百分比
- `{{playback}}` — 播放狀態 (▶ / ⏸)

### ⭐ 時間戳模式選擇器
- **媒體 / 應用程式 (預設)** — 來自目前軌跡/應用程式的時間戳。
- **與目前時間相同** — Discord 會將目前時間顯示為經過時間 (例如 15:30 → 顯示「15:30 已過」)。與 Vencord 的 customRPC 邏輯相同。
- **自訂** — 自行輸入 **開始/結束時間戳 (以毫秒為單位)**。

### ⭐ 主畫面重新設計
- 功能卡片現在是垂直堆疊的全寬卡片。
- **每張卡片都有開關切換** — Media / Custom / Experimental RPC 可以直接從主畫面啟動和停止。

### ⭐ 閘道復原能力 (從 [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) 移植)
- 發生任何非預期斷線時，以指數退避方式自動重新連接。
- 盡可能恢復連線；3 次失敗後會執行全新的 identify。
- 致命關閉代碼 (4004, 4010–4014) 仍然會永久停止。
- 狀態更新等待上限為 60 秒 (原先為無限迴圈)。
- 偵測迴圈現在以 try/catch 包裝 — 單一網路錯誤不再會停止應用程式/遊戲偵測，直到手動重新啟動。
- 應用程式切換輪詢：**2 秒** (原先為 5 秒)。
- 修正：在兩個啟用的應用程式之間切換時，現在會立即更新狀態。

### 修正
- 修正「分享日誌」時的崩潰 (FileProvider 權限現在遵循套件 ID)。
- 修正從主畫面切換 RPC 功能時的崩潰。

## 系統需求
- OS: Android 8.1 到 16
- RAM: 最低 3 GB

## 下載
從 [**Releases**](../../releases/latest) 頁面下載最新的簽署 `app-release.apk`。

**Kizzy Enhanced** 使用套件 ID **`com.my.kizzy.enhanced`**，因此它可以與原始 Kizzy 並行安裝。

> [!WARNING]
> 此應用程式使用 Discord 閘道連接。請自行承擔風險使用。

## 建置
> 前提條件：Android Studio，熟悉 Gradle、Kotlin 和 Jetpack Compose。

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
在 Android Studio 中開啟專案，匯入，然後建置並執行。發行組建由 **Build Release Apk** GitHub Actions 工作流程自動產生。

## 致謝
✨ [Kizzy](https://github.com/dead8309/Kizzy) — 原始 Kizzy 專案。

✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) — 閘道復原能力修正。

✨ [Read You](https://github.com/Ashinch/ReadYou) 與 [Seal](https://github.com/JunkFood02/Seal) — UI 元件

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) — Nintendo 與 Wii U 遊戲資料

✨ [Logra](https://github.com/wingio/Logra) — 日誌 UI

✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) — Xbox 遊戲資料

✨ [Monet](https://github.com/Kyant0/Monet) — Material3 調色板

## 授權
**Kizzy** 是一個 GNU GPL 3.0 開放原始碼專案，允許你免費使用、參考和修改原始碼，但不允許將修改後和衍生的程式碼作為閉原始碼商業軟體分發和銷售。
