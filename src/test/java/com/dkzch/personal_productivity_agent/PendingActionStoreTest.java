package com.dkzch.personal_productivity_agent;

import com.dkzch.personal_productivity_agent.common.ConsumeResult;
import com.dkzch.personal_productivity_agent.common.InMemoryPendingActionStore;
import com.dkzch.personal_productivity_agent.common.PendingAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PendingActionStoreTest {

    private static final Long USER_ID = 1L;
    private static final String CONVERSATION_ID = "conv-abc";
    private static final Long OTHER_USER_ID = 99L;
    private static final String OTHER_CONVERSATION_ID = "conv-xyz";

    private InMemoryPendingActionStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryPendingActionStore();
    }

    private PendingAction createAction(LocalDateTime expiresAt) {
        return new PendingAction(
                "action-1", "delete_task",
                Map.of("id", 5L),
                USER_ID, CONVERSATION_ID,
                LocalDateTime.now(), expiresAt);
    }

    @Test
    @DisplayName("保存后可按 actionId 查找")
    void saveAndFind() {
        store.save(createAction(LocalDateTime.now().plusMinutes(10)));

        assertTrue(store.find("action-1").isPresent());
        assertTrue(store.find("action-404").isEmpty());
    }

    @Test
    @DisplayName("四道闸门全过 → SUCCESS 且 consumed=true")
    void consumeSuccessfully() {
        store.save(createAction(LocalDateTime.now().plusMinutes(10)));

        ConsumeResult result = store.consume("action-1", USER_ID, CONVERSATION_ID);

        assertTrue(result.isSuccess());
        assertTrue(result.action().isConsumed());
        assertEquals("delete_task", result.action().getToolName());
    }

    @Test
    @DisplayName("⭐ 重放：第二次消费同一 actionId 被拒")
    void replayRejected() {
        store.save(createAction(LocalDateTime.now().plusMinutes(10)));
        store.consume("action-1", USER_ID, CONVERSATION_ID);

        ConsumeResult second = store.consume("action-1", USER_ID, CONVERSATION_ID);

        assertEquals(ConsumeResult.Outcome.ALREADY_CONSUMED, second.outcome());
    }

    @Test
    @DisplayName("⭐ 越权：其他 userId 消费被拒")
    void wrongUserRejected() {
        store.save(createAction(LocalDateTime.now().plusMinutes(10)));

        ConsumeResult result = store.consume("action-1", OTHER_USER_ID, CONVERSATION_ID);

        assertEquals(ConsumeResult.Outcome.FORBIDDEN, result.outcome());
        // 关键：原待办未被消费，合法用户仍可正常确认
        assertTrue(store.find("action-1").get().isConsumed() == false);
    }

    @Test
    @DisplayName("⭐ 跨会话：其他 conversationId 消费被拒")
    void wrongConversationRejected() {
        store.save(createAction(LocalDateTime.now().plusMinutes(10)));

        ConsumeResult result = store.consume("action-1", USER_ID, OTHER_CONVERSATION_ID);

        assertEquals(ConsumeResult.Outcome.FORBIDDEN, result.outcome());
    }

    @Test
    @DisplayName("过期：consume 时判过期并拒绝")
    void expiredRejected() {
        store.save(createAction(LocalDateTime.now().minusMinutes(1)));

        ConsumeResult result = store.consume("action-1", USER_ID, CONVERSATION_ID);

        assertEquals(ConsumeResult.Outcome.EXPIRED, result.outcome());
    }

    @Test
    @DisplayName("不存在的 actionId 返回 NOT_FOUND")
    void notFound() {
        ConsumeResult result = store.consume("action-404", USER_ID, CONVERSATION_ID);

        assertEquals(ConsumeResult.Outcome.NOT_FOUND, result.outcome());
    }

    @Test
    @DisplayName("FORBIDDEN 后原待办仍可被合法用户消费")
    void forbiddenDoesNotBlockLegitimateUser() {
        store.save(createAction(LocalDateTime.now().plusMinutes(10)));

        store.consume("action-1", OTHER_USER_ID, CONVERSATION_ID);   // 越权尝试
        ConsumeResult legit = store.consume("action-1", USER_ID, CONVERSATION_ID); // 合法确认

        assertTrue(legit.isSuccess());
    }

    @Test
    @DisplayName("⭐ 并发确认：20 个线程同时消费，只有 1 个成功")
    void concurrentConsumeOnlyOneSucceeds() throws Exception {

        store.save(createAction(LocalDateTime.now().plusMinutes(10)));

        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);   // 全部就位
        CountDownLatch start = new CountDownLatch(1);             // 统一发令
        CountDownLatch done = new CountDownLatch(threadCount);    // 全部完成
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();   // 所有线程一起冲，最大化并发冲突概率
                    if (store.consume("action-1", USER_ID, CONVERSATION_ID).isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "并发任务应在 5 秒内完成");
        pool.shutdown();

        assertEquals(1, successCount.get(),
                "20 个并发确认必须只有一个成功，其余被 ALREADY_CONSUMED 拦下");
    }

}


