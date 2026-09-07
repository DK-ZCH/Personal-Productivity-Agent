package com.dkzch.personal_productivity_agent.agent;

import com.dkzch.personal_productivity_agent.service.PromptService;
import com.dkzch.personal_productivity_agent.tool.TaskTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;


@Component
public class ProductivityAgent {

    private final ChatClient chatClient;
    private final TaskTool taskTool;
    private final PromptService promptService;

    public ProductivityAgent(ChatClient.Builder chatClientBuilder,TaskTool taskTool,PromptService promptService) {
        this.chatClient = chatClientBuilder.build();
        this.taskTool = taskTool;
        this.promptService = promptService;
    }


    public String chat(String message) {

        String systemPrompt =
                promptService.getProductivityAgentSystemPrompt();

        return chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .tools(taskTool)
                .call()
                .content();
    }
}