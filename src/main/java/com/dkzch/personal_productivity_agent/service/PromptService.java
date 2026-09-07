package com.dkzch.personal_productivity_agent.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class PromptService {

    private static final String PRODUCTIVITY_AGENT_PROMPT =
            "prompts/productivity-agent-system.txt";

    public String getProductivityAgentSystemPrompt() {

        try {
            ClassPathResource resource =
                    new ClassPathResource(PRODUCTIVITY_AGENT_PROMPT);

            return resource.getContentAsString(StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load productivity agent system prompt", e);
        }
    }
}