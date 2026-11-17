# Rano & Repo

Appli Android (Kotlin + Jetpack Compose) : chronomètre de repos (RepoMetre) et suivi de l'eau bue.

- Package : mg.sitraka.ranorepo
- Projet Firebase : ranorepo-sitraka
- Connexion Google + synchronisation Firestore (users/{uid}/repos et users/{uid}/waters)

## Compiler sur GitHub
1. Envoyez tout le contenu de ce dossier sur votre dépôt (branche main).
2. Onglet Actions > "Build APK Rano & Repo" > Run workflow (ou automatique à chaque push).
3. Téléchargez l'APK dans Artifacts > RanoRepo-apk.

## Empreinte SHA du keystore fixe (pour le Google login)
SHA-1   : C8:E7:33:87:C4:74:DC:21:D5:BC:C1:EF:60:FA:D8:2C:CF:E1:CE:5C
SHA-256 : BA:E4:13:34:67:3E:78:56:94:94:F6:7F:EC:91:F5:EC:6E:48:24:D4:1C:27:A5:AC:70:CF:A6:7C:17:41:0F:B8

Firebase > Paramètres du projet > Vos applications > Ajouter une empreinte :
ajoutez le SHA-1 ET le SHA-256, puis re-téléchargez google-services.json et remplacez app/google-services.json.

## Règles Firestore
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
