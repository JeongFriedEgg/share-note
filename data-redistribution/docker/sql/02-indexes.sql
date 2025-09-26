-- 인덱스 생성

-- 마이그레이션 전
-- pages 인덱스
CREATE INDEX idx_pages_workspace_archived_created
ON pages (workspace_id, is_archived, created_at DESC)
WHERE is_archived = false;


-- page_permissions 인덱스
CREATE INDEX idx_page_permissions_page_id_user_id
ON page_permissions(page_id, user_id);

-- blocks 인덱스
CREATE INDEX idx_blocks_archived_page_id_position
ON blocks(page_id, is_archived, position ASC)
WHERE is_archived = false;

CREATE INDEX idx_blocks_parent_position
ON blocks (parent_block_id, position ASC)
WHERE parent_block_id IS NOT NULL AND is_archived = false;

-- 마이그레이션 진행 중 (하이브리드 운영)
CREATE INDEX idx_pages_migration_status_updated
ON pages (migration_status, updated_at ASC)
WHERE migration_status IN ('READY','FAILED');


-- 마이그레이션 완료 후 (샤드 분산 운영)
-- DROP INDEX IF EXISTS idx_pages_migration_status_updated