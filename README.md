# Drivors TV

Écran d'accueil personnel pour la télé Google TV (Smart Technology STT-4380K).

- Barre du haut : profil, Rechercher, Accueil / Direct / Applis, Assistant, Maison, Paramètres, Réglages rapides, heure
- Grande bannière « À la une » qui tourne entre tes applis (◀ ▶ pour changer, OK pour ouvrir)
- Rangée « Sélection pour toi » (bannières des applis) et « Mes applis » (icônes rondes)
- Page « Applis » avec toutes les applis installées
- Appui long sur une appli : ouvre sa fiche (pour la désinstaller ou forcer l'arrêt)

## Installer sur la télé

Chaque envoi de code sur GitHub fabrique automatiquement `DrivorsTV.apk` (onglet Actions, puis Releases).

Dans l'app Downloader de la télé :
`github.com/daganyigite/DrivorsTV/releases/latest/download/DrivorsTV.apk`

## Sécurité

Les versions publiées sont signées avec une clé privée rangée dans les secrets GitHub du dépôt
(KEYSTORE_B64, KEYSTORE_PASSWORD). La clé TMDB est aussi un secret (TMDB_KEY).
