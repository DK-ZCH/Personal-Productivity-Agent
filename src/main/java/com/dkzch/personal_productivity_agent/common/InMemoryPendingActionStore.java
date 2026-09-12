package com.dkzch.personal_productivity_agent.common;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class InMemoryPendingActionStore implements PendingActionStore {

    private final Map<String, PendingAction> pendingActions = new ConcurrentHashMap<>();

    @Override
    public void save(PendingAction action) {
        pendingActions.put(action.getActionId(), action);
    }

    @Override
    public Optional<PendingAction> find(String actionId) {
        return Optional.ofNullable(pendingActions.get(actionId));
    }

    @Override
    public ConsumeResult consume(String actionId, Long userId, String conversationId) {

        AtomicReference<ConsumeResult.Outcome> outcome = new AtomicReference<>();
        AtomicReference<PendingAction> consumedAction = new AtomicReference<>();

        pendingActions.compute(actionId, (id, action) -> {
            if (action == null) {
                outcome.set(ConsumeResult.Outcome.NOT_FOUND);
                return null;
            }
            if (action.isConsumed()) {
                outcome.set(ConsumeResult.Outcome.ALREADY_CONSUMED);
                return action;
            }
            if (action.isExpired()) {
                outcome.set(ConsumeResult.Outcome.EXPIRED);
                return action;
            }
            if (!action.getUserId().equals(userId)
                    || !action.getConversationId().equals(conversationId)) {
                // 用户不匹配与会话不匹配统一报 FORBIDDEN，不泄露细分原因
                outcome.set(ConsumeResult.Outcome.FORBIDDEN);
                return action;
            }
            action.markConsumed();
            outcome.set(ConsumeResult.Outcome.SUCCESS);
            consumedAction.set(action);
            return action;
        });

        return outcome.get() == ConsumeResult.Outcome.SUCCESS
                ? ConsumeResult.success(consumedAction.get())
                : ConsumeResult.failure(outcome.get());
    }
}
