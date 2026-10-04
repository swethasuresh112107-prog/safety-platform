#!/bin/bash
echo "================================================"
echo "   GuardianVoice - One Click Launcher"
echo "================================================"
echo ""
echo "[1/3] Checking prerequisites..."
echo ""

if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed."
    echo "Please install Java 17 or higher from https://adoptium.net"
    read -p "Press Enter to exit..."
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed."
    echo "Please install Maven (e.g. 'brew install maven' on Mac, or 'sudo apt install maven' on Linux)"
    read -p "Press Enter to exit..."
    exit 1
fi

echo "Java and Maven found. Good to go!"
echo "(No MySQL needed - this uses a built-in H2 database automatically)"
echo ""
echo "[2/3] Starting GuardianVoice backend server..."
echo ""

cd "$(dirname "$0")/backend"

# Start backend in background
mvn spring-boot:run &
BACKEND_PID=$!

echo ""
echo "[3/3] Waiting for server to start, then opening browser..."
sleep 25

URL="http://localhost:8080/login.html"

if command -v xdg-open &> /dev/null; then
    xdg-open "$URL"
elif command -v open &> /dev/null; then
    open "$URL"
else
    echo "Please open your browser and go to: $URL"
fi

echo ""
echo "================================================"
echo " GuardianVoice is starting!"
echo " If the browser page does not load, wait a few"
echo " more seconds and refresh."
echo " Press Ctrl+C in this window to stop the server."
echo "================================================"

wait $BACKEND_PID
