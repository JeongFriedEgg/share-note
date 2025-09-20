package com.sharenote.redistribution.service.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {
    private final RedissonClient redissonClient;

    @Value("${app.distributed-lock.lock.lease-time:300000}")
    private long leaseTime;

    @Value("${app.distributed-lock.lock.wait-time:30000}")
    private long waitTime;

    /**
     * 페이지별 분산락 획득
     */
    public boolean acquirePageLock(UUID pageId) {
        String lockKey = "page-migration-lock:" + pageId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS);
            if (acquired) {
                log.debug("페이지 {} 분산락 획득 성공", pageId);
            } else {
                log.warn("페이지 {} 분산락 획득 실패 - 대기시간 초과", pageId);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("페이지 {} 분산락 획득 중 인터럽트 발생", pageId, e);
            return false;
        }
    }

    /**
     * 페이지별 분산락 해제
     */
    public void releasePageLock(UUID pageId) {
        String lockKey = "page-migration-lock:" + pageId;
        RLock lock = redissonClient.getLock(lockKey);

        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("페이지 {} 분산락 해제 완료", pageId);
        } else {
            log.warn("페이지 {} 분산락이 현재 스레드에 의해 보유되지 않음", pageId);
        }
    }

    /**
     * 분산락과 함께 작업 실행
     */
    public <T> T executeWithLock(UUID pageId, Supplier<T> task) {
        if (!acquirePageLock(pageId)) {
            throw new RuntimeException("페이지 " + pageId + "에 대한 분산락 획득에 실패했습니다.");
        }

        try {
            return task.get();
        } finally {
            releasePageLock(pageId);
        }
    }

    /**
     * 분산락과 함께 작업 실행 (반환값 없음)
     */
    public void executeWithLock(UUID pageId, Runnable task) {
        executeWithLock(pageId, () -> {
            task.run();
            return null;
        });
    }
}
