#!/bin/bash

# 테스트 환경 설정 스크립트
# Docker 컨테이너 실행 및 초기 설정

# 색깔 출력을 위한 변수
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# 로그 함수들
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_header() {
    echo -e "\n${PURPLE}============================================${NC}"
    echo -e "${PURPLE} $1${NC}"
    echo -e "${PURPLE}============================================${NC}\n"
}

# Docker 및 Docker Compose 설치 확인 함수
check_docker() {
    log_header "Docker 환경 확인"

    if ! command -v docker &> /dev/null; then
        log_error "Docker가 설치되어 있지 않습니다. Docker를 먼저 설치해주세요."
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose가 설치되어 있지 않습니다. Docker Compose를 먼저 설치해주세요."
        exit 1
    fi

    # Docker 데몬 실행 확인
    if ! docker ps &> /dev/null; then
        log_error "Docker 데몬이 실행되고 있지 않습니다. Docker를 시작해주세요."
        exit 1
    fi

    log_success "Docker 환경 확인 완료"
}

# 필요한 도구 설치 확인 함수
check_required_tools() {
    log_header "필수 도구 확인"

    local missing_tools=()

    # PostgreSQL 클라이언트 확인
    if ! command -v psql &> /dev/null; then
        missing_tools+=("postgresql-client")
    fi

    # Redis 클라이언트 확인
    if ! command -v redis-cli &> /dev/null; then
        missing_tools+=("redis-tools")
    fi

    if [ ${#missing_tools[@]} -gt 0 ]; then
        log_warning "다음 도구들이 설치되어 있지 않습니다: ${missing_tools[*]}"
        log_info "Ubuntu/Debian에서 설치하려면: sudo apt-get install ${missing_tools[*]}"
        log_info "macOS에서 설치하려면: brew install postgresql redis"
        exit 1
    fi

    log_success "모든 필수 도구가 설치되어 있습니다"
}

# 디렉토리 구조 생성 함수
create_directory_structure() {
    log_header "프로젝트 디렉토리 구조 생성"

    # sql 디렉토리 생성
    mkdir -p sql

    # 실행 권한 부여
    chmod +x *.sh 2>/dev/null || true

    log_success "디렉토리 구조 생성 완료"
}

# Docker 컨테이너 시작 함수
start_containers() {
    log_header "Docker 컨테이너 시작"

    log_info "기존 컨테이너 중지 및 제거 중..."
    docker-compose down -v 2>/dev/null || true

    log_info "새로운 컨테이너 시작 중..."
    if docker-compose up -d; then
        log_success "Docker 컨테이너 시작 완료"
    else
        log_error "Docker 컨테이너 시작 실패"
        exit 1
    fi
}

# 컨테이너 상태 확인 함수
wait_for_containers() {
    log_header "컨테이너 상태 확인 및 대기"

    local max_attempts=60  # 최대 5분 대기
    local attempt=0

    log_info "데이터베이스 컨테이너가 준비될 때까지 대기 중..."

    while [ $attempt -lt $max_attempts ]; do
        local ready_count=0

        # Legacy DB 확인
        if docker-compose exec -T sharenote-legacy-db pg_isready -U postgre_user -d sharenote_legacy &>/dev/null; then
            ((ready_count++))
        fi

        # Shard1 DB 확인
        if docker-compose exec -T sharenote-shard1-db pg_isready -U postgre_user -d sharenote_shard1 &>/dev/null; then
            ((ready_count++))
        fi

        # Shard2 DB 확인
        if docker-compose exec -T sharenote-shard2-db pg_isready -U postgre_user -d sharenote_shard2 &>/dev/null; then
            ((ready_count++))
        fi

        # Redis 확인
        if docker-compose exec -T sharenote-redis redis-cli ping | grep -q PONG; then
            ((ready_count++))
        fi

        if [ $ready_count -eq 4 ]; then
            log_success "모든 컨테이너가 준비되었습니다"
            return 0
        fi

        echo -n "."
        sleep 5
        ((attempt++))
    done

    echo ""
    log_error "컨테이너 준비 시간이 초과되었습니다"
    return 1
}

# 컨테이너 상태 출력 함수
show_container_status() {
    log_header "컨테이너 상태 정보"

    echo "실행 중인 컨테이너:"
    docker-compose ps

    echo ""
    echo "포트 매핑 정보:"
    echo "  - Legacy DB:   localhost:5432"
    echo "  - Shard1 DB:   localhost:5433"
    echo "  - Shard2 DB:   localhost:5434"
    echo "  - Redis:       localhost:6379"
}

# 테스트 연결 함수
test_connections() {
    log_header "연결 테스트"

    local DB_USER="postgre_user"
    local DB_PASSWORD="postgre_password"

    # Legacy DB 연결 테스트
    if PGPASSWORD=$DB_PASSWORD psql -h localhost -p 5432 -U $DB_USER -d sharenote_legacy -c "SELECT version();" &>/dev/null; then
        log_success "Legacy DB 연결 성공"
    else
        log_error "Legacy DB 연결 실패"
        return 1
    fi

    # Shard1 DB 연결 테스트
    if PGPASSWORD=$DB_PASSWORD psql -h localhost -p 5433 -U $DB_USER -d sharenote_shard1 -c "SELECT version();" &>/dev/null; then
        log_success "Shard1 DB 연결 성공"
    else
        log_error "Shard1 DB 연결 실패"
        return 1
    fi

    # Shard2 DB 연결 테스트
    if PGPASSWORD=$DB_PASSWORD psql -h localhost -p 5434 -U $DB_USER -d sharenote_shard2 -c "SELECT version();" &>/dev/null; then
        log_success "Shard2 DB 연결 성공"
    else
        log_error "Shard2 DB 연결 실패"
        return 1
    fi

    # Redis 연결 테스트
    if redis-cli -p 6379 ping | grep -q PONG; then
        log_success "Redis 연결 성공"
    else
        log_error "Redis 연결 실패"
        return 1
    fi

    log_success "모든 서비스 연결 테스트 통과"
}

# 사용법 출력 함수
show_usage() {
    log_header "다음 단계"

    echo "테스트 환경이 성공적으로 설정되었습니다!"
    echo ""
    echo "마이그레이션 테스트를 실행하려면:"
    echo "  ./migration-test.sh"
    echo ""
    echo "개별 작업을 수행하려면:"
    echo "  # Legacy DB에 접속"
    echo "  PGPASSWORD=postgre_password psql -h localhost -p 5432 -U postgre_user -d sharenote_legacy"
    echo ""
    echo "  # Shard1 DB에 접속"
    echo "  PGPASSWORD=postgre_password psql -h localhost -p 5433 -U postgre_user -d sharenote_shard1"
    echo ""
    echo "  # Shard2 DB에 접속"
    echo "  PGPASSWORD=postgre_password psql -h localhost -p 5434 -U postgre_user -d sharenote_shard2"
    echo ""
    echo "  # Redis에 접속"
    echo "  redis-cli -p 6379"
    echo ""
    echo "테스트 환경을 중지하려면:"
    echo "  docker-compose down"
    echo ""
    echo "테스트 환경을 완전히 제거하려면:"
    echo "  docker-compose down -v"
}

# 컨테이너 로그 확인 함수
check_container_logs() {
    log_header "컨테이너 로그 확인"

    echo "각 컨테이너의 최근 로그:"

    echo ""
    echo "=== Legacy DB 로그 ==="
    docker-compose logs --tail=5 sharenote-legacy-db

    echo ""
    echo "=== Shard1 DB 로그 ==="
    docker-compose logs --tail=5 sharenote-shard1-db

    echo ""
    echo "=== Shard2 DB 로그 ==="
    docker-compose logs --tail=5 sharenote-shard2-db

    echo ""
    echo "=== Redis 로그 ==="
    docker-compose logs --tail=5 sharenote-redis
}

# 메인 실행 함수
main() {
    log_header "ShareNote 마이그레이션 테스트 환경 설정"

    # 1. Docker 환경 확인
    check_docker

    # 2. 필수 도구 확인
    check_required_tools

    # 3. 디렉토리 구조 생성
    create_directory_structure

    # 4. Docker 컨테이너 시작
    start_containers

    # 5. 컨테이너 준비 대기
    if ! wait_for_containers; then
        log_error "컨테이너 설정 실패"
        check_container_logs
        exit 1
    fi

    # 6. 컨테이너 상태 출력
    show_container_status

    # 7. 연결 테스트
    if ! test_connections; then
        log_error "연결 테스트 실패"
        check_container_logs
        exit 1
    fi

    # 8. 사용법 안내
    show_usage

    log_success "테스트 환경 설정이 완료되었습니다!"
}

# 도움말 함수
show_help() {
    echo "사용법: $0 [옵션]"
    echo ""
    echo "옵션:"
    echo "  -h, --help     이 도움말을 표시합니다"
    echo "  --logs         컨테이너 로그를 확인합니다"
    echo "  --status       컨테이너 상태를 확인합니다"
    echo "  --stop         모든 컨테이너를 중지합니다"
    echo "  --clean        모든 컨테이너와 볼륨을 제거합니다"
}

# 파라미터 처리
case "${1:-}" in
    -h|--help)
        show_help
        exit 0
        ;;
    --logs)
        check_container_logs
        exit 0
        ;;
    --status)
        show_container_status
        exit 0
        ;;
    --stop)
        log_info "모든 컨테이너를 중지합니다..."
        docker-compose stop
        log_success "컨테이너 중지 완료"
        exit 0
        ;;
    --clean)
        log_warning "모든 컨테이너와 볼륨을 제거합니다..."
        docker-compose down -v
        log_success "정리 완료"
        exit 0
        ;;
    "")
        main "$@"
        ;;
    *)
        log_error "알 수 없는 옵션: $1"
        show_help
        exit 1
        ;;
esac