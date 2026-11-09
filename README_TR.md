<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4><a href="https://github.com/dead8309/Kizzy">Kizzy</a> uygulamasının geliştirilmiş bir çatalı (fork) — Kotlin ile yazılmış Android için Discord Rich Presence yöneticisi.</h4>
</div>

<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-27-%23?&style=flat-square&color=5b5ef7">
<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">
<img src="https://img.shields.io/badge/package-com.my.kizzy.enhanced-5b5ef7?style=flat-square">
<img src="https://img.shields.io/badge/name-Kizzy%20Enhanced-5b5ef7?style=flat-square">
<div align="center">

[![README in English](https://img.shields.io/badge/README-in%20English-blue?style=for-the-badge)](README.md)
[![README Türkçe'de](https://img.shields.io/badge/README-Türkçe'de-blue?style=for-the-badge)](README_TR.md)

</div>
</div>
<p>
<img src="https://github.com/user-attachments/assets/f8402300-d628-4104-8f86-dc6021c9fbbe" width=60%/>
</p>
</div>

> [!NOTE]
> **Kizzy Enhanced**, dead8309 tarafından geliştirilen [Kizzy](https://github.com/dead8309/Kizzy) projesinin kişisel bir çatalıdır (fork). Fork'a özel eklemeler aşağıda ⭐ ile işaretlenmiştir.

## Özellikler

- [x] Tıklanabilir düğmeler
- [x] Şu anda çalışan uygulamayı algılama
- [x] Şu anda çalınan medyayı algılama
- [x] İsteğe bağlı zaman damgaları (Timestamps)
- [x] Özel durum (Custom Status)
- [x] Varlık yapılandırmalarını kaydetme/yükleme
- [x] Material You teması
- [x] Çeviriler
- [x] Kolay kurulum
- [x] Konsol RPC taklidi (Spoof)
- [x] Kendi görsel ve bağlantılarınızla özel yapılandırmalar oluşturma
- [x] Uygulamanın içinde RPC önizlemesi
- [x] Ekran kapalıyken bile arka planda çalışma
- [x] GIF desteği
- [x] Harici URL desteği (web üzerindeki bir görselin bağlantısını verebilirsiniz, Discord bunu gösterir!)
- [x] Galeriden görsel kullanabilme
- [x] ⭐ Platform spoofer (Masaüstü / Web / Android / iOS / Xbox / Playstation / VR)
- [x] ⭐ Özel Uygulama Kimliği (Application ID) — kendi Discord uygulama simgenizi ve adınızı kullanın
- [x] ⭐ Ek şablon yer tutucuları (`{{media_album}}`, `{{battery}}`, `{{playback}}` ve daha fazlası)
- [x] ⭐ Zaman damgası modu seçici (Medya/Uygulama varsayılanı · Güncel saat · Özel ms)
- [x] ⭐ Ana ekran yeniden tasarımları — açma/kapama anahtarlarına sahip tam genişlikte kartlar
- [x] ⭐ Üstel geri çekilme (exponential backoff) özellikli otomatik yeniden bağlanma ağ geçidi (1-2 saat sonra kopma sorunu yok)
- [x] ⭐ Kırılamaz algılama döngüsü — ağ hataları artık uygulama/oyun algılamasını durdurmaz
- [x] ⭐ Daha hızlı oyun algılama için 2 saniyelik uygulama geçiş yoklaması (önceden 5 saniyeydi)

## Bu Fork'taki Yenilikler Neler?

Tüm eklemeler **Experimental RPC** ekranında ve ana ekranda yer almaktadır.

### ⭐ Platform Spoofer
- Varlığınızın hangi platformdan bağlanacağını seçin — **Masaüstü, Web, Android, iOS, Xbox, Playstation, VR** (Equicord'un platformSpoofer'ından port edilmiştir).
- Ağ geçidinin `IDENTIFY` düzeyinde (istemci durumu) çalışır, bu sayede **platformindicators** gibi eklentiler tarafından doğru şekilde algılanır.
- **Canlı (live)** olarak uygulanır — servis kendini yeniden başlatır, manuel bir geçiş yapmanıza gerek kalmaz.

### ⭐ Özel Uygulama Kimliği (Kendi Simgeniz)
- **Built-in / Custom** seçici. Built-in varsayılan simgeyi korur; Custom ise kendi Discord **Application ID**'nizi gireceğiniz bir alan açarak aktivitenin **sizin** simgenizi ve adınızı göstermesini sağlar.

### ⭐ Ek Şablon Yer Tutucuları
Orijinallere ek olarak, otomatik tamamlama desteğiyle:
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — pil yüzdesi
- `{{playback}}` — oynatma durumu (▶ / ⏸)

### ⭐ Zaman Damgası Modu Seçici
- **Medya / Uygulama (Varsayılan)** — mevcut parça/uygulamadan alınan zaman damgaları.
- **Günün şu anki saati ile aynı** — Discord mevcut saati geçen süre olarak gösterir (örn. saat 15:30'da → "15:30 geçti" gösterir). Vencord'un customRPC'si ile aynı mantık.
- **Özel (Custom)** — ham **Başlangıç / Bitiş Zaman Damgası (milisaniye cinsinden)** değerini kendiniz girin.

### ⭐ Ana Ekran Yeniden Tasarımı
- Özellik kartları artık tam genişlikte kartlardan oluşan dikey bir yığın halindedir.
- **Her kart üzerinde açma/kapama anahtarları**, böylece Medya / Custom / Experimental RPC doğrudan ana ekrandan başlatılabilir ve durdurulabilir.

### ⭐ Ağ Geçidi Dayanıklılığı ([YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy)'den port edilmiştir)
- Beklenmeyen bağlantı kopmalarında üstel geri çekilme ile otomatik yeniden bağlanma.
- Mümkün olduğunda oturumu devam ettirir; 3 başarısız denemeden sonra yeni bir identify işlemine geri döner.
- Kritik kapanış kodları (4004, 4010–4014) kalıcı olarak durdurmaya devam eder.
- Varlık güncelleme beklemelerinde 60 saniyelik sınır (sonsuz döngü yerine).
- Algılama döngüsü artık try/catch ile korunmaktadır — tek bir ağ hatası artık manuel yeniden başlatma gerektirmeden uygulama geçiş algılamasını durdurmaz.
- Uygulama geçiş yoklaması: **2 saniye** (önceden 5 saniyeydi).
- Düzeltme: Etkin iki uygulama arasında geçiş yapmak artık varlığı hemen günceller.

### Düzeltmeler
- **"Log paylaş"** sırasındaki çökme düzeltildi (FileProvider yetkisi artık paket kimliğini takip ediyor).
- Ana ekrandan RPC özellikleri değiştirilirken oluşan çöküşler düzeltildi.

## Sistem Gereksinimleri
- İşletim Sistemi: Android 8.1 - 16 arası
- RAM: Minimum 3 GB

## İndirme
En son imzalanmış `app-release.apk` dosyasını [**Releases**](../../releases/latest) sayfasından edinin.

**Kizzy Enhanced**, **`com.my.kizzy.enhanced`** paket kimliğini kullanır; bu sayede orijinal Kizzy yüklü olsa bile yanında çalışabilir.

> [!WARNING]
> Bu uygulama Discord Ağ Geçidi bağlantısını kullanır. Kullanımı tamamen kendi sorumluluğunuzdadır.

## Derleme (Build)
> Önkoşullar: Android Studio ve Gradle, Kotlin ile Jetpack Compose hakkında bilgi sahibi olmak.

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
Projeyi Android Studio'da açın, içeri aktarın ve derleyip çalıştırın (Build and Run). Release yapıları otomatik olarak **Build Release Apk** GitHub Actions iş akışı tarafından üretilir.

## Emeği Geçenler (Credits)
✨ Orijinal Kizzy projesi için [Kizzy](https://github.com/dead8309/Kizzy).

✨ Ağ geçidi dayanıklılık düzeltmeleri için [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy).

✨ UI bileşenleri için [Read You](https://github.com/Ashinch/ReadYou) ve [Seal](https://github.com/JunkFood02/Seal).

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ Nintendo ve Wii U oyun verileri için [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U).

✨ Günlükler arayüzü için [Logra](https://github.com/wingio/Logra).

✨ Xbox oyun verileri için [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord).

✨ Material3 paletleri için [Monet](https://github.com/Kyant0/Monet).

## Lisans
**Kizzy**, kaynak kodunu ücretsiz olarak kullanmanıza, referans göstermenize ve değiştirmenize izin veren ancak değiştirilmiş ve türetilmiş kodun kapalı kaynaklı ticari yazılım olarak dağıtılmasına ve satılmasına izin vermeyen GNU GPL 3.0 Açık Kaynak Lisansı altında lisanslanmıştır.
