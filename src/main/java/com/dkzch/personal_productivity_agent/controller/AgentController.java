package com.dkzch.personal_productivity_agent.controller;

import com.dkzch.personal_productivity_agent.agent.ProductivityAgent;
import com.dkzch.personal_productivity_agent.model.dto.ChatRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ProductivityAgent productivityAgent;


    public AgentController(ProductivityAgent productivityAgent) {
        this.productivityAgent = productivityAgent;
    }


    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {

        return productivityAgent.chat(request.getMessage());
    }
}