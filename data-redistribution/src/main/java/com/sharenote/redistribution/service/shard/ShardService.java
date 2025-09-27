package com.sharenote.redistribution.service.shard;

import com.sharenote.redistribution.config.DatabaseConfig;
import com.sharenote.redistribution.exception.custom.ShardException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@Component
public class ShardService {

    /**
     * 페이지 ID를 기반으로 샤드를 결정하는 메서드
     * @param pageId
     * @return
     */
    public String determineTargetShard(UUID pageId) {
        if (pageId == null) {
            throw new ShardException("페이지 ID는 null이 될 수 없습니다.");
        }

        // UUID의 hashCode를 사용하여 샤드 결정
        int hash = Math.abs(pageId.hashCode());
        int shardIndex = hash % 3; // 3개의 샤드로 분산

        String targetShard;
        switch (shardIndex) {
            case 0:
                targetShard = "legacy";
                break;
            case 1:
                targetShard = "shard1";
                break;
            case 2:
                targetShard = "shard2";
                break;
            default:
                targetShard = "legacy";
        }

        log.debug("페이지 ID {} -> 대상 샤드: {}",pageId,targetShard);
        return targetShard;
    }
}
