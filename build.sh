#!/bin/bash
# ============================================================
# build.sh  — Compile PDFForge CORBA
# ============================================================
set -e

LIBS="libs/*"
CLASSES="classes"
IDL="idl/PDFService.idl"

echo "📦 [1/4] Création des dossiers..."
mkdir -p $CLASSES libs outputs

echo "⬇️  [2/4] Téléchargement des dépendances..."
[ -f libs/pdfbox-app-2.0.31.jar ]       || curl -sL https://archive.apache.org/dist/pdfbox/2.0.31/pdfbox-app-2.0.31.jar -o libs/pdfbox-app-2.0.31.jar
[ -f libs/commons-fileupload-1.5.jar ]  || curl -sL https://repo1.maven.org/maven2/commons-fileupload/commons-fileupload/1.5/commons-fileupload-1.5.jar -o libs/commons-fileupload-1.5.jar
[ -f libs/commons-io-2.15.1.jar ]       || curl -sL https://repo1.maven.org/maven2/commons-io/commons-io/2.15.1/commons-io-2.15.1.jar -o libs/commons-io-2.15.1.jar
[ -f libs/javax.servlet-api-3.1.0.jar ] || curl -sL https://repo1.maven.org/maven2/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar -o libs/javax.servlet-api-3.1.0.jar

echo "🔧 [3/4] Génération des stubs CORBA (idlj)..."
idlj -fall -td src $IDL

echo "☕ [4/4] Compilation Java..."
# Tous les .java : stubs générés + src/corba + src/server + src/client + MultipartData
javac -cp "$LIBS" \
  src/PDFForge/*.java \
  src/corba/PDFWorkerImpl.java \
  src/corba/PDFWorkerServer.java \
  src/server/PDFHttpGateway.java \
  src/MultipartData.java \
  -d $CLASSES

echo ""
echo "✅ Build terminé !"
echo ""
echo "Lancer le Name Service :"
echo "  orbd -ORBInitialPort 1050 &"
echo ""
echo "Lancer le Worker CORBA :"
echo "  java -cp 'classes:libs/*' PDFWorkerServer"
echo ""
echo "Lancer la Gateway HTTP :"
echo "  java -cp 'classes:libs/*' PDFHttpGateway"
