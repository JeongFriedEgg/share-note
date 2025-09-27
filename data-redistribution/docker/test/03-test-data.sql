-- 테스트용 데이터 생성 스크립트
-- 페이지 1000개, 각 페이지당 권한 3개, 블록 30개씩 생성

-- 임시 시퀀스 생성 (테스트용)
CREATE TEMPORARY SEQUENCE IF NOT EXISTS temp_seq START 1;

-- 페이지 데이터 1000개 생성
INSERT INTO pages (
    id, workspace_id, title, icon, cover, properties, is_public, is_archived, is_template,
    created_at, updated_at, created_by, last_edited_by, migration_status
)
SELECT
    uuid_generate_v4() as id,
    uuid_generate_v4() as workspace_id,
    'Test Page ' || generate_series as title,
    CASE
        WHEN generate_series % 10 = 0 THEN '{"type": "emoji", "emoji": "📄"}' ::jsonb
        WHEN generate_series % 7 = 0 THEN '{"type": "emoji", "emoji": "📋"}'::jsonb
        WHEN generate_series % 5 = 0 THEN '{"type": "emoji", "emoji": "📝"}'::jsonb
        ELSE NULL
    END as icon,
    CASE
        WHEN generate_series % 15 = 0
        THEN '{"type": "external", "external": {"url": "https://images.unsplash.com/photo-1519389950473-47ba0277781c"}}'::jsonb
        ELSE NULL
    END as cover,
    jsonb_build_object(
        'tags', ARRAY['tag' || (generate_series % 5 + 1)::text],
        'priority', CASE WHEN generate_series % 3 = 0 THEN 'high' WHEN generate_series % 3 = 1 THEN 'medium' ELSE 'low' END,
        'status', CASE WHEN generate_series % 4 = 0 THEN 'todo' WHEN generate_series % 4 = 1 THEN 'in-progress' WHEN generate_series % 4 = 2 THEN 'done' ELSE 'backlog' END
    ) as properties,
    (generate_series % 10 = 0) as is_public,
    false as is_archived,
    (generate_series % 20 = 0) as is_template,
    NOW() - INTERVAL '1 day' * (generate_series % 30) as created_at,
    NOW() - INTERVAL '1 hour' * (generate_series % 24) as updated_at,
    uuid_generate_v4() as created_by,
    uuid_generate_v4() as last_edited_by,
    'READY' as migration_status
FROM generate_series(1, 1000);

-- 임시 테이블로 페이지 ID 저장
CREATE TEMPORARY TABLE temp_page_ids AS
SELECT id as page_id FROM pages WHERE migration_status = 'READY';

-- 각 페이지당 권한 3개씩 생성 (총 3000개)
INSERT INTO page_permissions (
    id, page_id, user_id, permission_type, granted_at, granted_by
)
SELECT
    uuid_generate_v4() as id,
    page_id,
    uuid_generate_v4() as user_id,
    CASE
        WHEN row_number() OVER (PARTITION BY page_id ORDER BY random()) = 1 THEN 'FULL_ACCESS'
        WHEN row_number() OVER (PARTITION BY page_id ORDER BY random()) = 2 THEN 'EDIT'
        ELSE 'READ'
    END as permission_type,
    NOW() - INTERVAL '1 day' * (random() * 10)::int as granted_at,
    uuid_generate_v4() as granted_by
FROM (
    SELECT
        page_id, generate_series(1, 3) as perm_seq
    FROM temp_page_ids
) perm_data;

-- 각 페이지당 블록 30개씩 생성 (총 30000개)
INSERT INTO blocks (
    id, page_id, parent_block_id, type, content, position, is_archived,
    created_at, updated_at, created_by, last_edited_by
)
SELECT
    uuid_generate_v4() as id,
    page_id,
    CASE
        WHEN block_seq > 1 AND block_seq % 5 = 0 THEN first_block_id
        ELSE NULL
    END as parent_block_id,
    CASE
        WHEN block_seq = 1 THEN 'heading_1'
        WHEN block_seq <= 5 THEN 'paragraph'
        WHEN block_seq <= 10 THEN 'bulleted_list_item'
        WHEN block_seq <= 15 THEN 'numbered_list_item'
        WHEN block_seq <= 20 THEN 'toggle'
        WHEN block_seq <= 25 THEN 'quote'
        ELSE 'callout'
    END as type,
    CASE
        WHEN block_seq = 1 THEN jsonb_build_object('rich_text', jsonb_build_array(jsonb_build_object('type', 'text', 'text', jsonb_build_object('content', 'Main Title'))))
        WHEN block_seq <= 5 THEN jsonb_build_object('rich_text', jsonb_build_array(jsonb_build_object('type', 'text', 'text', jsonb_build_object('content', 'This is paragraph content for block ' || block_seq))))
        WHEN block_seq <= 10 THEN jsonb_build_object('rich_text', jsonb_build_array(jsonb_build_object('type', 'text', 'text', jsonb_build_object('content', 'Bullet point ' || block_seq))))
        WHEN block_seq <= 15 THEN jsonb_build_object('rich_text', jsonb_build_array(jsonb_build_object('type', 'text', 'text', jsonb_build_object('content', 'Numbered item ' || block_seq))))
        WHEN block_seq <= 20 THEN jsonb_build_object('rich_text', jsonb_build_array(jsonb_build_object('type', 'text', 'text', jsonb_build_object('content', 'Toggle content ' || block_seq))))
        WHEN block_seq <= 25 THEN jsonb_build_object('rich_text', jsonb_build_array(jsonb_build_object('type', 'text', 'text', jsonb_build_object('content', 'Quote text ' || block_seq))))
        ELSE jsonb_build_object(
            'rich_text', jsonb_build_array(jsonb_build_object('type', 'text', 'text', jsonb_build_object('content', 'Callout content ' || block_seq))),
            'icon', jsonb_build_object('type', 'emoji', 'emoji', '💡'),
            'color', 'blue_background'
        )
    END as content,
    block_seq as position,
    false as is_archived,
    NOW() - INTERVAL '1 day' * (block_seq % 20) as created_at,
    NOW() - INTERVAL '1 hour' * (block_seq % 12) as updated_at,
    uuid_generate_v4() as created_by,
    uuid_generate_v4() as last_edited_by
FROM (
    SELECT
       page_id,
       generate_series(1, 30) as block_seq,
       FIRST_VALUE(uuid_generate_v4()) OVER (PARTITION BY page_id ORDER BY generate_series(1, 30)) as first_block_id
    FROM temp_page_ids
) block_data;


-- 테스트 데이터 생성 확인
SELECT
    'Pages' as table_name,
    COUNT(*) as total_count,
    COUNT(*) FILTER (WHERE migration_status = 'READY') as ready_count
FROM pages
UNION ALL
SELECT
    'Blocks' as table_name,
    COUNT(*) as total_count,
    COUNT(*) FILTER (WHERE is_archived = false) as active_count
FROM blocks
UNION ALL
SELECT
    'Page Permissions' as table_name,
    COUNT(*) as total_count,
    COUNT(DISTINCT page_id) as unique_pages
FROM page_permissions;


-- 마이그레이션 상태별 페이지 수 조회
SELECT
    migration_status,
    COUNT(*) as count
FROM pages
GROUP BY migration_status
ORDER BY migration_status;

-- 임시 테이블 정리
DROP TABLE temp_page_ids;