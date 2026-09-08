package com.dkzch.personal_productivity_agent.model.dto;

import com.dkzch.personal_productivity_agent.model.entity.Task;
import com.dkzch.personal_productivity_agent.model.enums.TaskPriority;
import com.dkzch.personal_productivity_agent.model.enums.TaskStatus;

import java.time.LocalDateTime;

//返回给 LLM 的任务摘要
public class TaskSummary {

    private Long id;
    private String title;
    private TaskPriority priority;
    private TaskStatus status;
    private LocalDateTime startTime;
    private LocalDateTime deadline;

    public static TaskSummary from(Task task) {
        TaskSummary summary = new TaskSummary();
        summary.setId(task.getId());
        summary.setTitle(task.getTitle());
        summary.setPriority(task.getPriority());
        summary.setStatus(task.getStatus());
        summary.setStartTime(task.getStartTime());
        summary.setDeadline(task.getDeadline());
        return summary;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
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
