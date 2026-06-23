#!/bin/bash
# ============================================================
# start.sh — Démarrage séquentiel pour Render.com
# Lance : orbd → PDFWorkerServer → PDFHttpGateway
# ============================================================
set -e

LIBS="classes:libs/*"

echo "🔵 [1/3] Démarrage du Name Service CORBA (orbd)..."
orbd -ORBInitialPort 1050 -port 1050 &
ORBD_PID=$!
sleep 4
echo "   orbd PID=$ORBD_PID ✅"

echo "🔵 [2/3] Démarrage du PDFWorkerServer CORBA..."
java -cp "$LIBS" \
  -Dorg.omg.CORBA.ORBInitialHost=localhost \
  -Dorg.omg.CORBA.ORBInitialPort=1050 \
  PDFWorkerServer &
WORKER_PID=$!
sleep 6
echo "   Worker PID=$WORKER_PID ✅"

echo "🔵 [3/3] Démarrage de la Gateway HTTP (port $PORT)..."
exec java -cp "$LIBS" \
  -Dorg.omg.CORBA.ORBInitialHost=localhost \
  -Dorg.omg.CORBA.ORBInitialPort=1050 \
  PDFHttpGateway
