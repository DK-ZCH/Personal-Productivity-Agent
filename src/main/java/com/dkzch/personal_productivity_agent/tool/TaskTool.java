package com.dkzch.personal_productivity_agent.tool;

import com.dkzch.personal_productivity_agent.model.dto.CreateTaskRequest;
import com.dkzch.personal_productivity_agent.model.dto.TaskSummary;
import com.dkzch.personal_productivity_agent.model.dto.ToolResult;
import com.dkzch.personal_productivity_agent.model.entity.Task;
import com.dkzch.personal_productivity_agent.model.enums.TaskPriority;
import com.dkzch.personal_productivity_agent.service.TaskService;
import org.springframework.stereotype.Component;
import org.springframework.ai.tool.annotation.Tool;
import java.time.LocalDateTime;
import org.springframework.ai.tool.annotation.ToolParam;

@Component
public class TaskTool {

    private final TaskService taskService;

    public TaskTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Tool(
            name = "create_task",
            description = "创建一个新的任务。当用户明确要求创建、安排或添加任务时使用。")
    public ToolResult<TaskSummary> createTask(
            @ToolParam(description = "任务标题") String title,

            @ToolParam(description = "任务描述") String description,

            @ToolParam(description = "任务优先级，例如 LOW、MEDIUM、HIGH") String priority,

            @ToolParam(description = "任务开始时间，使用 ISO-8601 格式，例如 2026-09-07T19:00:00") String startTime,

            @ToolParam(description = "任务截止时间，使用 ISO-8601 格式，例如 2026-09-07T20:00:00") String deadline
    ) {

        CreateTaskRequest request = new CreateTaskRequest();

        request.setTitle(title);
        request.setDescription(description);

        // 枚举转换
        request.setPriority(
                TaskPriority.valueOf(
                        priority.toUpperCase()
                )
        );

        request.setStartTime(LocalDateTime.parse(startTime));
        request.setDeadline(LocalDateTime.parse(deadline));

        Task task = taskService.createTask(request);

        return ToolResult.success(
                "任务创建成功：" + task.getTitle(),
                TaskSummary.from(task)
        );
    }
}