package com.dkzch.personal_productivity_agent.common;

import java.util.Optional;

//失败原因对外只区分四种；"userId 不匹配"与"conversationId 不匹配"
public record ConsumeResult(Outcome outcome, PendingAction action) {

    public enum Outcome {
        SUCCESS,            // 四道闸门全过，已原子标记 consumed，action 携带执行参数
        NOT_FOUND,          // actionId 不存在
        ALREADY_CONSUMED,   // 已确认过（重放）
        EXPIRED,            // 已过期
        FORBIDDEN           // userId 或 conversationId 不匹配
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public static ConsumeResult success(PendingAction action) {
        return new ConsumeResult(Outcome.SUCCESS, action);
    }

    public static ConsumeResult failure(Outcome outcome) {
        return new ConsumeResult(outcome, null);
    }

    //SUCCESS 时才有 action。
    public Optional<PendingAction> actionIfSuccess() {
        return isSuccess() ? Optional.ofNullable(action) : Optional.empty();
    }
}
