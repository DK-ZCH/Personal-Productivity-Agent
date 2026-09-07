package com.dkzch.personal_productivity_agent.service;

import com.dkzch.personal_productivity_agent.model.dto.CreateTaskRequest;
import com.dkzch.personal_productivity_agent.model.entity.Task;
import com.dkzch.personal_productivity_agent.model.enums.TaskStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskService {

    private final List<Task> tasks = new ArrayList<>();

    public Task createTask(CreateTaskRequest request) {

        Task task = new Task();

        task.setId((long) (tasks.size() + 1));
        task.setUserId(1L);
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setPriority(request.getPriority());
        task.setStatus(TaskStatus.TODO);
        task.setCreatedAt(LocalDateTime.now());
        task.setStartTime(request.getStartTime());
        task.setDeadline(request.getDeadline());

        tasks.add(task);

        return task;
    }

    public List<Task> getAllTasks() {
        return tasks;
    }
}