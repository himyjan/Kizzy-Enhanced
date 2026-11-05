<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>Kizzy 的增强分支 — 使用 Kotlin 编写的 Discord 富状态管理器</h4>
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
[![README in 中文（繁體）](https://img.shields.io/badge/README-in%20中文（繁體）-blue?style=for-the-badge)](README_ZH-HANT.md)

</div>
<p>
<img src="https://github.com/user-attachments/assets/f8402300-d628-4104-8f86-dc6021c9fbbe" width=60%/>
</p>
</div>

> [!NOTE]
> **Kizzy Enhanced** 是 [Kizzy](https://github.com/dead8309/Kizzy) by dead8309 的个人增强分支。分支特有的新增功能标记为 ⭐。

## 功能

- [x] 可点击的按钮
- [x] 检测当前运行中的应用
- [x] 检测当前播放的媒体
- [x] 可选的时间戳
- [x] 自定义状态
- [x] 保存/加载状态配置
- [x] Material You 主题
- [x] 翻译
- [x] 简单设置
- [x] 控制台 RPC 模拟
- [x] 使用自己的图片和链接创建自定义配置
- [x] 在应用内预览 RPC
- [x] 屏幕关闭时仍在后台运行
- [x] GIF 支持
- [x] 外部 URL 支持（可以提供网络上的图片 URL，Discord 会显示它）
- [x] 从相册使用图片
- [x] ⭐ 平台欺骗（桌面 / 网页 / Android / iOS / Xbox / Playstation / VR）
- [x] ⭐ 自定义应用 ID — 使用你自己的 Discord 应用图标和名称
- [x] ⭐ 额外的模板占位符（`{{media_album}}`、`{{battery}}`、`{{playback}}` 等）
- [x] ⭐ 时间戳模式选择器（媒体/应用 默认 · 当前时间 · 自定义 ms）
- [x] ⭐ 主页面重新设计 — 全宽卡片与开关
- [x] ⭐ 具有指数退避的自动重连网关（不会在 1-2 小时后断开）
- [x] ⭐ 不中断的检测循环 — 网络错误不再停止应用/游戏检测
- [x] ⭐ 2 秒应用切换轮询（原为 5 秒）以实现更快的游戏检测

## 此分支的新功能

所有新增功能都位于 **实验性 RPC** 页面和主页面上。

### ⭐ 平台欺骗
- 选择你的状态连接的平台 — **桌面、网页、Android、iOS、Xbox、Playstation、VR**（从 Equicord 的 platformSpoofer 移植而来）。
- 在网关 `IDENTIFY` 层级运作（客户端状态），因此会被 **platformindicators** 等插件正确识别。
- 即时生效 — 服务会自动重启，无需手动切换。

### ⭐ 自定义应用 ID（自定义图标）
- 选择器 **内置 / 自定义**。内置保留默认图标；自定义显示一个字段让你粘贴自己的 Discord **应用 ID**，因此活动会显示 **你自己的** 图标和名称。

### ⭐ 额外的模板占位符
除了原有的模板之外，还有自动完成功能：
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — 电池电量百分比
- `{{playback}}` — 播放状态（▶ / ⏸）

### ⭐ 时间戳模式选择器
- **媒体 / 应用（默认）** — 来自当前曲目/应用的时间戳。
- **与当前时间相同** — Discord 会将当前时间显示为经过时间（例如 15:30 → 显示「15:30 已过」）。与 Vencord 的 customRPC 逻辑相同。
- **自定义** — 自行输入 **开始/结束时间戳（以毫秒为单位）**。

### ⭐ 主页面重新设计
- 功能卡片现在是垂直堆叠的全宽卡片。
- **每张卡片都有开关切换** — Media / Custom / Experimental RPC 可以直接从主页面启动和停止。

### ⭐ 网关复原能力（从 [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) 移植）
- 发生任何非预期断线时，以指数退避方式自动重连。
- 尽可能恢复连接；3 次失败后会执行全新的 identify。
- 致命关闭代码（4004, 4010–4014）仍然会永久停止。
- 状态更新等待上限为 60 秒（原先为无限循环）。
- 检测循环现在以 try/catch 包裹 — 单一网络错误不再会停止应用/游戏检测，直到手动重新启动。
- 应用切换轮询：**2 秒**（原先为 5 秒）。
- 修正：在两个启用的应用之间切换时，现在会立即更新状态。

### 修正
- 修正「分享日志」时的崩溃（FileProvider 权限现在遵循包 ID）。
- 修正从主页面切换 RPC 功能时的崩溃。

## 系统需求
- OS: Android 8.1 到 16
- RAM: 最低 3 GB

## 下载
从 [**Releases**](../../releases/latest) 页面下载最新的签署 `app-release.apk`。

**Kizzy Enhanced** 使用包 ID **`com.my.kizzy.enhanced`**，因此它可以与原始 Kizzy 并行安装。

> [!WARNING]
> 此应用程序使用 Discord 网关连接。请自行承担风险使用。

## 构建
> 前提条件：Android Studio，熟悉 Gradle、Kotlin 和 Jetpack Compose。

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
在 Android Studio 中打开项目，导入，然后构建并运行。发行构建由 **Build Release Apk** GitHub Actions 工作流自动生成。

## 致谢
✨ [Kizzy](https://github.com/dead8309/Kizzy) — 原始 Kizzy 项目。

✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) — 网关复原能力修正。

✨ [Read You](https://github.com/Ashinch/ReadYou) 与 [Seal](https://github.com/JunkFood02/Seal) — UI 组件

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) — Nintendo 与 Wii U 游戏资料

✨ [Logra](https://github.com/wingio/Logra) — 日志 UI

✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) — Xbox 游戏资料

✨ [Monet](https://github.com/Kyant0/Monet) — Material3 调色板

## 授权
**Kizzy** 是一个 GNU GPL 3.0 开放原始码项目，允许你免费使用、参考和修改原始码，但不允许将修改后和衍生的程式码作为闭原始码商业软件分发和销售。
