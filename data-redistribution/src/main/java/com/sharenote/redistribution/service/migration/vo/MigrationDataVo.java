package com.sharenote.redistribution.service.migration.vo;

import com.sharenote.redistribution.entity.Block;
import com.sharenote.redistribution.entity.Page;
import com.sharenote.redistribution.entity.PagePermission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationDataVo {
    private Page page;

    @Builder.Default
    private List<Block> blocks = new ArrayList<>();

    @Builder.Default
    private List<PagePermission> permissions = new ArrayList<>();

    public UUID getPageId() {
        return page != null ? page.getId() : null;
    }
}
