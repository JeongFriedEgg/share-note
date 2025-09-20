package com.sharenote.redistribution.service.shard;

import java.util.List;
import java.util.UUID;

public interface ShardStrategy {
    /**
     * 페이지 ID를 기반으로 샤드를 결정
     */
    String determineShardByPageId(UUID pageId);

    /**
     * 전체 샤드 목록 반환
     */
    List<String> getAllShards();
}
