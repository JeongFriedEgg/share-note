package com.sharenote.redistribution.service.lock;

import com.sharenote.redistribution.exception.custom.DistributedLockException;
import com.sharenote.redistribution.exception.custom.LockAcquisitionException;
import com.sharenote.redistribution.exception.custom.RedisConnectionException;
import com.sharenote.redistribution.properties.RedissonProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {
    private final RedissonClient redissonClient;
    private final RedissonProperties redissonProperties;

    /**
     * 분산락을 획득하고 작업을 실행하는 메서드
     * @param lockKey   락 키
     * @param waitTime  락 대기시간 (초)
     * @param leaseTime 락 유지시간 (초)
     * @param task      실행할 작업
     * @return 작업 결과
     * @throws RedisConnectionException Redis 연결 실패 시
     * @throws LockAcquisitionException 락 획득 실패 시
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, Supplier<T> task) {
        // 1. Redis 연결 상태 확인
        validateRedisConnection();

        String fullLockKey = redissonProperties.getLock().buildLockKey(lockKey);
        RLock lock = redissonClient.getLock(fullLockKey);

        try {
            log.debug("분산락 획득 시도: {} (대기: {}초, 유지: {}초)", fullLockKey, waitTime, leaseTime);

            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

            if (!acquired) {
                log.error("락 획득 실패 (타임아웃): {} - 다른 프로세스에서 이미 사용 중일 수 있습니다", fullLockKey);
                throw new LockAcquisitionException("락 획득에 실패했습니다: " + fullLockKey);
            }

            log.debug("분산락 획득 성공: {}", fullLockKey);

            // 2. 락 획득 후 다시 한번 연결 상태 확인
            validateRedisConnection();

            return task.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("락 획득 중 인터럽트 발생: {}", fullLockKey, e);
            throw new LockAcquisitionException("락 획득 중 인터럽트가 발생했습니다: " + fullLockKey, e);

        } catch (LockAcquisitionException | RedisConnectionException e) {
            // 이미 정의된 예외들은 그대로 재던짐
            throw e;

        } catch (Exception e) {
            // Redis 네트워크 오류나 기타 예상치 못한 예외
            log.error("분산락 처리 중 예상치 못한 오류 발생: {}", fullLockKey, e);

            // Redis 관련 예외인지 확인
            if (isRedisRelatedError(e)) {
                throw new RedisConnectionException("Redis 통신 중 오류 발생: " + fullLockKey, e);
            } else {
                throw new DistributedLockException("분산락 처리 중 오류가 발생했습니다: " + fullLockKey, e);
            }

        } finally {
            // 3. 락 해제
            releaseLockSafely(lock, fullLockKey);
        }
    }

    /**
     * 반환값이 없는 작업을 위한 분산락 실행 메서드
     */
    public void executeWithLock(String lockKey, long waitTime, long leaseTime, Runnable task) {
        executeWithLock(lockKey, waitTime, leaseTime, () -> {
            task.run();
            return null;
        });
    }

    /**
     * Redis 연결 상태를 검증하는 메서드
     * @throws RedisConnectionException Redis 연결 실패 시
     */
    private void validateRedisConnection() {
        if (redissonClient.isShutdown()) {
            log.error("RedissonClient가 종료된 상태입니다");
            throw new RedisConnectionException("RedissonClient가 종료되었습니다");
        }

        try {
            // 단일 서버 모드에서 연결 상태 확인
            RBucket<String> testBucket = redissonClient.getBucket("__health_check__");
            testBucket.set("pong", 1, TimeUnit.SECONDS);

            log.trace("Redis 연결 상태 확인 완료");

        } catch (Exception e) {
            log.error("Redis 연결 확인 실패", e);
            throw new RedisConnectionException("Redis와의 연결이 끊어졌습니다", e);
        }
    }

    /**
     * 안전한 락 해제 메서드
     */
    private void releaseLockSafely(RLock lock, String fullLockKey) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("분산락 해제 완료: {}", fullLockKey);
            } else {
                log.warn("현재 스레드가 보유하지 않은 락 해제 시도: {}", fullLockKey);
            }
        } catch (Exception e) {
            log.error("분산락 해제 중 오류 발생 (무시함): {}", fullLockKey, e);
            // 락 해제 실패는 로그만 남기고 예외를 던지지 않음
            // Watchdog이 자동으로 만료시킬 것임
        }
    }

    /**
     * Redis 관련 오류인지 확인하는 메서드
     */
    private boolean isRedisRelatedError(Exception e) {
        String errorMessage = e.getMessage();
        String exceptionClass = e.getClass().getSimpleName();

        // Redis/Netty/연결 관련 예외 패턴 확인
        return errorMessage != null && (
                errorMessage.contains("connection") ||
                        errorMessage.contains("redis") ||
                        errorMessage.contains("timeout") ||
                        errorMessage.contains("network")
        ) || exceptionClass.contains("Redis") ||
                exceptionClass.contains("Netty") ||
                exceptionClass.contains("Connection");
    }
}
