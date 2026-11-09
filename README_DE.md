<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>Ein erweiterter Fork von <a href="https://github.com/dead8309/Kizzy">Kizzy</a> — ein Discord Rich Presence Manager für Android, geschrieben in Kotlin.</h4>
</div>

<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-27-%23?&style=flat-square&color=5b5ef7">
<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">
<img src="https://img.shields.io/badge/package-com.my.kizzy.enhanced-5b5ef7?style=flat-square">
<img src="https://img.shields.io/badge/name-Kizzy%20Enhanced-5b5ef7?style=flat-square">
<div align="center">

[![README in English](https://img.shields.io/badge/README-in%20English-blue?style=for-the-badge)](README.md)
[![README auf Deutsch](https://img.shields.io/badge/README-auf%20Deutsch-blue?style=for-the-badge)](README_DE.md)

</div>
</div>
<p>
<img src="https://github.com/user-attachments/assets/f8402300-d628-4104-8f86-dc6021c9fbbe" width=60%/>
</p>
</div>

> [!NOTE]
> **Kizzy Enhanced** ist ein persönlicher Fork von [Kizzy](https://github.com/dead8309/Kizzy) von dead8309. Fork-spezifische Ergänzungen sind unten mit ⭐ markiert.

## Funktionen

- [x] Anklickbare Buttons
- [x] Erkennt aktuell laufende Apps
- [x] Erkennt aktuell abgespielte Medien
- [x] Optionale Zeitstempel
- [x] Benutzerdefinierter Status
- [x] Präsenz-Konfigurationen speichern/laden
- [x] Material You Theme
- [x] Übersetzungen
- [x] Einfache Einrichtung
- [x] Konsolen-RPC-Spoof
- [x] Erstellen Sie benutzerdefinierte Konfigurationen mit Ihren eigenen Bildern und Links
- [x] RPC in der App selbst vorschauen
- [x] Läuft im Hintergrund, auch wenn der Bildschirm ausgeschaltet ist
- [x] GIF-Unterstützung
- [x] Externe URL-Unterstützung (d. h. Sie können eine URL angeben, die auf ein Bild im Web verweist, und Discord zeigt es an!)
- [x] Bilder aus der Galerie verwenden
- [x] ⭐ Plattform-Spoofer (Desktop / Web / Android / iOS / Xbox / Playstation / VR)
- [x] ⭐ Benutzerdefinierte Application ID — verwenden Sie Ihr eigenes Discord-App-Symbol und Ihren eigenen Namen
- [x] ⭐ Zusätzliche Template-Platzhalter (`{{media_album}}`, `{{battery}}`, `{{playback}}` und mehr)
- [x] ⭐ Zeitstempel-Modus-Auswahl (Standard Medien/App · Aktuelle Tageszeit · Benutzerdefinierte ms)
- [x] ⭐ Startbildschirm-Neugestaltung — Karten in voller Breite mit Ein/Aus-Schaltern
- [x] ⭐ Gateway-Wiederverbindung mit exponentiellem Backoff (stirbt nicht mehr nach 1–2 Stunden)
- [x] ⭐ Unzerstörbare Erkennungsschleife — Netzwerkfehler stoppen die App/Spiel-Erkennung nicht mehr
- [x] ⭐ 2-Sekunden-App-Wechsel-Abfrage (war 5 s) für schnellere Spielerkennung

## Was ist neu in diesem Fork

Alle Ergänzungen befinden sich im **Experimental RPC**-Bildschirm und auf dem Startbildschirm.

### ⭐ Plattform-Spoofer
- Wählen Sie aus, von welcher Plattform Ihre Präsenz verbunden wird — **Desktop, Web, Android, iOS, Xbox, Playstation, VR** (portiert von den Plattformen von Equicord's platformSpoofer).
- Funktioniert auf Gateway-`IDENTIFY`-Ebene (Client-Status) und wird daher von Plugins wie **platformindicators** korrekt erkannt.
- Wird **live** angewendet — der Dienst startet sich selbst neu, kein manuelles Umschalten erforderlich.

### ⭐ Benutzerdefinierte Application ID (eigenes Symbol)
- Auswahl **Built-in / Custom**. Built-in behält das Standardsymbol bei; Custom enthüllt ein Feld, in das Sie Ihre eigene Discord **Application ID** einfügen, sodass die Aktivität **Ihr** Symbol und Ihren Namen anzeigt.

### ⭐ Zusätzliche Template-Platzhalter
Zusätzlich zu den Originalen, mit Autovervollständigung:
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — Akkustand
- `{{playback}}` — Wiedergabestatus (▶ / ⏸)

### ⭐ Zeitstempel-Modus-Auswahl
- **Medien / App (Standard)** — Zeitstempel vom aktuellen Titel/der aktuellen App.
- **Wie aktuelle Tageszeit** — Discord zeigt die aktuelle Zeit als verstrichen an (z. B. um 15:30 Uhr → zeigt "15:30 verstrichen"). Gleiche Logik wie Vencords customRPC.
- **Benutzerdefiniert** — Geben Sie rohe **Start- / End-Zeitstempel (in Millisekunden)** selbst ein.

### ⭐ Startbildschirm-Neugestaltung
- Funktionskarten sind jetzt ein vertikaler Stapel von Karten in voller Breite.
- **Ein/Aus-Umschalter auf jeder Karte**, sodass Medien / Custom / Experimental RPC direkt vom Startbildschirm aus gestartet und gestoppt werden können.

### ⭐ Gateway-Resilienz (portiert von [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy))
- Automatische Wiederverbindung mit exponentiellem Backoff bei jeder unerwarteten Trennung.
- Setzt die Sitzung fort, wenn möglich; greift nach 3 Fehlversuchen auf ein neues Identify zurück.
- Fatale Schließungscodes (4004, 4010–4014) stoppen weiterhin dauerhaft.
- 60-Sekunden-Limit für Präsenzaktualisierungswartezeiten (war eine Endlosschleife).
- Die Erkennungsschleife ist jetzt in try/catch eingepackt — ein einzelner Netzwerkfehler stoppt die App-Wechsel-Erkennung nicht mehr bis zum manuellen Neustart.
- App-Wechsel-Abfrage: **2 s** (war 5 s).
- Behoben: Das Wechseln zwischen zwei aktivierten Apps aktualisiert die Präsenz nun sofort.

### Korrekturen
- Absturz beim **"Log teilen"** behoben (FileProvider-Authority folgt nun der Paket-ID).
- Abstürze beim Umschalten von RPC-Funktionen vom Startbildschirm aus behoben.

## Systemanforderungen
- Betriebssystem: Android 8.1 bis 16
- RAM: Mindestens 3 GB

## Download
Laden Sie die neueste signierte `app-release.apk` von der [**Releases**](../../releases/latest)-Seite herunter.

**Kizzy Enhanced** verwendet die Paket-ID **`com.my.kizzy.enhanced`** und wird daher neben dem originalen Kizzy installiert, falls Sie dieses besitzen.

> [!WARNING]
> Diese App verwendet die Discord-Gateway-Verbindung. Die Nutzung erfolgt auf eigene Gefahr.

## Build
> Voraussetzungen: Android Studio und Vertrautheit mit Gradle, Kotlin und Jetpack Compose.

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
Öffnen Sie das Projekt in Android Studio, importieren Sie es, erstellen und starten Sie es. Release-Builds werden automatisch durch den GitHub Actions-Workflow **Build Release Apk** erstellt.

## Credits
✨ [Kizzy](https://github.com/dead8309/Kizzy) für das ursprüngliche Kizzy-Projekt.

✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) für Gateway-Resilienz-Korrekturen.

✨ [Read You](https://github.com/Ashinch/ReadYou) und [Seal](https://github.com/JunkFood02/Seal) für UI-Komponenten.

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) für Nintendo- und Wii U-Spieldaten.

✨ [Logra](https://github.com/wingio/Logra) für die Protokoll-UI.

✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) für Xbox-Spieldaten.

✨ [Monet](https://github.com/Kyant0/Monet) für Material3-Paletten.

## Lizenz
**Kizzy** ist ein Open-Source-Projekt unter der GNU GPL 3.0 Open Source Lizenz, die es Ihnen erlaubt, den Quellcode kostenlos zu nutzen, zu referenzieren und zu ändern, aber nicht erlaubt, den modifizierten und abgeleiteten Code als geschlossene kommerzielle Software zu vertreiben und zu verkaufen.
