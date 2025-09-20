#!/bin/bash

# ShareNote Data Redistribution - Infrastructure Start Script

echo "🚀 Starting ShareNote Data Redistribution Infrastructure..."

# Docker Compose 파일 경로
COMPOSE_FILE="docker-compose.yml"

# Docker Compose 실행
if [ -f "$COMPOSE_FILE" ]; then
    echo "🧹 Cleaning up existing containers and volumes..."
    docker-compose down -v 2>/dev/null || true
    docker system prune -f

    echo "📦 Starting PostgreSQL databases and Redis Sentinel cluster..."
    docker-compose up -d

    echo "⏳ Waiting for services to be ready..."
    sleep 10

    echo "🔍 Checking service health..."
    docker-compose ps

    echo ""
    echo "✅ Infrastructure started successfully!"
    echo ""
    echo "📊 Service Endpoints:"
    echo "  - PostgreSQL Legacy:  localhost:5432 (sharenote_legacy)"
    echo "  - PostgreSQL Shard1:  localhost:5433 (sharenote_shard1)"
    echo "  - PostgreSQL Shard2:  localhost:5434 (sharenote_shard2)"
    echo "  - Redis Master:       localhost:6379"
    echo "  - Redis Sentinel 1:   localhost:26379"
    echo "  - Redis Sentinel 2:   localhost:26380"
    echo "  - Redis Sentinel 3:   localhost:26381"
    echo ""
    echo "🔑 Credentials:"
    echo "  - PostgreSQL: username=sharenote_user, password=sharenote_password"
    echo "  - Redis: password=redis_password"
    echo ""
else
    echo "❌ Error: docker-compose.yml not found!"
    exit 1
fi