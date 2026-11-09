<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>Kizzy の拡張フォーク — Kotlin で書かれた Discord リッチプレゼンス マネージャー</h4>
</div>

<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-27-%23?&style=flat-square&color=5b5ef7">
<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">
<img src="https://img.shields.io/badge/package-com.my.kizzy.enhanced-5b5ef7?style=flat-square">
<img src="https://img.shields.io/badge/name-Kizzy%20Enhanced-5b5ef7?style=flat-square">
<div align="center">

[![README in English](https://img.shields.io/badge/README-in%20English-blue?style=for-the-badge)](README.md)
[![README на русском](https://img.shields.io/badge/README-на%20русском%20言語-blue?style=for-the-badge)](README_RU.md)
[![README in 한국어](https://img.shields.io/badge/README-in%20한국어-blue?style=for-the-badge)](README_KR.md)
[![README in 中文（简体）](https://img.shields.io/badge/README-in%20中文（简体）-blue?style=for-the-badge)](README_ZH-HANS.md)
[![README in 中文（繁體）](https://img.shields.io/badge/README-in%20中文（繁體）-blue?style=for-the-badge)](README_ZH-HANT.md)

</div>
<p>
<img src="https://github.com/user-attachments/assets/f8402300-d628-4104-8f86-dc6021c9fbbe" width=60%/>
</p>
</div>

> [!NOTE]
> **Kizzy Enhanced** は [Kizzy](https://github.com/dead8309/Kizzy) by dead8309 の拡張フォークです。フォーク固有の追加機能には ⭐ が付いています。

## 機能

- [x] クリック可能なボタン
- [x] 現在実行中のアプリを検出
- [x] 現在再生中のメディアを検出
- [x] オプションのタイムスタンプ
- [x] カスタムステータス
- [x] プレゼンス設定の保存/読み込み
- [x] Material You テーマ
- [x] 翻訳
- [x] 簡単なセットアップ
- [x] コンソールRPCスプーフィング
- [x] 独自の画像とリンクを使用したカスタム設定作成
- [x] アプリ内でRPCをプレビュー
- [x] 画面がオフでもバックグラウンドで実行
- [x] GIF 対応
- [x] 外部 URL 対応（ウェブ上の画像 URL を指定すると、Discord に表示されます）
- [x] ギャラリーから画像を使用
- [x] ⭐ プラットフォームスプーファー（デスクトップ / ウェブ / Android / iOS / Xbox / Playstation / VR）
- [x] ⭐ カスタムアプリケーション ID — 独自の Discord アプリのアイコンと名前を使用
- [x] ⭐ 追加テンプレートプレースホルダー（`{{media_album}}`、`{{battery}}`、`{{playback}}` など）
- [x] ⭐ タイムスタンプモード選択（メディア/アプリ デフォルト · 現在の時刻 · カスタム ms）
- [x] ⭐ ホーム画面のリデザイン — フル幅カードとオン/オフ トグル
- [x] ⭐ 指数バックオフによる自動再接続ゲートウェイ（1〜2時間で切断されなくなる）
- [x] ⭐ 不壊な検出ループ — ネットワークエラーでアプリ/ゲーム検出が停止しなくなる
- [x] ⭐ 2秒アプリ切り替えポーリング（以前は5秒）でより迅速なゲーム検出

## このフォークの新機能

すべての追加機能は **Experimental RPC** 画面とホーム画面にあります。

### ⭐ プラットフォームスプーファー
- プレゼンスの接続プラットフォームを選択 — **Desktop, Web, Android, iOS, Xbox, Playstation, VR**（Equicord の platformSpoofer で使用されるプラットフォームから移植）。
- ゲートウェイ `IDENTIFY` レベルで動作（クライアントステータス）、したがって **platformindicators** のようなプラグインによって正しく認識されます。
- ライブで適用 — サービスが自動的に再起動します。手動での切り替えは不要です。

### ⭐ カスタムアプリケーション ID（独自アイコン）
- セレクター **Built-in / Custom**。Built-in はデフォルトアイコンを維持；Custom は独自の Discord **Application ID** を貼り付けるフィールドを表示し、アクティビティに **あなたの** アイコンと名前が表示されます。

### ⭐ 追加テンプレートプレースホルダー
オリジナルのものに加え、オートコンプリート付き：
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — バッテリー残量パーセント
- `{{playback}}` — 再生状態（▶ / ⏸）

### ⭐ タイムスタンプモード選択
- **メディア / アプリ（デフォルト）** — 現在のトラック/アプリからのタイムスタンプ。
- **現在の時刻と同じ** — Discord は現在時刻を経過時間として表示（例：15:30 →「15:30経過」と表示）。Vencord の customRPC と同じロジック。
- **カスタム** — **開始/終了タイムスタンプ（ミリ秒）**を自分で入力。

### ⭐ ホーム画面リデザイン
- 機能カードがフル幅の垂直スタックになりました。
- **すべてのカードにオン/オフトグルスイッチ** — Media / Custom / Experimental RPC をホーム画面から直接開始・停止可能。

### ⭐ ゲートウェイの耐久性（[YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) から移植）
- 予期しない切断時の指数バックオフによる自動再接続。
- 可能な場合はセッションを再開；3回の失敗後は新しい identify にフォールバック。
- 致命的な閉じコード（4004, 4010–4014）は永久に停止。
- プレゼンス更新待機に 60 秒の上限（以前は無限ループ）。
- 検出ループが try/catch でラップ — 単一のネットワークエラーがアプリ切り替え検出を停止しなくなり、手動再起動まで待たずに済む。
- アプリ切り替えポーリング：**2 秒**（以前は 5 秒）。
- 修正：2 つの有効アプリ間の切り替え時にプレゼンスが即座に更新されるように。

### 修正
- 「ログをシェア」でクラッシュする問題を修正（FileProvider の権限がパッケージ ID に従うように）。
- ホーム画面から RPC 機能を切り替える際のクラッシュを修正。

## システム要件
- OS: Android 8.1 から 16
- RAM: 3 GB 最小

## ダウンロード
[**Releases**](../../releases/latest) ページから最新の署名済み `app-release.apk` を入手してください。

**Kizzy Enhanced** はパッケージ ID **`com.my.kizzy.enhanced`** を使用するため、オリジナルの Kizzy をインストールしている場合でも並行してインストールできます。

> [!WARNING]
> このアプリは Discord ゲートウェイ接続を使用します。自己責任でご利用ください。

## ビルド
> 前提条件: Android Studio、Gradle、Kotlin、Jetpack Compose に関する知識。

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
Android Studio でプロジェクトを開き、インポートしてビルドして実行してください。リリースビルドは **Build Release Apk** GitHub Actions ワークフローによって自動的に生成されます。

## クレジット
✨ [Kizzy](https://github.com/dead8309/Kizzy) — オリジナルの Kizzy プロジェクト。

✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) — ゲートウェイ耐久性の修正。

✨ [Read You](https://github.com/Ashinch/ReadYou) と [Seal](https://github.com/JunkFood02/Seal) — UI コンポーネント

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) — Nintendo および Wii U ゲームデータ

✨ [Logra](https://github.com/wingio/Logra) — ログ UI

✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) — Xbox ゲームデータ

✨ [Monet](https://github.com/Kyant0/Monet) — Material3 パレット

## ライセンス
**Kizzy** は GNU GPL 3.0 オープンソースライセンスの下で公開されているプロジェクトであり、ソースコードの使用、参照、変更を無料で許可しますが、変更および派生コードをクローズドソースの商用ソフトウェアとして配布および販売することは許可されていません。
