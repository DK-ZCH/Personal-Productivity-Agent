package com.dkzch.personal_productivity_agent.agent;

import com.dkzch.personal_productivity_agent.service.PromptService;
import com.dkzch.personal_productivity_agent.tool.TaskTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;


@Component
public class ProductivityAgent {

    private static final DateTimeFormatter CURRENT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.SIMPLIFIED_CHINESE);

    private final ChatClient chatClient;
    private final TaskTool taskTool;
    private final PromptService promptService;

    public ProductivityAgent(ChatClient.Builder chatClientBuilder,TaskTool taskTool,PromptService promptService) {
        this.chatClient = chatClientBuilder.build();
        this.taskTool = taskTool;
        this.promptService = promptService;
    }


    public String chat(String message) {
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
                .tools(taskTool)
                .call()
                .content();
    }
}