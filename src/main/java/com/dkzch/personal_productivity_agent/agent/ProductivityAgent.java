package com.dkzch.personal_productivity_agent.agent;

import com.dkzch.personal_productivity_agent.common.ConversationContext;
import com.dkzch.personal_productivity_agent.service.PromptService;
import com.dkzch.personal_productivity_agent.tool.TaskTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;


@Component
public class ProductivityAgent {

    private static final DateTimeFormatter CURRENT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.SIMPLIFIED_CHINESE);

    //记忆窗口大小
    private static final int MEMORY_MAX_MESSAGES = 20;

    private final ChatClient chatClient;
    private final TaskTool taskTool;
    private final PromptService promptService;

    public ProductivityAgent(ChatClient.Builder chatClientBuilder,TaskTool taskTool,PromptService promptService) {

        this.taskTool = taskTool;
        this.promptService = promptService;

        // 显式创建记忆实例，不依赖自动配置的默认窗口行为：
        // 窗口大小是明确的架构决策，应该写在代码里而不是靠默认值
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(MEMORY_MAX_MESSAGES)
                .build();

        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }


    public String chat(String message) {
        String conversationId = ConversationContext.getConversationId();

        String basePrompt = promptService.getProductivityAgentSystemPrompt();

        String currentDateText = LocalDateTime.now().format(CURRENT_DATE_FORMATTER);

        String fullSystemPrompt = basePrompt
                + "\n\n【当前时间】\n"
                + "今天是 " + currentDateText + "。\n"
                + "如果用户使用'今天'、'明天'、'今晚'、'下周X'等相对时间描述，"
                + "你必须先基于上面的当前时间换算为具体日期，"
                + "再以 ISO-8601 格式传给工具，不要把相对描述直接传给工具。";

        return chatClient.prompt()
                .system(fullSystemPrompt)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(taskTool)
                .call()
                .content();
    }
}