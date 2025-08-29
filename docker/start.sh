#!/bin/bash

clear

echo "프로젝트 디렉토리로 이동..."
cd ..

echo "기존 Docker 컨테이너 및 볼륨 삭제..."
docker-compose -f docker/docker-compose.yml down -v

echo "새로운 Docker 컨테이너 시작..."
docker-compose -f docker/docker-compose.yml up -d