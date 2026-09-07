package com.dkzch.personal_productivity_agent.model.dto;

import com.dkzch.personal_productivity_agent.model.enums.TaskPriority;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.LocalDateTime;

public class CreateTaskRequest {

    @JsonPropertyDescription("任务标题")
    private String title;

    @JsonPropertyDescription("任务的详细描述")
    private String description;

    @JsonPropertyDescription("任务优先级，可选值为 LOW、MEDIUM、HIGH")
    private TaskPriority priority;

    @JsonPropertyDescription("任务开始时间")
    private LocalDateTime startTime;

    @JsonPropertyDescription("任务截止时间")
    private LocalDateTime deadline;

    public CreateTaskRequest() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }
}