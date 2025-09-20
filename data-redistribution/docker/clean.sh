#!/bin/bash

# ShareNote Data Redistribution - Infrastructure Clean Script

echo "🧹 Cleaning ShareNote Data Redistribution Infrastructure..."

# Docker Compose 파일 경로
COMPOSE_FILE="docker-compose.yml"

if [ -f "$COMPOSE_FILE" ]; then
    echo "📦 Stopping and removing all services..."
    docker-compose down -v

    echo "🗑️  Removing unused volumes and networks..."
    docker volume prune -f
    docker network prune -f

    echo "✅ Infrastructure cleaned successfully!"
    echo "⚠️  Note: All data has been removed. Run start.sh to recreate the infrastructure."
else
    echo "❌ Error: docker-compose.yml not found!"
    exit 1
fi