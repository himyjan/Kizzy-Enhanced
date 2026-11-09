<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>Un fork avanzato di <a href="https://github.com/dead8309/Kizzy">Kizzy</a> — un gestore Discord Rich Presence per Android, scritto in Kotlin.</h4>
</div>

<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-27-%23?&style=flat-square&color=5b5ef7">
<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">
<img src="https://img.shields.io/badge/package-com.my.kizzy.enhanced-5b5ef7?style=flat-square">
<img src="https://img.shields.io/badge/name-Kizzy%20Enhanced-5b5ef7?style=flat-square">
<div align="center">

[![README in English](https://img.shields.io/badge/README-in%20English-blue?style=for-the-badge)](README.md)
[![README in italiano](https://img.shields.io/badge/README-in%20italiano-blue?style=for-the-badge)](README_IT.md)

</div>
</div>
<p>
<img src="https://github.com/user-attachments/assets/f8402300-d628-4104-8f86-dc6021c9fbbe" width=60%/>
</p>
</div>

> [!NOTE]
> **Kizzy Enhanced** è un fork personale di [Kizzy](https://github.com/dead8309/Kizzy) creato da dead8309. Le aggiunte specifiche del fork sono contrassegnate con ⭐ di seguito.

## Funzionalità

- [x] Pulsanti cliccabili
- [x] Rileva l'app attualmente in esecuzione
- [x] Rileva i media attualmente in riproduzione
- [x] Timestamp opzionali
- [x] Stato personalizzato
- [x] Salva/carica configurazioni di presenza
- [x] Tema Material You
- [x] Traduzioni
- [x] Configurazione semplice
- [x] Spoofing Console RPC
- [x] Crea configurazioni personalizzate con le tue immagini e link
- [x] Anteprima RPC nell'app stessa
- [x] Esecuzione in background anche a schermo spento
- [x] Supporto GIF
- [x] Supporto URL esterni (puoi fornire un URL che punta a un'immagine sul web e Discord la mostrerà!)
- [x] Usa immagini dalla galleria
- [x] ⭐ Spoofer di piattaforma (Desktop / Web / Android / iOS / Xbox / Playstation / VR)
- [x] ⭐ ID Applicazione personalizzato — usa la tua icona e il tuo nome per l'app Discord
- [x] ⭐ Placeholder di template aggiuntivi (`{{media_album}}`, `{{battery}}`, `{{playback}}` e altro)
- [x] ⭐ Selettore modalità timestamp (Predefinito Media/App · Ora corrente del giorno · ms personalizzati)
- [x] ⭐ Ridisegno della schermata principale — schede a larghezza intera con interruttori di attivazione/disattivazione
- [x] ⭐ Riconnessione automatica del gateway con backoff esponenziale (niente più interruzioni dopo 1-2 ore)
- [x] ⭐ Ciclo di rilevamento indistruttibile — gli errori di rete non interrompono più il rilevamento di app/giochi
- [x] ⭐ Polling del cambio app ogni 2 secondi (anziché 5 s) per un rilevamento dei giochi più rapido

## Novità di questo fork

Tutte le aggiunte si trovano nella schermata **Experimental RPC** e nella schermata principale.

### ⭐ Spoofer di piattaforma
- Scegli da quale piattaforma si connette la tua presenza — **Desktop, Web, Android, iOS, Xbox, Playstation, VR** (portato dai platformSpoofer di Equicord).
- Funziona a livello di `IDENTIFY` del gateway (stato del client), quindi viene correttamente rilevato da plugin come **platformindicators**.
- Si applica **dal vivo** — il servizio si riavvia da solo, senza bisogno di attivazioni manuali.

### ⭐ ID Applicazione personalizzato (icona propria)
- Selettore **Built-in / Custom**. Built-in mantiene l'icona predefinita; Custom rivela un campo in cui incollare il tuo **Application ID** di Discord, in modo che l'attività mostri **la tua** icona e il **tuo** nome.

### ⭐ Placeholder di template aggiuntivi
Oltre a quelli originali, con completamento automatico:
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — percentuale batteria
- `{{playback}}` — stato di riproduzione (▶ / ⏸)

### ⭐ Selettore modalità timestamp
- **Media / App (predefinito)** — timestamp dal brano/app corrente.
- **Come ora corrente del giorno** — Discord mostra l'ora corrente come trascorsa (es. alle 15:30 → mostra "15:30 trascorse"). Stessa logica del customRPC di Vencord.
- **Personalizzato** — digita manualmente i **Timestamp di inizio / fine (in millisecondi)**.

### ⭐ Ridisegno della schermata principale
- Le schede delle funzioni sono ora una pila verticale di schede a larghezza intera.
- **Interruttori di attivazione/disattivazione su ogni scheda**, così Media / Custom / Experimental RPC possono essere avviati e fermati direttamente dalla schermata principale.

### ⭐ Resilienza del Gateway (portata da [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy))
- Riconnessione automatica con backoff esponenziale in caso di disconnessioni impreviste.
- Ripristina la sessione quando possibile; torna a un nuovo identify dopo 3 tentativi falliti.
- I codici di chiusura fatali (4004, 4010–4014) interrompono ancora permanentemente.
- Limite di 60 secondi sulle attese di aggiornamento della presenza (era un ciclo infinito).
- Il ciclo di rilevamento è ora racchiuso in un blocco try/catch — un singolo errore di rete non interrompe più il rilevamento del cambio app fino a un riavvio manuale.
- Polling cambio app: **2 s** (era 5 s).
- Correzione: il passaggio tra due app abilitate ora aggiorna immediatamente la presenza.

### Correzioni
- Risolto crash durante la **"Condivisione log"** (l'autorità del FileProvider ora segue l'ID del pacchetto).
- Risolti crash durante il cambio delle funzioni RPC dalla schermata principale.

## Requisiti di sistema
- Sistema operativo: Android da 8.1 a 16
- RAM: Minimo 3 GB

## Download
Ottieni l'ultimo file `app-release.apk` firmato dalla pagina [**Releases**](../../releases/latest).

**Kizzy Enhanced** utilizza l'ID pacchetto **`com.my.kizzy.enhanced`**, quindi si installa affiancato al Kizzy originale se lo possiedi.

> [!WARNING]
> Questa app utilizza la connessione al gateway di Discord. Usala a tuo rischio e pericolo.

## Compilazione
> Prerequisiti: Android Studio e familiarità con Gradle, Kotlin e Jetpack Compose.

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
Apri il progetto in Android Studio, importalo, quindi compila ed esegui (Build and Run). Le build di rilascio vengono prodotte automaticamente dal flusso di lavoro GitHub Actions **Build Release Apk**.

## Crediti
✨ [Kizzy](https://github.com/dead8309/Kizzy) per il progetto Kizzy originale.

✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) per le correzioni di resilienza del gateway.

✨ [Read You](https://github.com/Ashinch/ReadYou) e [Seal](https://github.com/JunkFood02/Seal) per i componenti UI.

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) per i dati dei giochi Nintendo e Wii U.

✨ [Logra](https://github.com/wingio/Logra) per l'interfaccia dei log.

✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) per i dati dei giochi Xbox.

✨ [Monet](https://github.com/Kyant0/Monet) per le palette Material3.

## Licenza
**Kizzy** è un progetto open source con licenza GNU GPL 3.0, che ti consente di utilizzare, fare riferimento e modificare il codice sorgente gratuitamente, ma non consente di distribuire e vendere il codice modificato e derivato come software commerciale closed-source.
