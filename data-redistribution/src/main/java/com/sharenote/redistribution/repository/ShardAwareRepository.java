package com.sharenote.redistribution.repository;

import com.sharenote.redistribution.entity.Block;
import com.sharenote.redistribution.entity.Page;
import com.sharenote.redistribution.entity.PagePermission;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShardAwareRepository {

    private final Map<String, EntityManagerFactory> entityManagerFactories = new ConcurrentHashMap<>();
    private final Map<String, PlatformTransactionManager> transactionManagers = new ConcurrentHashMap<>();

    // EntityManagerFactory 주입
    private final @Qualifier("legacyEntityManagerFactory") EntityManagerFactory legacyEntityManagerFactory;
    private final @Qualifier("shard1EntityManagerFactory") EntityManagerFactory shard1EntityManagerFactory;
    private final @Qualifier("shard2EntityManagerFactory") EntityManagerFactory shard2EntityManagerFactory;

    // TransactionManager 주입
    private final @Qualifier("legacyTransactionManager") PlatformTransactionManager legacyTransactionManager;
    private final @Qualifier("shard1TransactionManager") PlatformTransactionManager shard1TransactionManager;
    private final @Qualifier("shard2TransactionManager") PlatformTransactionManager shard2TransactionManager;

    @PostConstruct
    public void initializeManagers() {
        // EntityManagerFactory 매핑 초기화
        entityManagerFactories.put("legacy", legacyEntityManagerFactory);
        entityManagerFactories.put("shard1", shard1EntityManagerFactory);
        entityManagerFactories.put("shard2", shard2EntityManagerFactory);

        // TransactionManager 매핑 초기화
        transactionManagers.put("legacy", legacyTransactionManager);
        transactionManagers.put("shard1", shard1TransactionManager);
        transactionManagers.put("shard2", shard2TransactionManager);

        log.info("ShardAware Repository 초기화 완료 - EntityManagerFactories: {}, TransactionManagers: {}",
                entityManagerFactories.keySet(), transactionManagers.keySet());
    }

    /**
     * 샤드별 EntityManager 반환 (새 EntityManager 생성)
     */
    public EntityManager getEntityManager(String shardName) {
        EntityManagerFactory emf = entityManagerFactories.get(shardName);
        if (emf == null) {
            throw new IllegalArgumentException("알 수 없는 샤드: " + shardName);
        }
        return emf.createEntityManager();
    }

    /**
     * 샤드별 TransactionManager 반환
     */
    public PlatformTransactionManager getTransactionManager(String shardName) {
        PlatformTransactionManager tm = transactionManagers.get(shardName);
        if (tm == null) {
            throw new IllegalArgumentException("알 수 없는 샤드: " + shardName);
        }
        return tm;
    }

    /**
     * 특정 샤드에 페이지 저장 (외부에서 EntityManager 관리)
     */
    public void savePageToShard(String shardName, Page page, EntityManager entityManager) {
        entityManager.persist(page);
        log.debug("페이지 {} 를 {} 샤드에 저장", page.getId(), shardName);
    }

    /**
     * 특정 샤드에 블록 저장 (외부에서 EntityManager 관리)
     */
    public void saveBlockToShard(String shardName, Block block, EntityManager entityManager) {
        entityManager.persist(block);
    }

    /**
     * 특정 샤드에 페이지 권한 저장 (외부에서 EntityManager 관리)
     */
    public void savePagePermissionToShard(String shardName, PagePermission permission, EntityManager entityManager) {
        entityManager.persist(permission);
    }

    /**
     * 배치 저장을 위한 flush 및 clear (외부에서 EntityManager 관리)
     */
    public void flushAndClearShard(String shardName, EntityManager entityManager) {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * 편의 메서드: EntityManager 자동 생성 및 관리하는 페이지 저장
     */
    public void savePageToShard(String shardName, Page page) {
        try (EntityManager em = getEntityManager(shardName)) {
            em.getTransaction().begin();
            try {
                savePageToShard(shardName, page, em);
                em.getTransaction().commit();
            } catch (Exception e) {
                em.getTransaction().rollback();
                throw e;
            }
        }
    }
}
