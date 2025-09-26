-- UUID 활성화
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- enum 타입 생성
--CREATE TYPE migration_status AS ENUM ('READY', 'MIGRATING', 'MIGRATED', 'FAILED');
--CREATE TYPE page_permission_type AS ENUM ('READ', 'COMMENT', 'EDIT', 'FULL_ACCESS');

-- 페이지 테이블 생성
CREATE TABLE pages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID,
    parent_page_id UUID,
    title VARCHAR(255) NOT NULL,
    icon JSONB,
    cover JSONB,
    properties JSONB,
    is_public BOOLEAN DEFAULT FALSE,
    is_archived BOOLEAN DEFAULT FALSE,
    is_template BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    last_edited_by UUID,
    migration_status VARCHAR(20) DEFAULT 'READY'
);

-- 블록 테이블 생성
CREATE TABLE blocks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    page_id UUID NOT NULL,
    parent_block_id UUID,
    type VARCHAR(255) NOT NULL,
    content JSONB,
    position INTEGER NOT NULL,
    is_archived BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    last_edited_by UUID
);

-- 페이지 권한 테이블 생성
CREATE TABLE page_permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    page_id UUID NOT NULL,
    user_id UUID NOT NULL,
    permission_type VARCHAR(20),
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    granted_by UUID
);