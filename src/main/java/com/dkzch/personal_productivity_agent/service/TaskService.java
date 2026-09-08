package com.dkzch.personal_productivity_agent.service;

import com.dkzch.personal_productivity_agent.common.BusinessException;
import com.dkzch.personal_productivity_agent.model.dto.CreateTaskRequest;
import com.dkzch.personal_productivity_agent.model.entity.Task;
import com.dkzch.personal_productivity_agent.model.enums.TaskStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskService {

    private final List<Task> tasks = new ArrayList<>();

    //线程安全的 id 生成器
    private final AtomicLong idGenerator = new AtomicLong(0);
    public Task createTask(CreateTaskRequest request) {

        validateCreateRequest(request);
        Task task = new Task();

        task.setId(idGenerator.incrementAndGet());
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

    public Task getTaskById(Long id) {

        return tasks.stream()
                .filter(task -> task.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException("任务不存在，id=" + id));
    }

    //按关键字搜索任务，匹配 title 或 description（不区分大小写）。
    public List<Task> searchTasks(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return getAllTasks();
        }

        String lower = keyword.toLowerCase(Locale.ROOT);

        return tasks.stream()
                .filter(task ->
                        (task.getTitle() != null && task.getTitle().toLowerCase(Locale.ROOT).contains(lower))
                                || (task.getDescription() != null && task.getDescription().toLowerCase(Locale.ROOT).contains(lower)))
                .toList();
    }


    //创建任务的业务规则校验
    private void validateCreateRequest(CreateTaskRequest request) {

        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessException("任务标题不能为空。");
        }

        LocalDateTime startTime = request.getStartTime();
        LocalDateTime deadline = request.getDeadline();

        if (startTime == null || deadline == null) {
            throw new BusinessException("开始时间和截止时间都不能为空。");
        }

        LocalDateTime now = LocalDateTime.now();

        if (startTime.isBefore(now)) {
            throw new BusinessException(
                    "开始时间不能早于当前时间。当前时间：" + now
                            + "，收到的开始时间：" + startTime
                            + "。请确认用户是否想创建一个过去时间的任务。");
        }

        if (deadline.isBefore(now)) {
            throw new BusinessException(
                    "截止时间不能早于当前时间。当前时间：" + now
                            + "，收到的截止时间：" + deadline + "。");
        }

        if (!deadline.isAfter(startTime)) {
            throw new BusinessException(
                    "截止时间必须晚于开始时间。收到的开始时间：" + startTime
                            + "，截止时间：" + deadline + "。");
        }
    }
    public List<Task> getAllTasks() {
        return tasks;
    }
}