#!/bin/bash
set -e

LIBS="classes:libs/*"

echo "🔵 [0/3] Nettoyage des anciens process..."
pkill -f "orbd" 2>/dev/null || true
pkill -f "PDFWorkerServer" 2>/dev/null || true
sleep 2

echo "🔵 [1/3] Démarrage du Name Service CORBA (orbd)..."
orbd -ORBInitialPort 1050 -port 1049 &
sleep 5

echo "🔵 [2/3] Démarrage du PDFWorkerServer CORBA..."
java -cp "$LIBS" PDFWorkerServer &
sleep 8

echo "🔵 [3/3] Démarrage de la Gateway HTTP (port $PORT)..."
exec java -cp "$LIBS" PDFHttpGateway
