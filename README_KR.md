<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>Kizzy 의 향상된 포크 — Kotlin로 작성된 Discord 리치プレゼンス 매니저</h4>
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
[![README in 中文（简体）](https://img.shields.io/badge/README-in%20中文（简体）-blue?style=for-the-badge)](README_ZH-HANS.md)
[![README in 中文（繁體）](https://img.shields.io/badge/README-in%20中文（繁體）-blue?style=for-the-badge)](README_ZH-HANT.md)

</div>
<p>
<img src="https://github.com/user-attachments/assets/f8402300-d628-4104-8f86-dc6021c9fbbe" width=60%/>
</p>
</div>

> [!NOTE]
> **Kizzy Enhanced** 는 dead8309의 [Kizzy](https://github.com/dead8309/Kizzy)를 향상시킨 포크입니다. 포크 전용 추가 기능은 ⭐로 표시되어 있습니다.

## 기능

- [x] 클릭 가능한 버튼
- [x] 현재 실행 중인 앱 감지
- [x] 현재 재생 중인 미디어 감지
- [x] 선택적 타임스탬프
- [x] 커스텀 상태
- [x] 존재 설정 저장/불러오기
- [x] Material You 테마
- [x] 번역
- [x] 간편한 설정
- [x] 콘솔 RPC 스푸핑
- [x] 자체 이미지와 링크로 커스텀 설정 만들기
- [x] 앱 내에서 RPC 미리보기
- [x] 화면이 꺼져도 백그라운드에서 실행
- [x] GIF 지원
- [x] 외부 URL 지원 (웹상의 이미지 URL을 제공하면 Discord에 표시됨)
- [x] 갤러리에서 이미지 사용
- [x] ⭐ 플랫폼 스푸퍼 (데스크톱 / 웹 / Android / iOS / Xbox / Playstation / VR)
- [x] ⭐ 커스텀 애플리케이션 ID — 자체 Discord 앱 아이콘과 이름 사용
- [x] ⭐ 추가 템플릿 플레이스홀더 (`{{media_album}}`, `{{battery}}`, `{{playback}}` 등)
- [x] ⭐ 타임스탬프 모드 선택기 (미디어/앱 기본 · 현재 시간 · 커스텀 ms)
- [x] ⭐ 홈 화면 리디자인 — 전체 너비 카드와 온/오프 토글
- [x] ⭐ 지수 백오프를 사용한 자동 재연결 게이트웨이 (1~2시간 후 끊김 없음)
- [x] ⭐ 끊어지지 않는 탐지 루프 — 네트워크 오류로 앱/게임 탐지 중단 없음
- [x] ⭐ 2초 앱 전환 폴링 (이전 5초)로 더 빠른 게임 탐지

## 이 포크의 새로운 기능

모든 추가 기능은 **Experimental RPC** 화면과 홈 화면에 있습니다.

### ⭐ 플랫폼 스푸퍼
- 존재가 연결될 플랫폼 선택 — **데스크톱, 웹, Android, iOS, Xbox, Playstation, VR** (Equicord의 platformSpoofer에서 사용되는 플랫폼에서 이식).
- 게이트웨이 `IDENTIFY` 수준에서 작동 (클라이언트 상태), 따라서 **platformindicators** 같은 플러그인에서 올바르게 인식됨.
- 라이브로 적용 — 서비스가 자동으로 다시 시작됩니다. 수동 전환 불필요.

### ⭐ 커스텀 애플리케이션 ID (자체 아이콘)
- 선택기 **Built-in / Custom**. Built-in은 기본 아이콘 유지; Custom은 자체 Discord **Application ID**를 붙여넣는 필드를 표시하여 활동에 **자신의** 아이콘과 이름이 표시됨.

### ⭐ 추가 템플릿 플레이스홀더
기존 것 외에도 자동 완성 포함:
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — 배터리 잔량 퍼센트
- `{{playback}}` — 재생 상태 (▶ / ⏸)

### ⭐ 타임스탬프 모드 선택기
- **미디어 / 앱 (기본)** — 현재 트랙/앱의 타임스탬프.
- **현재 시간과 동일** — Discord가 현재 시간을 경과 시간으로 표시 (예: 15:30 → "15:30 경과"). Vencord의 customRPC와 동일한 로직.
- **사용자 지정** — **시작/종료 타임스탬프 (밀리초)**를 직접 입력.

### ⭐ 홈 화면 리디자인
- 기능 카드가 전체 너비의 수직 스택이 됨.
- **모든 카드에 온/오프 토글 스위치** — Media / Custom / Experimental RPC를 홈 화면에서 직접 시작/중지 가능.

### ⭐ 게이트웨이 복원력 ([YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy)에서 이식)
- 예기치 않은 연결 끊김 시 지수 백오프를 사용한 자동 재연결.
- 가능한 경우 세션 재개; 3회 실패 후 새로운 identify로 폴백.
- 치명적 닫힘 코드 (4004, 4010–4014)는 영구적으로 중단.
- 존재 업데이트 대기 60초 상한 (이전은 무한 루프).
- 탐지 루프가 try/catch로 래핑 — 단일 네트워크 오류가 수동 재시작까지 앱 전환 탐지를 중단하지 않음.
- 앱 전환 폴링: **2초** (이전 5초).
- 수정: 두 활성 앱 간 전환 시 존재가 즉시 업데이트됨.

### 수정
- "로그 공유" 시 크래시 수정 (FileProvider 권한이 패키지 ID를 따르도록).
- 홈 화면에서 RPC 기능 전환 시 크래시 수정.

## 시스템 요구사항
- OS: Android 8.1 ~ 16
- RAM: 3 GB 이상

## 다운로드
[**Releases**](../../releases/latest) 페이지에서 최신 서명된 `app-release.apk`를 다운로드하세요.

**Kizzy Enhanced**는 패키지 ID **`com.my.kizzy.enhanced`**를 사용하므로 원래 Kizzy를 설치한 경우에도 병렬로 설치할 수 있습니다.

> [!WARNING]
> 이 앱은 Discord 게이트웨이 연결을 사용합니다. 자체 위험에 의해 사용하세요.

## 빌드
> 전제 조건: Android Studio, Gradle, Kotlin, Jetpack Compose에 대한 지식.

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
Android Studio에서 프로젝트를 열고 가져오고, 빌드하고 실행하세요. 릴리스 빌드는 **Build Release Apk** GitHub Actions 워크플로에 의해 자동으로 생성됩니다.

## 크레디트
✨ [Kizzy](https://github.com/dead8309/Kizzy) — 원래 Kizzy 프로젝트.

✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) — 게이트웨이 복원력 수정.

✨ [Read You](https://github.com/Ashinch/ReadYou) 및 [Seal](https://github.com/JunkFood02/Seal) — UI 컴포넌트

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) — Nintendo 및 Wii U 게임 데이터

✨ [Logra](https://github.com/wingio/Logra) — 로그 UI

✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) — Xbox 게임 데이터

✨ [Monet](https://github.com/Kyant0/Monet) — Material3 팔레트

## 라이선스
**Kizzy**는 GNU GPL 3.0 오픈 소스 라이선스에 따라 배포되는 오픈 소스 프로젝트이며, 소스 코드의 사용, 참조, 수정을 무료로 허용하지만 수정 및 파생 코드를 폐쇄 소스 상용 소프트웨어로 배포 및 판매하는 것은 허용하지 않습니다.
