#!/bin/bash

# 마이그레이션 실시간 모니터링 스크립트
# 마이그레이션 진행 상황을 실시간으로 모니터링하고 상세 정보를 제공

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
CYAN='\033[0;36m'
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

# 터미널 화면 지우기
clear_screen() {
    printf '\033[2J\033[H'
}

# 진행률 바 생성 함수
create_progress_bar() {
    local current=$1
    local total=$2
    local width=${3:-50}

    # total이 0인 경우 0% 반환
    if [ "$total" -eq 0 ]; then
        printf "[${GREEN}░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░${NC}] %3d%% (%d/%d)" 0 "$current" "$total"
        return
    fi

    local percentage=$(( (current * 100) / total ))
    local filled=$(( (current * width) / total ))

    local bar=""
    for ((i=0; i<width; i++)); do
        if [ $i -lt $filled ]; then
            bar="${bar}█"
        else
            bar="${bar}░"
        fi
    done

    printf "[${GREEN}%s${NC}] %3d%% (%d/%d)" "$bar" "$percentage" "$current" "$total"
}

# 데이터베이스 상태 조회 함수
get_database_stats() {
    local db_name=$1
    local port=$2

    local stats=$(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $port -U $DB_USER -d $db_name -t -A -c "
        SELECT
            COALESCE((SELECT COUNT(*) FROM pages), 0) as pages,
            COALESCE((SELECT COUNT(*) FROM blocks), 0) as blocks,
            COALESCE((SELECT COUNT(*) FROM page_permissions), 0) as permissions;
    " 2>/dev/null)

    if [ $? -eq 0 ]; then
        echo "$stats"
    else
        echo "0|0|0"
    fi
}

# Legacy DB 마이그레이션 상태 조회 함수
get_legacy_migration_stats() {
    local stats=$(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB -t -A -c "
        SELECT
            COALESCE(SUM(CASE WHEN migration_status = 'READY' THEN 1 ELSE 0 END), 0) as ready,
            COALESCE(SUM(CASE WHEN migration_status = 'MIGRATING' THEN 1 ELSE 0 END), 0) as migrating,
            COALESCE(SUM(CASE WHEN migration_status = 'MIGRATED' THEN 1 ELSE 0 END), 0) as migrated,
            COALESCE(SUM(CASE WHEN migration_status = 'FAILED' THEN 1 ELSE 0 END), 0) as failed
        FROM pages;
    " 2>/dev/null)

    if [ $? -eq 0 ]; then
        echo "$stats"
    else
        echo "0|0|0|0"
    fi
}

# Redis 락 정보 조회 함수
get_redis_lock_info() {
    local lock_count=$(redis-cli -p $REDIS_PORT KEYS "sharenote:lock:migration:page:*" 2>/dev/null | wc -l)
    echo "${lock_count:-0}"
}

# 실시간 상태 표시 함수
display_realtime_status() {
    local start_time=$(date +%s)

    while true; do
        clear_screen

        # 현재 시간과 실행 시간 계산
        local current_time=$(date +"%Y-%m-%d %H:%M:%S")
        local elapsed_seconds=$(( $(date +%s) - start_time ))
        local elapsed_formatted=$(date -u -d @$elapsed_seconds +"%H:%M:%S")

        # 헤더 출력
        echo -e "${PURPLE}════════════════════════════════════════════════════════════════════════════════${NC}"
        echo -e "${PURPLE}                         ShareNote 마이그레이션 실시간 모니터링                          ${NC}"
        echo -e "${PURPLE}════════════════════════════════════════════════════════════════════════════════${NC}"
        echo -e "${CYAN}현재 시간: $current_time${NC} | ${CYAN}실행 시간: $elapsed_formatted${NC}"
        echo ""

        # Legacy DB 마이그레이션 상태 조회
        local migration_stats=$(get_legacy_migration_stats)
        IFS='|' read -r ready migrating legacy_migrated failed <<< "$migration_stats"

        # 각 데이터베이스 상태 조회
        local legacy_stats=$(get_database_stats $LEGACY_DB $LEGACY_PORT)
        local shard1_stats=$(get_database_stats $SHARD1_DB $SHARD1_PORT)
        local shard2_stats=$(get_database_stats $SHARD2_DB $SHARD2_PORT)

        IFS='|' read -r legacy_pages legacy_blocks legacy_permissions <<< "$legacy_stats"
        IFS='|' read -r shard1_pages shard1_blocks shard1_permissions <<< "$shard1_stats"
        IFS='|' read -r shard2_pages shard2_blocks shard2_permissions <<< "$shard2_stats"

        # 마이그레이션 완료된 페이지 수 계산 (Legacy DB의 MIGRATED 상태 페이지 + Shard DB 페이지)
        local completed_pages=$((shard1_pages + shard2_pages + legacy_migrated))

        # 전체 마이그레이션 대상 페이지 수 동적 계산
        local total_pages=$((completed_pages + ready + migrating + failed))

        # Redis 락 정보
        local active_locks=$(get_redis_lock_info)

        # 마이그레이션 진행률 표시
        echo -e "${YELLOW}📊 마이그레이션 진행 상황${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

        local progress_bar=$(create_progress_bar $completed_pages $total_pages 60)
        echo -e "전체 진행률: $progress_bar"
        echo ""

        # 상태별 페이지 수 표시
        echo -e "${YELLOW}📈 마이그레이션 상태별 통계${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        printf "%-15s: ${GREEN}%6d${NC}   " "✅ 완료(MIGRATED)" $completed_pages
        printf "%-15s: ${BLUE}%6d${NC}\n" "⏳ 진행중(MIGRATING)" $migrating
        printf "%-15s: ${YELLOW}%6d${NC}   " "⏸️ 대기(READY)" $ready
        printf "%-15s: ${RED}%6d${NC}\n" "❌ 실패(FAILED)" $failed
        echo ""

        # 데이터베이스별 데이터 현황
        echo -e "${YELLOW}🗄️ 데이터베이스별 데이터 현황${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        printf "%-12s │ %8s │ %8s │ %8s\n" "Database" "Pages" "Blocks" "Permissions"
        echo "─────────────┼──────────┼──────────┼────────────"
        printf "%-12s │ ${YELLOW}%8s${NC} │ ${YELLOW}%8s${NC} │ ${YELLOW}%8s${NC}\n" "Legacy" "$legacy_pages" "$legacy_blocks" "$legacy_permissions"
        printf "%-12s │ ${GREEN}%8s${NC} │ ${GREEN}%8s${NC} │ ${GREEN}%8s${NC}\n" "Shard1" "$shard1_pages" "$shard1_blocks" "$shard1_permissions"
        printf "%-12s │ ${BLUE}%8s${NC} │ ${BLUE}%8s${NC} │ ${BLUE}%8s${NC}\n" "Shard2" "$shard2_pages" "$shard2_blocks" "$shard2_permissions"
        echo ""

        # 마이그레이션 속도 계산 및 예상 완료 시간
        if [ $elapsed_seconds -gt 0 ] && [ $completed_pages -gt 0 ]; then
            local pages_per_second=$(echo "scale=2; $completed_pages / $elapsed_seconds" | bc -l 2>/dev/null || echo "0")
            local remaining_pages=$((total_pages - completed_pages))

            if [ "$pages_per_second" != "0" ] && [ $(echo "$pages_per_second > 0" | bc -l 2>/dev/null || echo "0") -eq 1 ]; then
                local eta_seconds=$(echo "$remaining_pages / $pages_per_second" | bc -l 2>/dev/null || echo "0")
                local eta_formatted=$(date -u -d @${eta_seconds%.*} +"%H:%M:%S" 2>/dev/null || echo "계산 불가")
            else
                local eta_formatted="계산 불가"
            fi
        else
            local pages_per_second="0.00"
            local eta_formatted="계산 불가"
        fi

        echo -e "${YELLOW}⚡ 성능 지표${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        printf "처리 속도: ${GREEN}%.2f pages/sec${NC}   " "$pages_per_second"
        printf "예상 완료 시간: ${CYAN}%s${NC}   " "$eta_formatted"
        printf "활성 락: ${PURPLE}%d${NC}\n" "$active_locks"
        echo ""

        # 완료 상태 확인
        if [ $completed_pages -eq $total_pages ] && [ $total_pages -gt 0 ]; then
            local success_rate=$(echo "scale=2; $completed_pages * 100 / $total_pages" | bc -l 2>/dev/null || echo "0")
            echo -e "${GREEN}🎉 마이그레이션 완료!${NC}"
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            printf "성공률: ${GREEN}%.2f%%${NC}   " "$success_rate"
            printf "총 처리 시간: ${CYAN}%s${NC}\n" "$elapsed_formatted"
            echo ""

            if [ $failed -gt 0 ]; then
                log_warning "$failed 개의 페이지 마이그레이션이 실패했습니다."
                echo ""
                echo "실패한 페이지를 다시 처리하려면 Spring Boot 애플리케이션을 재시작하세요."
            else
                log_success "모든 페이지가 성공적으로 마이그레이션되었습니다!"
            fi

            echo ""
            echo "모니터링을 중단하려면 Ctrl+C를 누르세요."
            break
        fi

        # 하단 정보
        echo -e "${CYAN}💡 팁: 실패한 페이지는 애플리케이션이 자동으로 재시도합니다.${NC}"
        echo -e "${CYAN}📊 더 자세한 로그는 애플리케이션 콘솔에서 확인하세요.${NC}"
        echo ""
        echo "모니터링을 중단하려면 Ctrl+C를 누르세요..."

        # 5초 대기
        sleep 5
    done
}

# 요약 보고서 생성 함수
generate_summary_report() {
    log_header "마이그레이션 요약 보고서"

    local total_pages=$(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB -t -A -c "SELECT COUNT(*) FROM pages;" 2>/dev/null)
    local migration_stats=$(get_legacy_migration_stats)
    IFS='|' read -r ready migrating legacy_migrated failed <<< "$migration_stats"

    local legacy_stats=$(get_database_stats $LEGACY_DB $LEGACY_PORT)
    local shard1_stats=$(get_database_stats $SHARD1_DB $SHARD1_PORT)
    local shard2_stats=$(get_database_stats $SHARD2_DB $SHARD2_PORT)

    IFS='|' read -r legacy_pages legacy_blocks legacy_permissions <<< "$legacy_stats"
    IFS='|' read -r shard1_pages shard1_blocks shard1_permissions <<< "$shard1_stats"
    IFS='|' read -r shard2_pages shard2_blocks shard2_permissions <<< "$shard2_stats"

    local completed_pages=$((shard1_pages + shard2_pages + legacy_migrated))
    local total_pages_report=$((completed_pages + ready + migrating + failed))

    echo "마이그레이션 결과 요약:"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    printf "• 총 처리 대상: %d개 페이지\n" "$total_pages_report"
    printf "• 성공적으로 마이그레이션: ${GREEN}%d개${NC}\n" "$completed_pages"
    printf "• 실패한 마이그레이션: ${RED}%d개${NC}\n" "$failed"
    printf "• 진행 중: ${BLUE}%d개${NC}\n" "$migrating"
    printf "• 대기 중: ${YELLOW}%d개${NC}\n" "$ready"
    echo ""

    echo "데이터베이스별 최종 데이터 분포:"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    printf "• Legacy DB: Pages: %s, Blocks: %s, Permissions: %s\n" "$legacy_pages" "$legacy_blocks" "$legacy_permissions"
    printf "• Shard1 DB: Pages: %s, Blocks: %s, Permissions: %s\n" "$shard1_pages" "$shard1_blocks" "$shard1_permissions"
    printf "• Shard2 DB: Pages: %s, Blocks: %s, Permissions: %s\n" "$shard2_pages" "$shard2_blocks" "$shard2_permissions"
    echo ""

    local total_migrated_pages=$((shard1_pages + shard2_pages + legacy_migrated))
    local total_migrated_blocks=$((shard1_blocks + shard2_blocks))
    local total_migrated_permissions=$((shard1_permissions + shard2_permissions))

    echo "마이그레이션 검증 결과:"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    local initial_blocks_count=$((total_pages_report * 30))
    local initial_permissions_count=$((total_pages_report * 3))

    if [ $((completed_pages + failed)) -eq $total_pages_report ]; then
        printf "• Pages 검증: ${GREEN}통과${NC} (마이그레이션된 %d + 실패 %d = 총 %d)\n" "$completed_pages" "$failed" "$total_pages_report"
    else
        printf "• Pages 검증: ${RED}실패${NC} (마이그레이션된 %d + 실패 %d ≠ 총 %d)\n" "$completed_pages" "$failed" "$total_pages_report"
    fi

    # 블록 및 권한 데이터 수량 검증
    if [ $total_migrated_blocks -eq $((completed_pages * 30)) ]; then
        printf "• Blocks 검증: ${GREEN}통과${NC} (예상 %d개 = 실제 %d개)\n" $((completed_pages * 30)) "$total_migrated_blocks"
    else
        printf "• Blocks 검증: ${RED}실패${NC} (예상 %d개 ≠ 실제 %d개)\n" $((completed_pages * 30)) "$total_migrated_blocks"
    fi

    if [ $total_migrated_permissions -eq $((completed_pages * 3)) ]; then
        printf "• Permissions 검증: ${GREEN}통과${NC} (예상 %d개 = 실제 %d개)\n" $((completed_pages * 3)) "$total_migrated_permissions"
    else
        printf "• Permissions 검증: ${RED}실패${NC} (예상 %d개 ≠ 실제 %d개)\n" $((completed_pages * 3)) "$total_migrated_permissions"
    fi
}

# 실패한 페이지 상세 정보 조회 함수
show_failed_pages() {
    log_header "실패한 페이지 상세 정보"

    local failed_pages=$(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB -t -A -c "
        SELECT
            id,
            title,
            updated_at
        FROM pages
        WHERE migration_status = 'FAILED'
        ORDER BY updated_at DESC
        LIMIT 10;
    " 2>/dev/null)

    if [ -z "$failed_pages" ]; then
        log_success "실패한 페이지가 없습니다."
        return
    fi

    echo "최근 실패한 페이지 (최대 10개):"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    printf "%-38s %-30s %s\n" "Page ID" "Title" "Last Updated"
    echo "──────────────────────────────────────┼──────────────────────────────┼─────────────────────"

    echo "$failed_pages" | while IFS='|' read -r page_id title updated_at; do
        if [ -n "$page_id" ]; then
            printf "%-38s │ %-28s │ %s\n" "$page_id" "${title:0:28}" "$updated_at"
        fi
    done
}

# Redis 락 상태 상세 조회 함수
show_redis_lock_details() {
    log_header "Redis 분산 락 상세 정보"

    local lock_keys=$(redis-cli -p $REDIS_PORT KEYS "sharenote:lock:migration:page:*" 2>/dev/null)
    local lock_count=$(echo "$lock_keys" | wc -l)

    if [ -z "$lock_keys" ] || [ "$lock_count" -eq 0 ]; then
        log_info "현재 활성화된 락이 없습니다."
        return
    fi

    echo "활성화된 분산 락: ${lock_count}개"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    local count=0
    echo "$lock_keys" | head -10 | while read -r key; do
        if [ -n "$key" ]; then
            local ttl=$(redis-cli -p $REDIS_PORT TTL "$key" 2>/dev/null)
            local page_id=$(echo "$key" | sed 's/.*://')
            printf "• Page ID: %s, TTL: %s초\n" "$page_id" "$ttl"
            count=$((count + 1))
        fi
    done

    if [ $lock_count -gt 10 ]; then
        echo "... 그리고 $((lock_count - 10))개 더"
    fi
}

# 사용법 표시 함수
show_help() {
    echo "사용법: $0 [옵션]"
    echo ""
    echo "옵션:"
    echo "  -h, --help          이 도움말을 표시합니다"
    echo "  -r, --realtime      실시간 모니터링을 시작합니다 (기본값)"
    echo "  -s, --summary       현재 상태 요약 보고서를 출력합니다"
    echo "  -f, --failed        실패한 페이지 목록을 표시합니다"
    echo "  -l, --locks         활성 Redis 락 정보를 표시합니다"
    echo "  -a, --all           모든 정보를 한번에 표시합니다"
    echo ""
    echo "예시:"
    echo "  $0                  # 실시간 모니터링 시작"
    echo "  $0 --summary        # 현재 상태 요약만 출력"
    echo "  $0 --failed         # 실패한 페이지 목록 출력"
    echo "  $0 --all            # 모든 정보 출력"
}

# 연결 테스트 함수
test_connections() {
    local failed=0

    # PostgreSQL 연결 테스트
    if ! PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB -c "SELECT 1;" &>/dev/null; then
        log_error "Legacy DB 연결 실패"
        failed=1
    fi

    if ! PGPASSWORD=$DB_PASSWORD psql -h localhost -p $SHARD1_PORT -U $DB_USER -d $SHARD1_DB -c "SELECT 1;" &>/dev/null; then
        log_error "Shard1 DB 연결 실패"
        failed=1
    fi

    if ! PGPASSWORD=$DB_PASSWORD psql -h localhost -p $SHARD2_PORT -U $DB_USER -d $SHARD2_DB -c "SELECT 1;" &>/dev/null; then
        log_error "Shard2 DB 연결 실패"
        failed=1
    fi

    # Redis 연결 테스트
    if ! redis-cli -p $REDIS_PORT ping &>/dev/null; then
        log_error "Redis 연결 실패"
        failed=1
    fi

    if [ $failed -eq 1 ]; then
        log_error "일부 서비스에 연결할 수 없습니다. Docker 컨테이너 상태를 확인해주세요."
        echo ""
        echo "Docker 컨테이너 상태 확인:"
        echo "  docker-compose ps"
        echo ""
        echo "컨테이너 로그 확인:"
        echo "  docker-compose logs [서비스명]"
        exit 1
    fi
}

# bc 설치 확인 함수
check_bc() {
    if ! command -v bc &> /dev/null; then
        log_warning "bc 계산기가 설치되어 있지 않습니다."
        log_info "Ubuntu/Debian: sudo apt-get install bc"
        log_info "macOS: brew install bc"
        log_info "일부 계산 기능이 제한될 수 있습니다."
    fi
}

# 메인 실행 함수
main() {
    # 필수 도구 확인
    check_bc

    # 연결 테스트
    test_connections

    # 전체 페이지 수 미리 계산 (요약 보고서에서만 사용)
    local total_pages_at_start=$(PGPASSWORD=$DB_PASSWORD psql -h localhost -p $LEGACY_PORT -U $DB_USER -d $LEGACY_DB -t -A -c "SELECT COUNT(*) FROM pages;" 2>/dev/null)

    if [ "$total_pages_at_start" -eq 0 ]; then
        log_warning "Legacy DB에 마이그레이션할 페이지가 없습니다. 테스트 데이터 생성 스크립트를 먼저 실행하세요."
        # 그러나 다른 옵션("-s, -f, -l")을 위해 스크립트를 계속 진행
    fi


    case "${1:-}" in
        -h|--help)
            show_help
            exit 0
            ;;
        -s|--summary)
            generate_summary_report
            ;;
        -f|--failed)
            show_failed_pages
            ;;
        -l|--locks)
            show_redis_lock_details
            ;;
        -a|--all)
            generate_summary_report
            echo ""
            show_failed_pages
            echo ""
            show_redis_lock_details
            ;;
        -r|--realtime|"")
            log_info "실시간 모니터링을 시작합니다..."
            log_info "Ctrl+C를 눌러 언제든지 중단할 수 있습니다."
            echo ""
            sleep 2
            display_realtime_status
            ;;
        *)
            log_error "알 수 없는 옵션: $1"
            show_help
            exit 1
            ;;
    esac
}

# 신호 처리 (Ctrl+C)
trap 'echo -e "\n\n${YELLOW}모니터링을 중단합니다...${NC}"; exit 0' INT TERM

# 스크립트 실행
main "$@"