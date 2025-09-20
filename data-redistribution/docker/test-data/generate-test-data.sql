-- ShareNote Data Redistribution - Test Data Generation Script
-- Generates 50,000 pages with 3 permissions and 30 blocks each

\echo 'Starting test data generation...'

-- Create temporary function for random UUID generation
CREATE OR REPLACE FUNCTION random_uuid() RETURNS UUID AS $$
BEGIN
    RETURN gen_random_uuid();
END;
$$ LANGUAGE plpgsql;

-- Create sample workspace and user IDs
INSERT INTO temp_ids (type, id) VALUES
('workspace', random_uuid()),
('workspace', random_uuid()),
('workspace', random_uuid()),
('user', random_uuid()),
('user', random_uuid()),
('user', random_uuid()),
('user', random_uuid()),
('user', random_uuid())
ON CONFLICT DO NOTHING;

-- Create temporary table for workspace and user IDs
CREATE TEMP TABLE IF NOT EXISTS temp_ids (
    type VARCHAR(20),
    id UUID
);

-- Insert sample workspace and user IDs
WITH workspace_ids AS (
    SELECT random_uuid() as id FROM generate_series(1, 3)
), user_ids AS (
    SELECT random_uuid() as id FROM generate_series(1, 5)
)
INSERT INTO temp_ids (type, id)
SELECT 'workspace', id FROM workspace_ids
UNION ALL
SELECT 'user', id FROM user_ids;

\echo 'Generating 50,000 pages...'

-- Generate 50,000 pages in batches
DO $$
DECLARE
    batch_size INTEGER := 1000;
    total_pages INTEGER := 50000;
    current_batch INTEGER := 0;
    workspace_ids UUID[];
    user_ids UUID[];
BEGIN
    -- Get workspace and user IDs
    SELECT array_agg(id) INTO workspace_ids FROM temp_ids WHERE type = 'workspace';
    SELECT array_agg(id) INTO user_ids FROM temp_ids WHERE type = 'user';

    -- Generate pages in batches
    WHILE current_batch * batch_size < total_pages LOOP
        INSERT INTO pages (
            id,
            workspace_id,
            title,
            icon,
            cover,
            properties,
            is_public,
            is_archived,
            is_template,
            created_by,
            last_edited_by,
            migration_status
        )
        SELECT
            random_uuid(),
            workspace_ids[1 + (i % array_length(workspace_ids, 1))],
            'Test Page ' || (current_batch * batch_size + i),
            '{"type": "emoji", "emoji": "📄"}',
            '{"type": "image", "url": "https://example.com/cover.jpg"}',
            '{"status": "draft", "tags": ["test", "migration"]}',
            (random() < 0.1), -- 10% public
            false,
            (random() < 0.05), -- 5% templates
            user_ids[1 + (i % array_length(user_ids, 1))],
            user_ids[1 + ((i + 1) % array_length(user_ids, 1))],
            'READY'
        FROM generate_series(1, LEAST(batch_size, total_pages - current_batch * batch_size)) i;

        current_batch := current_batch + 1;

        -- Log progress
        IF current_batch % 10 = 0 THEN
            RAISE NOTICE 'Generated % pages...', current_batch * batch_size;
        END IF;
    END LOOP;

    RAISE NOTICE 'Completed generating % pages', total_pages;
END $$;

\echo 'Generating page permissions (3 per page)...'

-- Generate 3 permissions per page
DO $$
DECLARE
    batch_size INTEGER := 5000;
    page_count INTEGER;
    current_offset INTEGER := 0;
    user_ids UUID[];
    permissions page_permission_type[] := ARRAY['READ', 'COMMENT', 'EDIT', 'FULL_ACCESS'];
BEGIN
    -- Get user IDs and page count
    SELECT array_agg(id) INTO user_ids FROM temp_ids WHERE type = 'user';
    SELECT count(*) INTO page_count FROM pages;

    -- Generate permissions in batches
    WHILE current_offset < page_count LOOP
        INSERT INTO page_permissions (
            id,
            page_id,
            user_id,
            permission_type,
            granted_by,
            migration_status
        )
        SELECT
            random_uuid(),
            p.id,
            user_ids[1 + ((row_number() OVER() + perm_num - 1) % array_length(user_ids, 1))],
            permissions[1 + (perm_num % array_length(permissions, 1))],
            user_ids[1], -- First user as granter
            'READY'
        FROM (
            SELECT id
            FROM pages
            ORDER BY id
            LIMIT batch_size OFFSET current_offset
        ) p
        CROSS JOIN generate_series(1, 3) perm_num;

        current_offset := current_offset + batch_size;

        -- Log progress
        IF (current_offset / batch_size) % 10 = 0 THEN
            RAISE NOTICE 'Generated permissions for % pages...', current_offset;
        END IF;
    END LOOP;

    RAISE NOTICE 'Completed generating permissions for % pages', page_count;
END $$;

\echo 'Generating blocks (30 per page)...'

-- Generate 30 blocks per page
DO $$
DECLARE
    batch_size INTEGER := 1000;
    page_count INTEGER;
    current_offset INTEGER := 0;
    user_ids UUID[];
    block_types VARCHAR[] := ARRAY['paragraph', 'heading_1', 'heading_2', 'heading_3', 'bulleted_list', 'numbered_list', 'code', 'quote', 'divider', 'image'];
BEGIN
    -- Get user IDs and page count
    SELECT array_agg(id) INTO user_ids FROM temp_ids WHERE type = 'user';
    SELECT count(*) INTO page_count FROM pages;

    -- Generate blocks in batches
    WHILE current_offset < page_count LOOP
        INSERT INTO blocks (
            id,
            page_id,
            type,
            content,
            position,
            is_archived,
            created_by,
            last_edited_by,
            migration_status
        )
        SELECT
            random_uuid(),
            p.id,
            block_types[1 + (block_num % array_length(block_types, 1))],
            (CASE
                WHEN block_types[1 + (block_num % array_length(block_types, 1))] = 'paragraph' THEN
                    '{"text": "This is test paragraph content for block ' || block_num || ' in page ' || p.title || '.", "rich_text": [{"type": "text", "text": {"content": "This is test content."}}]}'
                WHEN block_types[1 + (block_num % array_length(block_types, 1))] LIKE 'heading%' THEN
                    '{"text": "Test Heading ' || block_num || '", "rich_text": [{"type": "text", "text": {"content": "Test Heading"}}]}'
                WHEN block_types[1 + (block_num % array_length(block_types, 1))] = 'code' THEN
                    '{"language": "javascript", "text": "console.log(\"Test code block ' || block_num || '\");", "rich_text": [{"type": "text", "text": {"content": "console.log(\"test\");"}}]}'
                ELSE
                    '{"text": "Test ' || block_types[1 + (block_num % array_length(block_types, 1))] || ' content", "rich_text": [{"type": "text", "text": {"content": "Test content"}}]}'
            END)::jsonb,
            block_num,
            false,
            user_ids[1 + (block_num % array_length(user_ids, 1))],
            user_ids[1 + ((block_num + 1) % array_length(user_ids, 1))],
            'READY'
        FROM (
            SELECT id, title
            FROM pages
            ORDER BY id
            LIMIT batch_size OFFSET current_offset
        ) p
        CROSS JOIN generate_series(1, 30) block_num;

        current_offset := current_offset + batch_size;

        -- Log progress
        IF (current_offset / batch_size) % 5 = 0 THEN
            RAISE NOTICE 'Generated blocks for % pages...', current_offset;
        END IF;
    END LOOP;

    RAISE NOTICE 'Completed generating blocks for % pages', page_count;
END $$;

-- Generate statistics
\echo 'Test data generation completed!'
\echo 'Statistics:'

SELECT 'Pages' as entity, count(*) as total FROM pages
UNION ALL
SELECT 'Page Permissions' as entity, count(*) as total FROM page_permissions
UNION ALL
SELECT 'Blocks' as entity, count(*) as total FROM blocks;

-- Clean up
DROP FUNCTION IF EXISTS random_uuid();
DROP TABLE IF EXISTS temp_ids;