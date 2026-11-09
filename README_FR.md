<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>Kizzy Amélioré — Un gestionnaire de présence enrichie Discord écrit en Kotlin</h4>
</div>

<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-27-%23?&style=flat-square&color=5b5ef7">
<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">
<img src="https://img.shields.io/badge/package-com.my.kizzy.enhanced-5b5ef7?style=flat-square">
<img src="https://img.shields.io/badge/name-Kizzy%20Enhanced-5b5ef7?style=flat-square">
<div align="center">

[![README in English](https://img.shields.io/badge/README-in%20English-blue?style=for-the-badge)](README.md)
[![README en français](https://img.shields.io/badge/README-en%20français-blue?style=for-the-badge)](README_FR.md)

</div>
</div>

> [!NOTE]
> **Kizzy Enhanced** est un fork personnel de [Kizzy](https://github.com/dead8309/Kizzy) par dead8309. Les fonctionnalités spécifiques au fork sont marquées avec ⭐.

## Fonctionnalités

- [x] Boutons cliquables
- [x] Détecte l'application en cours d'exécution
- [x] Détecte les médias en cours de lecture
- [x] Horodatages optionnels
- [x] Statut personnalisé
- [x] Enregistrer/charger les configurations de présence
- [x] Thème Material You
- [x] Traductions
- [x] Configuration simple
- [x] Émulation de console RPC
- [x] Créer des configurations personnalisées avec vos propres images et liens
- [x] Aperçu RPC dans l'application
- [x] Fonctionne en arrière-plan même avec l'écran éteint
- [x] Prise en charge des GIF
- [x] Prise en charge des URL externes
- [x] Utiliser des images depuis la galerie
- [x] ⭐ Spoofer de plateforme (Bureau / Web / Android / iOS / Xbox / Playstation / VR)
- [x] ⭐ ID d'application personnalisé — utiliser votre propre icône et nom d'application Discord
- [x] ⭐ Placeholders de modèle supplémentaires
- [x] ⭐ Sélecteur de mode d'horodatage
- [x] ⭐ Refonte de l'écran d'accueil
- [x] ⭐ Reconnexion automatique de la passerelle avec retrait exponentiel
- [x] ⭐ Boucle de détection indestructible
- [x] ⭐ Enquête de changement d'application toutes les 2 secondes

## Nouveautés de ce fork

Toutes les nouvelles fonctionnalités se trouvent sur l'écran **Experimental RPC** et sur l'écran d'accueil.

### ⭐ Spoofer de plateforme
- Choisissez depuis quelle plateforme votre présence se connecte — **Bureau, Web, Android, iOS, Xbox, Playstation, VR** (porté depuis le platformSpoofer d'Equicord).
- Fonctionne au niveau de l'identification du gateway (statut du client), il est donc correctement détecté par des plugins comme **platformindicators**.
- Appliqué en direct — le service redémarre tout seul, aucun basculement manuel nécessaire.

### ⭐ ID d'application personnalisé (icône propre)
- Sélecteur **Built-in / Custom**. Built-in garde l'icône par défaut ; Custom affiche un champ où coller votre propre **ID d'application** Discord, de sorte que l'activité affiche **votre** icône et nom.

### ⭐ Placeholders de modèle supplémentaires
En plus des originaux, avec autocomplétion :
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — pourcentage de batterie
- `{{playback}}` — état de lecture (▶ / ⏸)

### ⭐ Sélecteur de mode d'horodatage
- **Média / App (par défaut)** — horodatages depuis la piste/application actuelle.
- **Identique à l'heure actuelle** — Discord affiche l'heure actuelle comme temps écoulé (par exemple à 15:30 → affiche "15:30 écoulé"). Même logique que le customRPC de Vencord.
- **Personnalisé** — saisissez vos propres **horodatages de début / fin (en millisecondes)**.

### ⭐ Refonte de l'écran d'accueil
- Les cartes de fonctionnalités sont maintenant une pile verticale de cartes pleine largeur.
- **Interrupteurs marche/arrêt sur chaque carte**, pour que Media / Custom / Experimental RPC puissent être démarrés et arrêtés directement depuis l'écran d'accueil.

### ⭐ Résilience de la passerelle (porté depuis [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy))
- Reconnexion automatique avec retrait exponentiel en cas de déconnexion inattendue.
- Reprend la session quand c'est possible ; tente à nouveau l'identification après 3 échecs.
- Les codes de fermeture fatals (4004, 4010–4014) arrêtent toujours définitivement.
- Limite de 60 secondes sur l'attente de mise à jour de présence (était une boucle infinie).
- La boucle de détection est enveloppée dans try/catch — une erreur réseau n'arrête plus la détection d'applications jusqu'au redémarrage manuel.
- Enquête de changement d'application : **2 s** (était 5 s).
- Correction : passer d'une application activée à l'autre met maintenant à jour la présence immédiatement.

### Corrections
- Corrigé le crash lors du clic sur **"Partager les logs"** (l'autorité FileProvider suit maintenant l'ID du paquet).
- Corrigés les crashs lors du changement de fonctionnalités RPC depuis l'écran d'accueil.

## Configuration requise
- OS : Android 8.1 à 16
- RAM : 3 Go minimum

## Téléchargement
Téléchargez le `app-release.apk` signé le plus récent depuis la page [**Releases**](../../releases/latest).

**Kizzy Enhanced** utilise l'ID de paquet **`com.my.kizzy.enhanced`**, il s'installe donc en parallèle de Kizzy original si vous l'avez.

> [!WARNING]
> Cette application utilise la connexion à la passerelle Discord. Utilisez-la à vos propres risques.

## Construction
> Prérequis : Android Studio, et familiarité avec Gradle, Kotlin et Jetpack Compose.

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
Ouvrez le projet dans Android Studio, importez-le, puis construisez et exécutez. Les versions de release sont produites automatiquement par le flux de travail **Build Release Apk** de GitHub Actions.

## Crédits
✨ [Kizzy](https://github.com/dead8309/Kizzy) pour le projet Kizzy original.
✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) pour les corrections de résilience de la passerelle.
✨ [Read You](https://github.com/Ashinch/ReadYou) et [Seal](https://github.com/JunkFood02/Seal) pour les composants UI.
✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)
✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) pour les données de jeux Nintendo et Wii U.
✨ [Logra](https://github.com/wingio/Logra) pour l'UI des logs.
✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) pour les données de jeux Xbox.
✨ [Monet](https://github.com/Kyant0/Monet) pour les palettes Material3.

## Licence
**Kizzy** est un projet open source sous licence GNU GPL 3.0, qui vous permet d'utiliser, de référencer et de modifier le code source gratuitement, mais n'autorise pas la distribution et la vente du code modifié et dérivé en tant que logiciel commercial fermé.
