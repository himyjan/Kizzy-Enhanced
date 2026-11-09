<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>Kizzy Mejorado — Un Discord Rich Presence manager escrito en Kotlin</h4>
</div>

<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-27-%23?&style=flat-square&color=5b5ef7">
<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">
<img src="https://img.shields.io/badge/package-com.my.kizzy.enhanced-5b5ef7?style=flat-square">
<img src="https://img.shields.io/badge/name-Kizzy%20Enhanced-5b5ef7?style=flat-square">
<div align="center">

[![README in English](https://img.shields.io/badge/README-in%20English-blue?style=for-the-badge)](README.md)
[![README en español](https://img.shields.io/badge/README-en%20espa%C3%B1ol-blue?style=for-the-badge)](README_ES.md)

</div>
</div>

> [!NOTE]
> **Kizzy Enhanced** es un fork personal de [Kizzy](https://github.com/dead8309/Kizzy) por dead8309. Las características propias del fork están marcadas con ⭐.

## Características

- [x] Botones clickeables
- [x] Detecta la aplicación en ejecución actual
- [x] Detecta los medios reproduciéndose actualmente
- [x] Marcas de tiempo opcionales
- [x] Estado personalizado
- [x] Guardar/cargar configuraciones de presencia
- [x] Tema Material You
- [x] Traducciones
- [x] Configuración sencilla
- [x] Emulación de Consola RPC
- [x] Crear configuraciones personalizadas con tus propias imágenes y enlaces
- [x] Vista previa de RPC en la propia app
- [x] Ejecutarse en segundo plano incluso con la pantalla apagada
- [x] Soporte para GIF
- [x] Soporte para URL externas
- [x] Usar imágenes de la galería
- [x] ⭐ Spoofer de plataforma (Escritorio / Web / Android / iOS / Xbox / Playstation / VR)
- [x] ⭐ ID de Aplicación Personalizada — usa tu propio ícono y nombre de aplicación de Discord
- [x] ⭐ Plantillas adicionales (`{{media_album}}`, `{{battery}}`, `{{playback}}` y más)
- [x] ⭐ Selector de modo de marca de tiempo
- [x] ⭐ Rediseño de pantalla de inicio
- [x] ⭐ Reconexión automática de gateway con retroceso exponencial
- [x] ⭐ Bucle de detección indestructible
- [x] ⭐ Encuesta de cambio de aplicación cada 2 segundos

## Novedades en este fork

Todas las adiciones están en la pantalla **Experimental RPC** y en la pantalla de inicio.

### ⭐ Spoofer de plataforma
- Elige desde qué plataforma se conecta tu presencia — **Escritorio, Web, Android, iOS, Xbox, Playstation, VR** (portado desde el platformSpoofer de Equicord).
- Funciona a nivel de IDENTIFY del gateway (estado del cliente), por lo que es correctamente detectado por plugins como **platformindicators**.
- Se aplica en vivo — el servicio se reinicia solo, no se necesita alternar manualmente.

### ⭐ ID de Aplicación Personalizada (ícono propio)
- Selector **Built-in / Custom**. Built-in mantiene el ícono predeterminado; Custom muestra un campo donde pegar tu propio **ID de Aplicación** de Discord, para que la actividad muestre **tu** ícono y nombre.

### ⭐ Plantillas adicionales
Además de las originales, con autocompletado:
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — porcentaje de batería
- `{{playback}}` — estado de reproducción (▶ / ⏸)

### ⭐ Selector de modo de marca de tiempo
- **Media / App (predeterminado)** — marcas de tiempo desde la pista/app actual.
- **Mismo que hora actual** — Discord muestra la hora actual como transcurrido (por ejemplo, a las 15:30 → muestra "15:30 transcurrido"). Misma lógica que customRPC de Vencord.
- **Personalizado** — escribe tu propio **Inicio / Fin (en milisegundos)**.

### ⭐ Rediseño de pantalla de inicio
- Las tarjetas de características son ahora una pila vertical de tarjetas de ancho completo.
- **Interruptores de encendido/apagado en cada tarjeta**, para que se puedan iniciar y detener Media / Custom / Experimental RPC directamente desde la pantalla de inicio.

### ⭐ Resiliencia de Gateway (portado desde [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy))
- Reconexión automática con retroceso exponencial ante cualquier desconexión inesperada.
- Reanuda la sesión cuando es posible; vuelve a hacer identify después de 3 intentos fallidos.
- Los códigos de cierre fatales (4004, 4010–4014) aún detienen permanentemente.
- Límite de 60 segundos en esperas de actualización de presencia (era un bucle infinito).
- El bucle de detección está envuelto en try/catch — un error de red ya no detiene la detección de aplicaciones hasta un reinicio manual.
- Encuesta de cambio de aplicación: **2 s** (era 5 s).
- Corrección: cambiar entre dos aplicaciones habilitadas ahora actualiza la presencia inmediatamente.

### Correcciones
- Corregido fallo al **"Compartir registro"** (FileProvider authority ahora sigue el ID del paquete).
- Corregidos fallos al cambiar funciones RPC desde la pantalla de inicio.

## Requisitos del sistema
- SO: Android 8.1 a 16
- RAM: 3 GB mínimo

## Descarga
Obtén el `app-release.apk` firmado más reciente desde la página [**Releases**](../../releases/latest).

**Kizzy Enhanced** usa el ID de paquete **`com.my.kizzy.enhanced`**, por lo que se instala junto con Kizzy original si lo tienes.

> [!WARNING]
> Esta aplicación usa la conexión a Discord Gateway. Úsala bajo tu propio riesgo.

## Construcción
> Requisitos previos: Android Studio, familiaridad con Gradle, Kotlin y Jetpack Compose.

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
Abre el proyecto en Android Studio, impórtalo, compila y ejecuta. Las compilaciones de lanzamiento son producidas automáticamente por el flujo de trabajo **Build Release Apk** de GitHub Actions.

## Créditos
✨ [Kizzy](https://github.com/dead8309/Kizzy) por el proyecto original de Kizzy.
✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) por las correcciones de resiliencia de gateway.
✨ [Read You](https://github.com/Ashinch/ReadYou) y [Seal](https://github.com/JunkFood02/Seal) para componentes de UI.
✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)
✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) para datos de juegos de Nintendo y Wii U.
✨ [Logra](https://github.com/wingio/Logra) para la UI de registros.
✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) para datos de juegos de Xbox.
✨ [Monet](https://github.com/Kyant0/Monet) para paletas Material3.

## Licencia
**Kizzy** es un proyecto de código abierto bajo la licencia GNU GPL 3.0, que te permite usar, referenciar y modificar el código fuente de forma gratuita, pero no permite que el código modificado y derivado se distribuya y venda como software comercial cerrado.
