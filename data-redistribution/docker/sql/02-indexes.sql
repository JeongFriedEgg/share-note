-- 인덱스 생성

-- pages 인덱스
CREATE INDEX idx_pages_workspace ON pages (workspace_id, is_archived) WHERE is_archived = false;
CREATE INDEX idx_pages_migration_status ON pages (migration_status, created_at);

-- page_permissions 인덱스
CREATE INDEX idx_page_permissions_page_id_user_id ON page_permissions(page_id, user_id);

-- blocks 인덱스
CREATE INDEX idx_blocks_page_id_is_archived_position ON blocks(page_id, is_archived, position);
CREATE INDEX idx_blocks_parent_position ON blocks (parent_block_id, position)
WHERE parent_block_id IS NOT NULL AND is_archived = false;