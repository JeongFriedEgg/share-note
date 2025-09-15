CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    authorities VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

--INSERT INTO "user" (username, password, roles)
--VALUES ('testuser', '$2a$10$FDp2CJ7TypXuD7OqZdByrOhLLz.xSoJDNYVUUeymNTzTER/dP4k8y', 'ROLE_USER');

CREATE TABLE IF NOT EXISTS refresh_token (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES "users"(id) ON DELETE SET NULL
);

-- 워크스페이스 멤버십 테이블
CREATE TABLE IF NOT EXISTS workspace_members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID REFERENCES "users"(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('owner', 'admin', 'member', 'guest')),
    joined_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(workspace_id, user_id)
);

-- 페이지 테이블
CREATE TABLE IF NOT EXISTS pages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    parent_page_id UUID REFERENCES pages(id) ON DELETE CASCADE,
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
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    page_id UUID REFERENCES pages(id) ON DELETE CASCADE,
    user_id UUID REFERENCES "users"(id) ON DELETE CASCADE,
    permission VARCHAR(20) NOT NULL CHECK (permission IN ('read', 'comment', 'edit', 'full_access')),
    granted_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    granted_by UUID REFERENCES "users"(id) ON DELETE SET NULL,
    UNIQUE(page_id, user_id)
);

-- 블록 테이블
CREATE TABLE IF NOT EXISTS blocks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    page_id UUID REFERENCES pages(id) ON DELETE CASCADE,
    parent_block_id UUID REFERENCES blocks(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL, -- paragraph, heading_1, heading_2, bulleted_list_item, etc.
    content JSONB NOT NULL, -- 블록별 고유한 콘텐츠
    position INTEGER NOT NULL, -- 같은 부모 내에서의 순서
    is_archived BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES "users"(id) ON DELETE SET NULL,
    last_edited_by BIGINT REFERENCES "users"(id) ON DELETE SET NULL
);

-- 인덱스 생성
-- 사용자 관련 인덱스
CREATE INDEX idx_users_username ON users (username);

-- pages 인덱스
CREATE INDEX idx_pages_workspace ON pages (workspace_id, is_archived) WHERE is_archived = false;
CREATE INDEX idx_pages_created_by ON pages (created_by);

-- page_permissions 인덱스
CREATE INDEX idx_page_permissions_page_id_user_id ON page_permissions(page_id, user_id);

-- blocks 인덱스
CREATE INDEX idx_blocks_page_id_is_archived_position ON blocks(page_id, is_archived, position);
CREATE INDEX idx_blocks_parent_position ON blocks (parent_block_id, position)
WHERE parent_block_id IS NOT NULL AND is_archived = false;



-- 페이지 테이블 icon 검증 함수
CREATE OR REPLACE FUNCTION validate_icon_jsonb(icon_data JSONB)
RETURNS BOOLEAN AS $$
DECLARE
    icon_type VARCHAR;
BEGIN
    -- NULL 값은 유효함 (선택사항)
    IF icon_data IS NULL THEN
        RETURN TRUE;
    END IF;

    -- 객체 타입 확인
    IF jsonb_typeof(icon_data) != 'object' THEN
        RETURN FALSE;
    END IF;

    -- type 필드 존재 확인
    IF NOT (icon_data ? 'type') THEN
        RETURN FALSE;
    END IF;

    icon_type := icon_data->>'type';
    CASE icon_type
        WHEN 'emoji' THEN
            -- emoji 필드 존재 및 문자열 타입 확인
            RETURN (icon_data ? 'emoji')
                   AND jsonb_typeof(icon_data->'emoji') = 'string'
                   AND LENGTH(icon_data->>'emoji') > 0;

        WHEN 'file' THEN
            -- file 객체 및 필수 필드들 확인
            IF NOT (icon_data ? 'file') OR jsonb_typeof(icon_data->'file') != 'object' THEN
                RETURN FALSE;
            END IF;

            -- url과 name 필드 존재 및 문자열 타입 확인
            RETURN (icon_data->'file' ? 'url')
                   AND (icon_data->'file' ? 'name')
                   AND jsonb_typeof(icon_data->'file'->'url') = 'string'
                   AND jsonb_typeof(icon_data->'file'->'name') = 'string'
                   AND LENGTH(icon_data->'file'->>'url') > 0
                   AND LENGTH(icon_data->'file'->>'name') > 0;

        WHEN 'external' THEN
            -- external 타입 지원 (향후 확장성을 위해)
            IF NOT (icon_data ? 'external') OR jsonb_typeof(icon_data->'external') != 'object' THEN
                RETURN FALSE;
            END IF;

            RETURN (icon_data->'external' ? 'url')
                   AND jsonb_typeof(icon_data->'external'->'url') = 'string'
                   AND LENGTH(icon_data->'external'->>'url') > 0;
        ELSE
            RETURN FALSE;
    END CASE;
END;
$$ LANGUAGE plpgsql;

-- 페이지 테이블 cover 검증 함수
CREATE OR REPLACE FUNCTION validate_cover_jsonb(cover_data JSONB)
RETURNS BOOLEAN AS $$
DECLARE
    cover_type VARCHAR;
BEGIN
    -- NULL 값은 유효함 (선택사항)
    IF cover_data IS NULL THEN
        RETURN TRUE;
    END IF;

    -- 객체 타입 확인
    IF jsonb_typeof(cover_data) != 'object' THEN
        RETURN FALSE;
    END IF;

    -- type 필드 존재 확인
    IF NOT (cover_data ? 'type') THEN
        RETURN FALSE;
    END IF;

    cover_type := cover_data->>'type';
    CASE cover_type
        WHEN 'external' THEN
            -- external 객체 및 url 필드 확인
            IF NOT (cover_data ? 'external') OR jsonb_typeof(cover_data->'external') != 'object' THEN
                RETURN FALSE;
            END IF;

            RETURN (cover_data->'external' ? 'url')
                   AND jsonb_typeof(cover_data->'external'->'url') = 'string'
                   AND LENGTH(cover_data->'external'->>'url') > 0;

        WHEN 'file' THEN
            -- file 객체 및 필수 필드들 확인
            IF NOT (cover_data ? 'file') OR jsonb_typeof(cover_data->'file') != 'object' THEN
                RETURN FALSE;
            END IF;

            -- url과 name 필드 존재 및 문자열 타입 확인
            IF NOT ((cover_data->'file' ? 'url') AND (cover_data->'file' ? 'name')) THEN
                RETURN FALSE;
            END IF;

            IF NOT (jsonb_typeof(cover_data->'file'->'url') = 'string'
                   AND jsonb_typeof(cover_data->'file'->'name') = 'string') THEN
                RETURN FALSE;
            END IF;

            IF NOT (LENGTH(cover_data->'file'->>'url') > 0
                   AND LENGTH(cover_data->'file'->>'name') > 0) THEN
                RETURN FALSE;
            END IF;

            -- 선택적 필드들 검증 (있으면 올바른 타입이어야 함)
            IF cover_data->'file' ? 'upload_date' THEN
                -- ISO 8601 날짜 형식인지 간단히 확인 (정확한 파싱은 애플리케이션에서)
                IF NOT (jsonb_typeof(cover_data->'file'->'upload_date') = 'string'
                       AND cover_data->'file'->>'upload_date' ~ '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}') THEN
                    RETURN FALSE;
                END IF;
            END IF;

            IF cover_data->'file' ? 'size' THEN
                IF NOT (jsonb_typeof(cover_data->'file'->'size') = 'number') THEN
                    RETURN FALSE;
                END IF;
            END IF;

            RETURN TRUE;

        WHEN 'gradient' THEN
            -- gradient 타입 지원 (향후 확장성을 위해)
            IF NOT (cover_data ? 'gradient') OR jsonb_typeof(cover_data->'gradient') != 'object' THEN
                RETURN FALSE;
            END IF;

            -- gradient 타입과 colors 배열 확인
            RETURN (cover_data->'gradient' ? 'type')
                   AND (cover_data->'gradient' ? 'colors')
                   AND jsonb_typeof(cover_data->'gradient'->'colors') = 'array';
        ELSE
            RETURN FALSE;
    END CASE;
END;
$$ LANGUAGE plpgsql;

-- 페이지 메타데이터 검증 함수 통합
CREATE OR REPLACE FUNCTION validate_page_metadata(icon_data JSONB, cover_data JSONB, properties_data JSONB)
RETURNS TABLE(field_name TEXT, is_valid BOOLEAN, error_message TEXT) AS $$
BEGIN
    -- 아이콘 검증
    IF NOT validate_icon_jsonb(icon_data) THEN
        RETURN QUERY SELECT 'icon'::TEXT, FALSE,
            CASE
                WHEN icon_data IS NULL THEN 'Icon is null but validation failed'
                WHEN NOT (icon_data ? 'type') THEN 'Icon must have a "type" field'
                WHEN icon_data->>'type' = 'emoji' AND NOT (icon_data ? 'emoji') THEN 'Emoji icon must have "emoji" field'
                WHEN icon_data->>'type' = 'file' AND NOT (icon_data ? 'file') THEN 'File icon must have "file" object'
                WHEN icon_data->>'type' = 'file' AND NOT (icon_data->'file' ? 'url') THEN 'File icon must have "url" field'
                WHEN icon_data->>'type' = 'file' AND NOT (icon_data->'file' ? 'name') THEN 'File icon must have "name" field'
                ELSE format('Invalid icon type: %s. Only "emoji", "file", "external" are allowed', icon_data->>'type')
            END::TEXT;
    ELSE
        RETURN QUERY SELECT 'icon'::TEXT, TRUE, NULL::TEXT;
    END IF;

    -- 커버 검증
    IF NOT validate_cover_jsonb(cover_data) THEN
        RETURN QUERY SELECT 'cover'::TEXT, FALSE,
            CASE
                WHEN cover_data IS NULL THEN 'Cover is null but validation failed'
                WHEN NOT (cover_data ? 'type') THEN 'Cover must have a "type" field'
                WHEN cover_data->>'type' = 'external' AND NOT (cover_data ? 'external') THEN 'External cover must have "external" object'
                WHEN cover_data->>'type' = 'external' AND NOT (cover_data->'external' ? 'url') THEN 'External cover must have "url" field'
                WHEN cover_data->>'type' = 'file' AND NOT (cover_data ? 'file') THEN 'File cover must have "file" object'
                WHEN cover_data->>'type' = 'file' AND NOT (cover_data->'file' ? 'url') THEN 'File cover must have "url" field'
                WHEN cover_data->>'type' = 'file' AND NOT (cover_data->'file' ? 'name') THEN 'File cover must have "name" field'
                ELSE format('Invalid cover type: %s. Only "external", "file", "gradient" are allowed', cover_data->>'type')
            END::TEXT;
    ELSE
        RETURN QUERY SELECT 'cover'::TEXT, TRUE, NULL::TEXT;
    END IF;

    -- properties 검증 (기본적으로 객체 타입이면 OK, 세부 검증은 애플리케이션에서)
    IF properties_data IS NOT NULL AND jsonb_typeof(properties_data) != 'object' THEN
        RETURN QUERY SELECT 'properties'::TEXT, FALSE, 'Properties must be a JSON object'::TEXT;
    ELSE
        RETURN QUERY SELECT 'properties'::TEXT, TRUE, NULL::TEXT;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- 페이지 메타데이터 트리거 함수
CREATE OR REPLACE FUNCTION check_page_metadata_trigger()
RETURNS TRIGGER AS $$
DECLARE
    validation_record RECORD;
    error_messages TEXT[] := ARRAY[]::TEXT[];
    final_error_message TEXT;
BEGIN
    -- 기본 필드 검증
    IF NEW.title IS NULL OR LENGTH(TRIM(NEW.title)) = 0 THEN
        RAISE EXCEPTION 'Page title cannot be null or empty';
    END IF;

    IF NEW.workspace_id IS NULL THEN
        RAISE EXCEPTION 'Page must belong to a workspace';
    END IF;

    -- 메타데이터 검증 수행
    FOR validation_record IN
        SELECT * FROM validate_page_metadata(NEW.icon, NEW.cover, NEW.properties)
    LOOP
        IF NOT validation_record.is_valid THEN
            error_messages := array_append(error_messages,
                format('%s: %s', validation_record.field_name, validation_record.error_message));
        END IF;
    END LOOP;

    -- 오류가 있으면 예외 발생
    IF array_length(error_messages, 1) > 0 THEN
        final_error_message := 'Page validation failed: ' || array_to_string(error_messages, '; ');
        RAISE EXCEPTION '%', final_error_message;
    END IF;

    -- 추가 비즈니스 로직 검증
    -- 자기 자신을 부모로 설정하는 것 방지
    IF NEW.parent_page_id = NEW.id THEN
        RAISE EXCEPTION 'Page cannot be its own parent';
    END IF;

    -- updated_at 자동 설정
    NEW.updated_at := CURRENT_TIMESTAMP;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 트리거 생성
CREATE TRIGGER pages_metadata_validation_trigger
BEFORE INSERT OR UPDATE ON pages
FOR EACH ROW
EXECUTE FUNCTION check_page_metadata_trigger();






-- 블록 콘텐츠 검증 함수
CREATE OR REPLACE FUNCTION validate_block_content(block_type VARCHAR, content_data JSONB)
RETURNS BOOLEAN AS $$
DECLARE
    rich_text_item JSONB;
    icon_type VARCHAR;
    media_type VARCHAR;
    cell_array JSONB;
    cell_item JSONB;
BEGIN
    CASE block_type
        WHEN 'paragraph', 'heading_1', 'heading_2', 'heading_3', 'bulleted_list_item', 'numbered_list_item', 'toggle', 'quote' THEN
            RETURN validate_rich_text_content(content_data);

        WHEN 'code' THEN
            -- rich_text 검증 + language 필드 체크
            IF NOT validate_rich_text_content(content_data) THEN
                RETURN FALSE;
            END IF;

            -- language 필드 검증 (선택사항이지만 있으면 문자열이어야 함)
            IF content_data ? 'language' AND jsonb_typeof(content_data->'language') != 'string' THEN
                RETURN FALSE;
            END IF;
            RETURN TRUE;

        WHEN 'to_do' THEN
            -- rich_text 검증 + checked 필드 체크
            IF NOT validate_rich_text_content(content_data) THEN
                RETURN FALSE;
            END IF;

            IF NOT (content_data ? 'checked') OR jsonb_typeof(content_data->'checked') != 'boolean' THEN
                RETURN FALSE;
            END IF;
            RETURN TRUE;

        WHEN 'callout' THEN
            -- rich_text 검증
            IF NOT validate_rich_text_content(content_data) THEN
                RETURN FALSE;
            END IF;

            -- icon 검증
            IF NOT validate_icon(content_data->'icon') THEN
                RETURN FALSE;
            END IF;
            RETURN TRUE;

        WHEN 'divider' THEN
            -- divider는 빈 객체이거나 color 정도만 가질 수 있음
            RETURN TRUE;

        WHEN 'image', 'video', 'file' THEN
            RETURN validate_media_content(content_data);

        WHEN 'table' THEN
            -- 모든 테이블 관련 필드 검증
            IF NOT (content_data ? 'table_width') OR jsonb_typeof(content_data->'table_width') != 'number' THEN
                RETURN FALSE;
            END IF;

            -- 선택적 필드들 검증
            IF content_data ? 'has_column_header' AND jsonb_typeof(content_data->'has_column_header') != 'boolean' THEN
                RETURN FALSE;
            END IF;

            IF content_data ? 'has_row_header' AND jsonb_typeof(content_data->'has_row_header') != 'boolean' THEN
                RETURN FALSE;
            END IF;
            RETURN TRUE;

        WHEN 'table_row' THEN
            RETURN validate_table_row_content(content_data);

        WHEN 'database' THEN
            RETURN content_data ? 'properties' AND jsonb_typeof(content_data->'properties') = 'object';

        ELSE
            -- 알려지지 않은 블록 타입에 대한 로깅
            RAISE NOTICE 'Unknown block type: %', block_type;
            RETURN FALSE;
    END CASE;
END;
$$ LANGUAGE plpgsql;

-- 리치 텍스트 검증 헬퍼 함수
CREATE OR REPLACE FUNCTION validate_rich_text_content(content_data JSONB)
RETURNS BOOLEAN AS $$
DECLARE
    rich_text_item JSONB;
BEGIN
    -- rich_text 필드 존재 및 배열 타입 확인
    IF NOT (content_data ? 'rich_text') OR jsonb_typeof(content_data->'rich_text') != 'array' THEN
        RETURN FALSE;
    END IF;

    -- rich_text 배열 내 각 객체 검증
    FOR rich_text_item IN SELECT * FROM jsonb_array_elements(content_data->'rich_text') LOOP
        -- 필수 필드 확인
        IF NOT (rich_text_item ? 'type') OR NOT (rich_text_item ? 'plain_text') THEN
            RETURN FALSE;
        END IF;

        -- type별 추가 검증
        CASE rich_text_item->>'type'
            WHEN 'text' THEN
                -- text 객체 검증 (선택사항)
                IF rich_text_item ? 'text' THEN
                    IF NOT (rich_text_item->'text' ? 'content') THEN
                        RETURN FALSE;
                    END IF;
                END IF;

            WHEN 'mention' THEN
                -- mention 객체 검증
                IF NOT (rich_text_item ? 'mention') THEN
                    RETURN FALSE;
                END IF;

            WHEN 'equation' THEN
                -- equation 객체 검증
                IF NOT (rich_text_item ? 'equation') OR NOT (rich_text_item->'equation' ? 'expression') THEN
                    RETURN FALSE;
                END IF;
        END CASE;

        -- annotations 검증 (선택사항이지만 있으면 객체여야 함)
        IF rich_text_item ? 'annotations' AND jsonb_typeof(rich_text_item->'annotations') != 'object' THEN
            RETURN FALSE;
        END IF;
    END LOOP;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- 아이콘 검증 헬퍼 함수
CREATE OR REPLACE FUNCTION validate_icon(icon_data JSONB)
RETURNS BOOLEAN AS $$
DECLARE
    icon_type VARCHAR;
BEGIN
    -- icon이 null이면 유효함 (선택사항)
    IF icon_data IS NULL THEN
        RETURN TRUE;
    END IF;

    -- 객체 타입 확인
    IF jsonb_typeof(icon_data) != 'object' OR NOT (icon_data ? 'type') THEN
        RETURN FALSE;
    END IF;

    icon_type := icon_data->>'type';
    CASE icon_type
        WHEN 'emoji' THEN
            RETURN icon_data ? 'emoji' AND jsonb_typeof(icon_data->'emoji') = 'string';

        WHEN 'file' THEN
            RETURN icon_data ? 'file'
                   AND (icon_data->'file' ? 'url')
                   AND jsonb_typeof(icon_data->'file'->'url') = 'string';

        WHEN 'external' THEN
            RETURN icon_data ? 'external'
                   AND (icon_data->'external' ? 'url')
                   AND jsonb_typeof(icon_data->'external'->'url') = 'string';
        ELSE
            RETURN FALSE;
    END CASE;
END;
$$ LANGUAGE plpgsql;

-- 미디어 콘텐츠 검증 헬퍼 함수
CREATE OR REPLACE FUNCTION validate_media_content(content_data JSONB)
RETURNS BOOLEAN AS $$
DECLARE
    media_type VARCHAR;
BEGIN
    -- type 필드 확인
    IF NOT (content_data ? 'type') THEN
        RETURN FALSE;
    END IF;

    media_type := content_data->>'type';
    CASE media_type
        WHEN 'file' THEN
            RETURN (content_data ? 'file')
                   AND (content_data->'file' ? 'url')
                   AND jsonb_typeof(content_data->'file'->'url') = 'string';

        WHEN 'external' THEN
            RETURN (content_data ? 'external')
                   AND (content_data->'external' ? 'url')
                   AND jsonb_typeof(content_data->'external'->'url') = 'string';
        ELSE
            RETURN FALSE;
    END CASE;
END;
$$ LANGUAGE plpgsql;

-- 테이블 행 검증 헬퍼 함수
CREATE OR REPLACE FUNCTION validate_table_row_content(content_data JSONB)
RETURNS BOOLEAN AS $$
DECLARE
    cell_array JSONB;
    cell_item JSONB;
BEGIN
    -- cells 필드 확인
    IF NOT (content_data ? 'cells') OR jsonb_typeof(content_data->'cells') != 'array' THEN
        RETURN FALSE;
    END IF;

    -- 각 셀 배열 검증
    FOR cell_array IN SELECT * FROM jsonb_array_elements(content_data->'cells') LOOP
        IF jsonb_typeof(cell_array) != 'array' THEN
            RETURN FALSE;
        END IF;

        -- 각 셀의 리치 텍스트 검증
        FOR cell_item IN SELECT * FROM jsonb_array_elements(cell_array) LOOP
            IF NOT (cell_item ? 'type') OR NOT (cell_item ? 'plain_text') THEN
                RETURN FALSE;
            END IF;
        END LOOP;
    END LOOP;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- 트리거 함수
CREATE OR REPLACE FUNCTION check_block_content_trigger()
RETURNS TRIGGER AS $$
DECLARE
    validation_result BOOLEAN;
    error_context TEXT;
BEGIN
    -- 블록 타입 유효성 사전 체크
    IF NEW.type IS NULL OR LENGTH(TRIM(NEW.type)) = 0 THEN
        RAISE EXCEPTION 'Block type cannot be null or empty';
    END IF;

    -- 콘텐츠 유효성 검사
    BEGIN
        validation_result := validate_block_content(NEW.type, NEW.content);
    EXCEPTION WHEN OTHERS THEN
        -- 검증 중 오류 발생 시 상세 정보 제공
        GET STACKED DIAGNOSTICS error_context = PG_EXCEPTION_CONTEXT;
        RAISE EXCEPTION 'Validation error for block type "%": % Context: %',
                       NEW.type, SQLERRM, error_context;
    END;

    -- 검증 실패 시 상세한 오류 메시지
    IF NOT validation_result THEN
        RAISE EXCEPTION 'Invalid block content structure for type "%". Content: %',
                       NEW.type, NEW.content::text;
    END IF;

    -- 성공 시 로깅 (개발 환경에서만)
    -- RAISE NOTICE 'Block validation passed for type: %', NEW.type;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 트리거 생성
CREATE TRIGGER blocks_content_validation_trigger
BEFORE INSERT OR UPDATE ON blocks
FOR EACH ROW
EXECUTE FUNCTION check_block_content_trigger();