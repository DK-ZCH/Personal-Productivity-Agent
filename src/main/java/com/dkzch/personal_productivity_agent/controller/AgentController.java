package com.dkzch.personal_productivity_agent.controller;

import com.dkzch.personal_productivity_agent.agent.ProductivityAgent;
import com.dkzch.personal_productivity_agent.common.ConversationContext;
import com.dkzch.personal_productivity_agent.common.ConversationIds;
import com.dkzch.personal_productivity_agent.model.dto.ChatRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ProductivityAgent productivityAgent;


    public AgentController(ProductivityAgent productivityAgent) {
        this.productivityAgent = productivityAgent;
    }


    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {

        String conversationId = request.getConversationId();

        // conversationId 必填：高风险确认依赖稳定的会话标识，
        // 缺失时不给默认值——直接拒绝，避免多个会话被静默合并
        if (!ConversationIds.isValid(conversationId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "conversationId 缺失或格式非法：长度需在 1-" + ConversationIds.MAX_LENGTH
                            + " 之间，且只允许字母、数字、下划线、连字符");
        }

        ConversationContext.set(ConversationIds.normalize(conversationId));
        try {
            return productivityAgent.chat(request.getMessage());
        } finally {
            ConversationContext.clear();
        }
    }
}