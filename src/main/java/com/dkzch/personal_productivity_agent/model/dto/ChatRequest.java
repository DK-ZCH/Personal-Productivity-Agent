package com.dkzch.personal_productivity_agent.model.dto;

public class ChatRequest {

    private String message;

    //会话标识，必填：同一段对话的多轮请求必须传相同值。
    private String conversationId;

    public ChatRequest() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
    public String getConversationId() {
        return conversationId;
    }
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}