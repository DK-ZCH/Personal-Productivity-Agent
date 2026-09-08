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
import java.util.List;

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

    @Tool(
            name = "list_tasks",
            description = "查询当前用户的所有任务。"
                    + "当用户询问'我有哪些任务'、'看看我的任务'、'列出所有任务'、'我的待办是什么'时使用。"
                    + "本工具不需要任何参数，直接调用即可。")
    public ToolResult<List<TaskSummary>> listTasks() {

        List<Task> tasks = taskService.getAllTasks();

        List<TaskSummary> summaries = tasks.stream()
                .map(TaskSummary::from)
                .toList();

        log.info("list_tasks 执行成功，任务数量={}", summaries.size());

        if (summaries.isEmpty()) {
            return ToolResult.success("当前没有任何任务。", summaries);
        }

        return ToolResult.success(
                "共查询到 " + summaries.size() + " 个任务。",
                summaries
        );
    }

    @Tool(
            name = "get_task",
            description = "根据任务 ID 查询单个任务的详细信息。"
                    + "当用户询问'任务 1 的详情'、'看看 ID 为 3 的任务'、'某个任务是什么'时使用。"
                    + "需要用户提供任务 ID；如果用户不知道 ID，应先用 list_tasks 查询所有任务再获取 ID。")
    public ToolResult<TaskSummary> getTask(
            @ToolParam(description = "任务 ID，纯数字，例如 1") Long id
    ) {
        try {
            Task task = taskService.getTaskById(id);

            log.info("get_task 执行成功，taskId={}", id);

            return ToolResult.success(
                    "已找到任务：" + task.getTitle(),
                    TaskSummary.from(task)
            );

        } catch (BusinessException e) {
            log.warn("get_task 查询失败，taskId={}, 原因={}", id, e.getMessage());

            return ToolResult.failure(
                    "查询任务失败：" + e.getMessage()
                            + "。请向用户确认任务 ID 是否正确，或先用 list_tasks 查看所有任务。");
        }
    }

    @Tool(
            name = "search_tasks",
            description = "按关键字搜索任务，匹配标题或描述（不区分大小写）。"
                    + "当用户询问'找包含某某的任务'、'搜索某个关键词'、'查找类似的任务'时使用。"
                    + "关键字可以是中文或英文；空字符串或只传空格会返回所有任务。")
    public ToolResult<List<TaskSummary>> searchTasks(
            @ToolParam(description = "搜索关键字，例如 '控笔'、'阅读'、'数学'") String keyword
    ) {

        List<Task> tasks = taskService.searchTasks(keyword);

        List<TaskSummary> summaries = tasks.stream()
                .map(TaskSummary::from)
                .toList();

        log.info("search_tasks 执行成功，关键字='{}'，匹配数量={}", keyword, summaries.size());

        if (summaries.isEmpty()) {
            return ToolResult.failure(
                    "没有找到包含 '" + keyword + "' 的任务。请换个关键字试试，或用 list_tasks 查看所有任务。");
        }

        return ToolResult.success(
                "找到 " + summaries.size() + " 个包含 '" + keyword + "' 的任务。",
                summaries
        );
    }

}