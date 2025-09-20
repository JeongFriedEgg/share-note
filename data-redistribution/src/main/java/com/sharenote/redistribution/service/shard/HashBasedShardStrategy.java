package com.sharenote.redistribution.service.shard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class HashBasedShardStrategy implements ShardStrategy{
    private final List<String> shards = List.of("legacy", "shard1", "shard2");

    /**
     * UUID의 해시값을 이용한 샤드 결정
     * UUID를 문자열로 변환 후 해시코드를 계산하여 샤드 개수로 나눈 나머지로 결정
     */
    @Override
    public String determineShardByPageId(UUID pageId) {
        if (pageId == null) {
            throw new IllegalArgumentException("페이지 ID는 null일 수 없습니다.");
        }

        // UUID를 문자열로 변환하여 해시코드 계산
        String uuidString = pageId.toString();
        int hashCode = Math.abs(uuidString.hashCode());
        int shardIndex = hashCode % shards.size();

        String selectedShard = shards.get(shardIndex);
        log.debug("페이지 ID {} 해시코드: {}, 선택된 샤드: {}", pageId, hashCode, selectedShard);

        return selectedShard;
    }

    @Override
    public List<String> getAllShards() {
        return new ArrayList<>(shards);
    }
}
