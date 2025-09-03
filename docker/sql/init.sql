CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    authorities VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    created_at TIMESTAMP WITHOUT TIME ZONE
);

--INSERT INTO "user" (username, password, roles)
--VALUES ('testuser', '$2a$10$FDp2CJ7TypXuD7OqZdByrOhLLz.xSoJDNYVUUeymNTzTER/dP4k8y', 'ROLE_USER');

CREATE TABLE IF NOT EXISTS refresh_token (
    id BIGSERIAL PRIMARY KEY,
    refresh_token VARCHAR(500) UNIQUE NOT NULL,
    username VARCHAR(255) NOT NULL,
    expiration_date TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    ip_address VARCHAR(45),
    device_name VARCHAR(255),
    os_name VARCHAR(255),
    browser_name VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_username FOREIGN KEY (username) REFERENCES "users"(username) ON DELETE CASCADE
);

-- 워크스페이스 테이블
CREATE TABLE IF NOT EXISTS workspaces (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES "users"(id) ON DELETE SET NULL
);

-- 워크스페이스 멤버십 테이블
CREATE TABLE IF NOT EXISTS workspace_members (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES "users"(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('owner', 'admin', 'member', 'guest')),
    joined_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(workspace_id, user_id)
);

-- 페이지 테이블
CREATE TABLE IF NOT EXISTS pages (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
    parent_page_id BIGINT REFERENCES pages(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    icon JSONB,
    cover JSONB,
    properties JSONB, -- 페이지 속성들 (데이터베이스 뷰에서 사용)
    is_public BOOLEAN DEFAULT false, -- 페이지 공개 여부
    is_archived BOOLEAN DEFAULT false, -- 소프트 삭제(휴지통 기능)
    is_template BOOLEAN DEFAULT false, -- 템플릿 페이지 여부(새 페이지 생성시 템플릿으로 사용 가능)
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES "users"(id) ON DELETE SET NULL,
    last_edited_by BIGINT REFERENCES "users"(id) ON DELETE SET NULL,
);

-- 페이지 권한 테이블
CREATE TABLE IF NOT EXISTS page_permissions (
    id BIGSERIAL PRIMARY KEY,
    page_id BIGINT REFERENCES pages(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES "users"(id) ON DELETE CASCADE,
    permission VARCHAR(20) NOT NULL CHECK (permission IN ('read', 'comment', 'edit', 'full_access')),
    granted_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    granted_by BIGINT REFERENCES "users"(id) ON DELETE SET NULL,
    UNIQUE(page_id, user_id)
);

-- 블록 테이블
CREATE TABLE IF NOT EXISTS blocks (
    id BIGSERIAL PRIMARY KEY,
    page_id BIGINT REFERENCES pages(id) ON DELETE CASCADE,
    parent_block_id BIGINT REFERENCES blocks(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL, -- paragraph, heading_1, heading_2, bulleted_list_item, etc.
    content JSONB NOT NULL, -- 블록별 고유한 콘텐츠
    position INTEGER NOT NULL, -- 같은 부모 내에서의 순서
    is_archived BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES "users"(id) ON DELETE SET NULL,
    last_edited_by BIGINT REFERENCES "users"(id) ON DELETE SET NULL
);

-- 인덱스 생성
-- workspace_members 인덱스
CREATE INDEX idx_workspace_members_workspace_id ON workspace_members(workspace_id);
CREATE INDEX idx_workspace_members_user_id ON workspace_members(user_id);

-- pages 인덱스
CREATE INDEX idx_pages_workspace_id ON pages(workspace_id);
CREATE INDEX idx_pages_parent_page_id ON pages(parent_page_id);

-- page_permissions 인덱스
CREATE INDEX idx_page_permissions_page_id ON page_permissions(page_id);
CREATE INDEX idx_page_permissions_user_id ON page_permissions(user_id);

-- blocks 인덱스
CREATE INDEX idx_blocks_page_id ON blocks(page_id);
CREATE INDEX idx_blocks_parent_block_id ON blocks(parent_block_id);
CREATE INDEX idx_blocks_position ON blocks(page_id, parent_block_id, position);

-- GIN 인덱스
CREATE INDEX idx_blocks_content_gin ON blocks USING GIN (content);
CREATE INDEX idx_pages_properties_gin ON pages USING GIN (properties);