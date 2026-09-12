package com.dkzch.personal_productivity_agent.common;

import java.util.Optional;

/**
 * 待确认动作的存储抽象。
 *
 * <p>V1 用内存实现；将来多实例部署时换 Redis/DB 实现，业务零改动。
 */
public interface PendingActionStore {

    /**
     * 保存待确认动作。action 已包含 expiresAt（由创建方计算），
     * Store 只负责保存与校验，不参与 TTL 决策。
     */
    void save(PendingAction action);

    Optional<PendingAction> find(String actionId);

    /**
     * 原子消费：四道闸门校验 + 标记 consumed 必须在同一个原子操作内完成。
     * 禁止"先 get 检查、再 set"的两步写法（并发重放漏洞）。
     */
    ConsumeResult consume(String actionId, Long userId, String conversationId);
}
