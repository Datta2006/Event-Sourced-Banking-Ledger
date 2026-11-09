#!/bin/bash
# Run the frontend in development mode
# Usage: ./run-frontend.sh [--mock|--live]

set -e

cd "$(dirname "$0")/../.."

echo "🚀 Starting Meridian Banking Portal Frontend..."
echo ""

if [ "$1" = "--live" ]; then
  echo "🔗 Connecting to live backend at http://localhost:8080"
  echo "   (Make sure Docker Compose is running: docker compose up -d)"
  echo ""
  echo "📱 Access at: http://localhost:5173"
  echo ""
  cd frontend
  npm run dev
else
  echo "🧪 Running in MOCK mode (no backend required)"
  echo "   Pre-seeded demo accounts available"
  echo ""
  echo "📱 Access at: http://localhost:5173"
  echo ""
  echo "🔐 Login with demo credentials (see README.md)"
  echo ""
  cd frontend
  npm run dev
fi