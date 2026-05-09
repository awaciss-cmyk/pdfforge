# PDFForge

Application web de traitement PDF — 8 outils en ligne.

## Fonctionnalités

- 🔗 Fusion de PDF
- ✂️ Découpage de PDF
- 📄 Extraction de pages
- 🗑️ Suppression de pages
- 🔐 Protection par mot de passe
- 🖼️ Conversion PDF → Images
- 📝 Extraction de texte
- ✨ Création de PDF

## Déploiement sur Render

1. Pusher ce dossier sur GitHub
2. Aller sur [render.com](https://render.com)
3. New → Web Service → connecter le repo GitHub
4. Render détecte automatiquement le Dockerfile
5. Cliquer Deploy

## Lancement local

```bash
# Compiler
javac -cp "libs/*" src/*.java -d classes

# Lancer
java -cp "classes:libs/*" PDFApp
```

Ouvrir : http://localhost:8080
