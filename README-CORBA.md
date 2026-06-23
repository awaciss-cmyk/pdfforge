# PDFForge — Version CORBA

## Architecture

```
Navigateur
    │  HTTP
    ▼
PDFHttpGateway  (port 8080 — service public Render)
    │  CORBA / IIOP
    ▼
[CORBA Name Service]  (orbd, port 1050)
    │
    ▼
PDFWorkerServer  (port 1060 — service interne)
    │
    ▼
PDFBox (traitement PDF réel)
```

**Composants :**

| Fichier | Rôle |
|---|---|
| `idl/PDFService.idl` | Contrat CORBA (interface partagée) |
| `src/corba/PDFWorkerImpl.java` | Servant CORBA — traite les PDF |
| `src/corba/PDFWorkerServer.java` | Lance le Worker et l'enregistre dans le Name Service |
| `src/server/PDFHttpGateway.java` | Reçoit les requêtes HTTP, délègue au Worker via CORBA |

---

## Test local avec Docker Compose

```bash
# Construire et lancer les 3 services
docker-compose up --build

# Ouvrir : http://localhost:8080
```

---

## Déploiement sur Render.com

### Problème fondamental avec Render

Render propose des **Web Services** isolés : chaque service a son propre conteneur.  
CORBA (IIOP) nécessite une communication réseau **privée** entre les services.  
Render ne fournit pas de réseau privé inter-services sur le plan gratuit.

### Solution recommandée : Monorepo avec les 2 processus dans 1 seul conteneur

C'est la solution la plus simple pour Render gratuit.  
On utilise un script `start.sh` qui lance d'abord `orbd`, puis le Worker, puis la Gateway dans le même conteneur.

#### `start.sh`
```bash
#!/bin/bash
set -e

# 1. Lancer le Name Service CORBA en arrière-plan
orbd -ORBInitialPort 1050 &
sleep 3

# 2. Lancer le Worker CORBA en arrière-plan
java -cp "classes:libs/*" PDFWorkerServer &
sleep 5

# 3. Lancer la Gateway HTTP au premier plan (Render surveille ce process)
exec java -cp "classes:libs/*" PDFHttpGateway
```

#### `Dockerfile.render` (tout-en-un pour Render)
```dockerfile
FROM eclipse-temurin:11-jdk
WORKDIR /app

RUN apt-get update && apt-get install -y fonts-dejavu-core fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p libs && \
    curl -sL https://archive.apache.org/dist/pdfbox/2.0.31/pdfbox-app-2.0.31.jar \
         -o libs/pdfbox-app-2.0.31.jar && \
    curl -sL https://repo1.maven.org/maven2/commons-fileupload/commons-fileupload/1.5/commons-fileupload-1.5.jar \
         -o libs/commons-fileupload-1.5.jar && \
    curl -sL https://repo1.maven.org/maven2/commons-io/commons-io/2.15.1/commons-io-2.15.1.jar \
         -o libs/commons-io-2.15.1.jar && \
    curl -sL https://repo1.maven.org/maven2/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar \
         -o libs/javax.servlet-api-3.1.0.jar

COPY idl/ idl/
COPY src/ src/
COPY static/ static/
COPY start.sh .

RUN idlj -fall -td src idl/PDFService.idl && \
    javac -cp "libs/*" \
      src/PDFForge/*.java \
      src/corba/PDFWorkerImpl.java \
      src/corba/PDFWorkerServer.java \
      src/server/PDFHttpGateway.java \
      src/MultipartData.java \
      -d classes

RUN mkdir -p outputs && chmod +x start.sh

ENV PORT=8080 \
    CORBA_NS_HOST=localhost \
    CORBA_NS_PORT=1050

EXPOSE 8080
CMD ["./start.sh"]
```

### Étapes Render.com

1. Pusher le repo sur GitHub
2. Render → **New** → **Web Service**
3. Connecter le repo
4. **Docker** détecté automatiquement (choisir `Dockerfile.render`)
5. Variables d'environnement à ajouter dans Render Dashboard :
   - `PORT` = `10000` (Render impose ce port)
   - `CORBA_NS_HOST` = `localhost`
   - `CORBA_NS_PORT` = `1050`
6. Cliquer **Deploy**

---

## Structure des fichiers

```
pdfforge-corba/
├── idl/
│   └── PDFService.idl          # Interface CORBA
├── src/
│   ├── corba/
│   │   ├── PDFWorkerImpl.java  # Servant (traitement PDF)
│   │   └── PDFWorkerServer.java
│   ├── server/
│   │   └── PDFHttpGateway.java # Passerelle HTTP → CORBA
│   └── MultipartData.java      # (copié depuis le projet original)
├── static/
│   └── index.html              # Frontend (inchangé)
├── Dockerfile                  # Gateway seule
├── Dockerfile.worker           # Worker seul
├── Dockerfile.render           # Tout-en-un pour Render
├── docker-compose.yml          # Test local (3 conteneurs)
├── build.sh                    # Build local sans Docker
└── start.sh                    # Script de démarrage Render
```
