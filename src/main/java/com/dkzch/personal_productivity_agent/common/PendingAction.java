package com.dkzch.personal_productivity_agent.common;

import java.time.LocalDateTime;
import java.util.Map;

//一次"等待用户确认"的高风险操作。
public class PendingAction {

    private final String actionId;
    private final String toolName;
    private final Map<String, Object> args;   // 原始参数快照，确认后按它执行
    private final Long userId;
    private final String conversationId;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;

    private boolean consumed;

    public PendingAction(String actionId, String toolName, Map<String, Object> args,
                         Long userId, String conversationId,
                         LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.actionId = actionId;
        this.toolName = toolName;
        // 防御性拷贝：外部后续修改原 Map 不会影响本次待办
        // 注意：Map.copyOf 不接受 null 键/值——创建时只放入非 null 参数
        this.args = (args == null) ? Map.of() : Map.copyOf(args);
        this.userId = userId;
        this.conversationId = conversationId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.consumed = false;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumed;
    }

    //只允许 Store 在原子消费时调用
    void markConsumed() {
        this.consumed = true;
    }

    public String getActionId() { return actionId; }
    public String getToolName() { return toolName; }
    public Map<String, Object> getArgs() { return args; }
    public Long getUserId() { return userId; }
    public String getConversationId() { return conversationId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
