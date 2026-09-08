package com.dkzch.personal_productivity_agent.tool;

import com.dkzch.personal_productivity_agent.common.BusinessException;
import com.dkzch.personal_productivity_agent.common.TaskParamParser;
import com.dkzch.personal_productivity_agent.model.dto.CreateTaskRequest;
import com.dkzch.personal_productivity_agent.model.dto.TaskSummary;
import com.dkzch.personal_productivity_agent.model.dto.ToolResult;
import com.dkzch.personal_productivity_agent.model.entity.Task;
import com.dkzch.personal_productivity_agent.model.enums.TaskPriority;
import com.dkzch.personal_productivity_agent.service.TaskService;
import org.springframework.stereotype.Component;
import org.springframework.ai.tool.annotation.Tool;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.tool.annotation.ToolParam;

@Component
public class TaskTool {

    private static final Logger log = LoggerFactory.getLogger(TaskTool.class);
    private final TaskService taskService;

    public TaskTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Tool(
            name = "create_task",
            description = "创建一个新的任务。仅当用户明确要求创建、安排或添加任务时使用。"
                    + "时间参数必须是 ISO-8601 格式（例如 2026-09-08T19:00:00），"
                    + "不接受'明天'、'下周三'、'今晚'这类相对或模糊描述。"
                    + "任务标题为必填项，若用户未提供，不要编造，应向用户询问。")
    public ToolResult<TaskSummary> createTask(
            @ToolParam(description = "任务标题，必填，不能为空") String title,

            @ToolParam(description = "任务描述，可选") String description,

            @ToolParam(description = "任务优先级，可选值为 LOW、MEDIUM、HIGH；无法判断时使用 MEDIUM") String priority,

            @ToolParam(description = "任务开始时间，必须是 ISO-8601 格式，例如 2026-09-08T19:00:00") String startTime,

            @ToolParam(description = "任务截止时间，必须是 ISO-8601 格式，且必须晚于开始时间") String deadline
    ) {

        LocalDateTime start = TaskParamParser.parseDateTime(startTime);
        LocalDateTime end = TaskParamParser.parseDateTime(deadline);

        if (start == null || end == null) {
            log.warn("create_task 参数解析失败：时间格式无法识别");
            return ToolResult.failure(
                    "时间无法识别，请使用 ISO-8601 格式，例如 2026-09-08T19:00:00。"
                            + "收到的开始时间：" + startTime
                            + "，截止时间：" + deadline
                            + "。请向用户确认具体日期和时间后再重试。");
        }

        CreateTaskRequest request = new CreateTaskRequest();

        request.setTitle(title);
        request.setDescription(description);
        request.setPriority(TaskParamParser.parsePriority(priority));
        request.setStartTime(start);
        request.setDeadline(end);

        try {
            Task task = taskService.createTask(request);

            log.info("create_task 执行成功，taskId={}, priority={}",
                    task.getId(), task.getPriority());

            return ToolResult.success(
                    "任务创建成功：" + task.getTitle(),
                    TaskSummary.from(task)
            );
        } catch (BusinessException e) {
            log.warn("create_task 业务校验失败，原因={}", e.getMessage());

            return ToolResult.failure(
                    "任务创建失败：" + e.getMessage()
                            + "。请向用户确认正确信息后重试。");
        }
    }
}