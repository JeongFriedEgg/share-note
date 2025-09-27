#!/bin/bash

# 마이그레이션 테스트 자동화 스크립트
# 설정 변수
LEGACY_DB="sharenote_legacy"
SHARD1_DB="sharenote_shard1"
SHARD2_DB="sharenote_shard2"
DB_USER="postgre_user"
DB_PASSWORD="postgre_password"
LEGACY_PORT="5432"
SHARD1_PORT="5433"
SHARD2_PORT="5434"
REDIS_PORT="6379"

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

# PostgreSQL 연결 테스트 함수
test_db_connection() {
    local db_name=$1
    local port=$2

    if PGPASSWORD=$DB_PASSWORD psql -h localhost -p $port -U $DB_USER -d $db_name -c "SELECT 1;" > /dev/null 2>&1; then
        log_success "$db_name 데이터베이스 연결 성공 (포트: $port)"
        return 0
    else
        log_error "$db_name 데이터베이스 연결 실패 (포트: $port)"
        return 1
    fi
}

# Redis 연결 테스트 함수
test_redis_connection() {
    if redis-cli -p $REDIS_PORT ping > /dev/null 2>&1; then
        log_success "Redis 연결 성공 (포트: $REDIS_PORT)"
        return 0
    else
        log_error "Redis 연결 실패 (포트: $REDIS_PORT)"
        return 1
    fi
}

# 데이터베이스 초기화 함수
reset_databases() {
    log_header "데이터베이스 초기화 중..."

    # Legacy DB 초기화
    log_info "Legacy DB 테이블 삭제 및 재생성 중..."
    PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB << EOF
DROP TABLE IF EXISTS page_permissions CASCADE;
DROP TABLE IF EXISTS blocks CASCADE;
DROP TABLE IF EXISTS pages CASCADE;
DROP TYPE IF EXISTS migration_status CASCADE;
DROP TYPE IF EXISTS page_permission_type CASCADE;
EOF

    # Shard1 DB 초기화
    log_info "Shard1 DB 테이블 삭제 및 재생성 중..."
    PGPASSWORD=$DB_PASSWORD psql -h localhost -p $SHARD1_PORT -U $DB_USER -d $SHARD1_DB << EOF
DROP TABLE IF EXISTS page_permissions CASCADE;
DROP TABLE IF EXISTS blocks CASCADE;
DROP TABLE IF EXISTS pages CASCADE;
DROP TYPE IF EXISTS migration_status CASCADE;
DROP TYPE IF EXISTS page_permission_type CASCADE;
EOF

    # Shard2 DB 초기화
    log_info "Shard2 DB 테이블 삭제 및 재생성 중..."
    PGPASSWORD=$DB_PASSWORD psql -h localhost -p $SHARD2_PORT -U $DB_USER -d $SHARD2_DB << EOF
DROP TABLE IF EXISTS page_permissions CASCADE;
DROP TABLE IF EXISTS blocks CASCADE;
DROP TABLE IF EXISTS pages CASCADE;
DROP TYPE IF EXISTS migration_status CASCADE;
DROP TYPE IF EXISTS page_permission_type CASCADE;
EOF

    # 모든 DB에 스키마 재생성
    for db_config in "$LEGACY_DB:$LEGACY_PORT" "$SHARD1_DB:$SHARD1_PORT" "$SHARD2_DB:$SHARD2_PORT"; do
        IFS=':' read -r db_name port <<< "$db_config"
        log_info "$db_name 스키마 생성 중..."

        PGPASSWORD=$DB_PASSWORD psql -h localhost -p $port -U $DB_USER -d $db_name -f sql/01-init.sql
        PGPASSWORD=$DB_PASSWORD psql -h localhost -p $port -U $DB_USER -d $db_name -f sql/02-indexes.sql
    done

    log_success "모든 데이터베이스 초기화 완료"
}

# 테스트 데이터 생성 함수
generate_test_data() {
    log_header "테스트 데이터 생성 중..."

    log_info "Legacy DB에 테스트 데이터 생성 중 (페이지 1000개, 권한 3000개, 블록 30000개)..."
    PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB -f test/03-test-data.sql

    log_success "테스트 데이터 생성 완료"
}

# 마이그레이션 전 데이터 확인 함수
check_initial_data() {
    log_header "마이그레이션 전 데이터 상태 확인"

    log_info "Legacy DB 데이터 현황:"
    PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB << EOF
SELECT
    'Legacy DB' as database,
    'Pages' as table_name,
    COUNT(*) as count
FROM pages
UNION ALL
SELECT
    'Legacy DB' as database,
    'Blocks' as table_name,
    COUNT(*) as count
FROM blocks
UNION ALL
SELECT
    'Legacy DB' as database,
    'Page Permissions' as table_name,
    COUNT(*) as count
FROM page_permissions;
EOF

    log_info "Shard1 DB 데이터 현황:"
    PGPASSWORD=$DB_PASSWORD psql -h localhost -p $SHARD1_PORT -U $DB_USER -d $SHARD1_DB << EOF
SELECT
    'Shard1 DB' as database,
    'Pages' as table_name,
    COUNT(*) as count
FROM pages
UNION ALL
SELECT
    'Shard1 DB' as database,
    'Blocks' as table_name,
    COUNT(*) as count
FROM blocks
UNION ALL
SELECT
    'Shard1 DB' as database,
    'Page Permissions' as table_name,
    COUNT(*) as count
FROM page_permissions;
EOF

    log_info "Shard2 DB 데이터 현황:"
    PGPASSWORD=$DB_PASSWORD psql -h localhost -p $SHARD2_PORT -U $DB_USER -d $SHARD2_DB << EOF
SELECT
    'Shard2 DB' as database,
    'Pages' as table_name,
    COUNT(*) as count
FROM pages
UNION ALL
SELECT
    'Shard2 DB' as database,
    'Blocks' as table_name,
    COUNT(*) as count
FROM blocks
UNION ALL
SELECT
    'Shard2 DB' as database,
    'Page Permissions' as table_name,
    COUNT(*) as count
FROM page_permissions;
EOF
}

# 마이그레이션 진행 상황 모니터링 함수
monitor_migration_progress() {
    log_header "마이그레이션 진행 상황 모니터링 시작"

    local total_pages=1000 # 시작 시점의 총 페이지 수
    local check_interval=30 # 30초마다 확인
    local timeout=3600 # 1시간 타임아웃
    local elapsed=0

    while [ $elapsed -lt $timeout ]; do
        # Legacy DB에서 마이그레이션 상태 확인
        local migration_stats=$(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB -t -c "
            SELECT
                COALESCE(SUM(CASE WHEN migration_status = 'READY' THEN 1 ELSE 0 END), 0) as ready,
                COALESCE(SUM(CASE WHEN migration_status = 'MIGRATING' THEN 1 ELSE 0 END), 0) as migrating,
                COALESCE(SUM(CASE WHEN migration_status = 'MIGRATED' THEN 1 ELSE 0 END), 0) as migrated,
                COALESCE(SUM(CASE WHEN migration_status = 'FAILED' THEN 1 ELSE 0 END), 0) as failed
            FROM pages;
        " | tr -d ' ')
        IFS='|' read -r ready migrating legacy_migrated failed <<< "$migration_stats"

        # 각 Shard DB의 페이지 수 확인
        local shard1_pages=$(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $SHARD1_PORT -U $DB_USER -d $SHARD1_DB -t -c "SELECT COUNT(*) FROM pages;" | tr -d ' ')
        local shard2_pages=$(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $SHARD2_PORT -U $DB_USER -d $SHARD2_DB -t -c "SELECT COUNT(*) FROM pages;" | tr -d ' ')

        # 완료된 페이지 수 계산 (Shard DB로 이동된 페이지 + Legacy DB에 남아있는 migrated 상태 페이지)
        local completed_pages=$((shard1_pages + shard2_pages + legacy_migrated))

        # 전체 진행률 계산
        local progress_percent=0
        if [ $total_pages -gt 0 ]; then
            progress_percent=$(( (completed_pages * 100) / total_pages ))
        fi

        log_info "마이그레이션 진행률: $progress_percent% ($completed_pages/$total_pages)"
        log_info "상태별 페이지 수 - READY: $ready, MIGRATING: $migrating, MIGRATED: $legacy_migrated, FAILED: $failed"
        log_info "Shard DB 페이지 수 - Shard1: $shard1_pages, Shard2: $shard2_pages"

        # 마이그레이션 완료 확인
        if [ "$completed_pages" -eq "$total_pages" ] || [ $((completed_pages + failed)) -eq "$total_pages" ]; then
            log_success "마이그레이션 완료! 성공: $completed_pages, 실패: $failed"
            break
        fi

        sleep $check_interval
        elapsed=$((elapsed + check_interval))

        # 진행률 표시 바
        local bar_length=40
        local filled_length=$(( (completed_pages * bar_length) / total_pages ))
        local bar=""
        for ((i=0; i<bar_length; i++)); do
            if [ $i -lt $filled_length ]; then
                bar="${bar}█"
            else
                bar="${bar}░"
            fi
        done
        echo -e "진행률: [${GREEN}${bar}${NC}] ${progress_percent}%"
        echo ""
    done

    if [ $elapsed -ge $timeout ]; then
        log_warning "모니터링 타임아웃에 도달했습니다."
    fi
}

# 마이그레이션 후 데이터 검증 함수
verify_migration_result() {
    log_header "마이그레이션 결과 검증"
    local initial_pages=$1
    local initial_blocks=$2
    local initial_permissions=$3

    # Legacy DB 최종 상태
    log_info "Legacy DB 최종 상태:"
    PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB << EOF
SELECT
    migration_status,
    COUNT(*) as count
FROM pages
GROUP BY migration_status
ORDER BY migration_status;
EOF

    # Legacy DB에 남아있는 블록/권한 수 조회
    local legacy_final_counts=$(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB -t -A -c "
        SELECT
            COALESCE((SELECT COUNT(*) FROM blocks), 0),
            COALESCE((SELECT COUNT(*) FROM page_permissions), 0);
    ")
    IFS='|' read -r legacy_blocks legacy_permissions <<< "$legacy_final_counts"
    log_info "Legacy DB 최종 블록/권한 수: Blocks($legacy_blocks), Permissions($legacy_permissions)"

    # Shard1 DB 최종 데이터 수
    log_info "Shard1 DB 최종 데이터 수:"
    local shard1_counts=$(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $SHARD1_PORT -U $DB_USER -d $SHARD1_DB -t -A -c "
        SELECT
            COUNT(*) as pages,
            (SELECT COUNT(*) FROM blocks) as blocks,
            (SELECT COUNT(*) FROM page_permissions) as permissions
        FROM pages;
    ")
    IFS='|' read -r shard1_pages shard1_blocks shard1_permissions <<< "$shard1_counts"
    echo "  - Pages: $shard1_pages"
    echo "  - Blocks: $shard1_blocks"
    echo "  - Permissions: $shard1_permissions"

    # Shard2 DB 최종 데이터 수
    log_info "Shard2 DB 최종 데이터 수:"
    local shard2_counts=$(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $SHARD2_PORT -U $DB_USER -d $SHARD2_DB -t -A -c "
        SELECT
            COUNT(*) as pages,
            (SELECT COUNT(*) FROM blocks) as blocks,
            (SELECT COUNT(*) FROM page_permissions) as permissions
        FROM pages;
    ")
    IFS='|' read -r shard2_pages shard2_blocks shard2_permissions <<< "$shard2_counts"
    echo "  - Pages: $shard2_pages"
    echo "  - Blocks: $shard2_blocks"
    echo "  - Permissions: $shard2_permissions"

    # 모든 DB의 데이터를 합산하여 총합 계산
    local total_migrated_pages=$((shard1_pages + shard2_pages))
    local total_migrated_blocks=$((shard1_blocks + shard2_blocks))
    local total_migrated_permissions=$((shard1_permissions + shard2_permissions))

    local final_total_pages=$((total_migrated_pages + $(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB -t -A -c "SELECT COUNT(*) FROM pages;" | tr -d ' ')))
    local final_total_blocks=$((total_migrated_blocks + legacy_blocks))
    local final_total_permissions=$((total_migrated_permissions + legacy_permissions))

    log_info "마이그레이션 결과 요약:"
    echo "  - 총 페이지 수: ${final_total_pages}/${initial_pages}"
    echo "  - 총 블록 수: ${final_total_blocks}/${initial_blocks}"
    echo "  - 총 권한 수: ${final_total_permissions}/${initial_permissions}"

    # 데이터 무결성 검증
    if [ "$final_total_pages" -eq "$initial_pages" ] && [ "$final_total_blocks" -eq "$initial_blocks" ] && [ "$final_total_permissions" -eq "$initial_permissions" ]; then
        log_success "데이터 무결성 검증 통과! 모든 데이터가 성공적으로 마이그레이션되었습니다."
        return 0
    else
        log_error "데이터 무결성 검증 실패! 일부 데이터가 누락되었습니다."
        return 1
    fi
}

# Redis 데이터 정리 함수
cleanup_redis() {
    log_info "Redis 데이터 정리 중..."
    redis-cli -p $REDIS_PORT FLUSHDB > /dev/null 2>&1
    log_success "Redis 데이터 정리 완료"
}

# 메인 실행 함수
main() {
    log_header "ShareNote 데이터 마이그레이션 테스트 시작"

    # 1. 연결 테스트
    log_header "Step 1: 인프라 연결 테스트"
    if ! test_db_connection $LEGACY_DB $LEGACY_PORT || \
       ! test_db_connection $SHARD1_DB $SHARD1_PORT || \
       ! test_db_connection $SHARD2_DB $SHARD2_PORT || \
       ! test_redis_connection; then
        log_error "인프라 연결 테스트 실패. Docker 컨테이너 상태를 확인해주세요."
        exit 1
    fi

    # 2. 사용자 확인
    echo ""
    read -p "기존 데이터를 모두 삭제하고 테스트를 진행하시겠습니까? (y/N): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "테스트를 중단합니다."
        exit 0
    fi

    # 3. 데이터베이스 초기화
    reset_databases

    # 4. Redis 정리
    cleanup_redis

    # 5. 테스트 데이터 생성
    generate_test_data

    # 6. 마이그레이션 전 상태 확인 및 초기 데이터 수 저장
    log_header "마이그레이션 전 데이터 상태 확인"
    local initial_counts=$(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB -t -A -c "
        SELECT (SELECT COUNT(*) FROM pages), (SELECT COUNT(*) FROM blocks), (SELECT COUNT(*) FROM page_permissions);
    ")
    IFS='|' read -r initial_pages initial_blocks initial_permissions <<< "$initial_counts"

    check_initial_data

    # 7. 마이그레이션 애플리케이션 실행 안내
    log_header "Step 7: 마이그레이션 애플리케이션 실행"
    log_info "이제 별도 터미널에서 Spring Boot 애플리케이션을 실행해주세요:"
    log_info "  ./gradlew bootRun"
    log_info ""
    log_info "애플리케이션이 실행되면 자동으로 마이그레이션이 시작됩니다."
    echo ""
    read -p "애플리케이션을 실행했다면 Enter를 눌러 모니터링을 시작하세요..." -r

    # 8. 마이그레이션 진행 상황 모니터링
    monitor_migration_progress

    # 9. 결과 검증
    verify_migration_result "$initial_pages" "$initial_blocks" "$initial_permissions"

    if [ $? -eq 0 ]; then
        log_header "테스트 완료"
        log_success "마이그레이션 테스트가 성공적으로 완료되었습니다!"
    else
        log_header "테스트 실패"
        log_error "마이그레이션 테스트에서 문제가 발생했습니다. 로그를 확인해주세요."
        exit 1
    fi
}

# 스크립트 실행
main "$@"