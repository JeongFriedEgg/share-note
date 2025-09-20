#!/bin/bash

# ShareNote Data Redistribution - Infrastructure Stop Script

echo "🛑 Stopping ShareNote Data Redistribution Infrastructure..."

# Docker Compose 파일 경로
COMPOSE_FILE="docker-compose.yml"

if [ -f "$COMPOSE_FILE" ]; then
    echo "📦 Stopping all services..."
    docker-compose down

    echo "✅ All services stopped successfully!"
else
    echo "❌ Error: docker-compose.yml not found!"
    exit 1
fi