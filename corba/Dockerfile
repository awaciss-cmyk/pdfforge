# ── Dockerfile (Gateway HTTP) ──────────────────────────────────────────
# Service public : PDFHttpGateway   (port 8080 → Render)
# ──────────────────────────────────────────────────────────────────────

FROM eclipse-temurin:11-jdk

WORKDIR /app

# Polices Unicode
RUN apt-get update && apt-get install -y \
    fonts-dejavu-core \
    fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

# Dépendances Java
RUN mkdir -p libs && \
    curl -sL https://archive.apache.org/dist/pdfbox/2.0.31/pdfbox-app-2.0.31.jar \
         -o libs/pdfbox-app-2.0.31.jar && \
    curl -sL https://repo1.maven.org/maven2/commons-fileupload/commons-fileupload/1.5/commons-fileupload-1.5.jar \
         -o libs/commons-fileupload-1.5.jar && \
    curl -sL https://repo1.maven.org/maven2/commons-io/commons-io/2.15.1/commons-io-2.15.1.jar \
         -o libs/commons-io-2.15.1.jar && \
    curl -sL https://repo1.maven.org/maven2/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar \
         -o libs/javax.servlet-api-3.1.0.jar

# Copier sources + UI
COPY idl/ idl/
COPY src/ src/
COPY static/ static/

# Générer les stubs CORBA + compiler
RUN idlj -fall -td src idl/PDFService.idl && \
    javac -cp "libs/*" \
      src/PDFForge/*.java \
      src/server/PDFHttpGateway.java \
      src/MultipartData.java \
      -d classes

RUN mkdir -p outputs

# Variables d'environnement (à setter dans Render dashboard)
ENV PORT=8080 \
    CORBA_NS_HOST=localhost \
    CORBA_NS_PORT=1050

EXPOSE 8080

CMD ["java", "-cp", "classes:libs/*", "PDFHttpGateway"]
