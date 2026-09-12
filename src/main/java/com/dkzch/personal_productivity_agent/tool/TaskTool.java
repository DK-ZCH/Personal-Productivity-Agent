package com.dkzch.personal_productivity_agent.tool;

import com.dkzch.personal_productivity_agent.common.*;
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
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.tool.annotation.ToolParam;

@Component
public class TaskTool {

    private static final Logger log = LoggerFactory.getLogger(TaskTool.class);
    private final TaskService taskService;

    /** 待确认动作的有效期（分钟） */
    private static final int CONFIRMATION_TTL_MINUTES = 10;

    /** 确认码长度：16 位十六进制 = 64 bit 随机空间 */
    private static final int ACTION_ID_LENGTH = 16;
    private final PendingActionStore pendingActionStore;
    private final CurrentUserProvider currentUserProvider;

    public TaskTool(TaskService taskService,
                    PendingActionStore pendingActionStore,
                    CurrentUserProvider currentUserProvider) {
        this.taskService = taskService;
        this.pendingActionStore = pendingActionStore;
        this.currentUserProvider = currentUserProvider;
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

    @Tool(
            name = "complete_task",
            description = "将指定任务标记为已完成。"
                    + "当用户说'完成某个任务'、'任务做完了'、'把任务 1 标记为完成'时使用。"
                    + "需要任务 ID；如果用户不知道 ID，先用 list_tasks 或 search_tasks 查找。"
                    + "如果任务已经是完成状态，会返回失败提示。")
    public ToolResult<TaskSummary> completeTask(
            @ToolParam(description = "要标记完成的任务 ID，纯数字") Long id
    ) {
        try {
            Task task = taskService.completeTask(id);

            log.info("complete_task 执行成功，taskId={}, title={}", id, task.getTitle());

            return ToolResult.success(
                    "任务已完成：" + task.getTitle(),
                    TaskSummary.from(task)
            );

        } catch (BusinessException e) {
            log.warn("complete_task 执行失败，taskId={}, 原因={}", id, e.getMessage());

            return ToolResult.failure(
                    "无法完成任务：" + e.getMessage()
                            + "。请向用户确认任务 ID 和当前状态，必要时先用 list_tasks 查看。");
        }
    }

    @Tool(
            name = "update_task",
            description = "部分更新任务的信息（修改标题/描述/优先级/时间等）。"
                    + "当用户说'把任务 1 改名为某某'、'调整截止时间'、'把优先级改为高'时使用。"
                    + "所有可更新字段都是可选的：不传或传 null 表示不更新；至少要更新一个字段。"
                    + "不能修改已完成的任务。不能修改 id、userId、status（这些是系统管理的）。")
    public ToolResult<TaskSummary> updateTask(
            @ToolParam(description = "要更新的任务 ID") Long id,

            @ToolParam(description = "新标题（不更新请不传）", required = false) String title,

            @ToolParam(description = "新描述（不更新请不传）", required = false) String description,

            @ToolParam(description = "新优先级，可选 LOW/MEDIUM/HIGH（不更新请不传）", required = false) String priority,

            @ToolParam(description = "新开始时间，ISO-8601 格式（不更新请不传）", required = false) String startTime,

            @ToolParam(description = "新截止时间，ISO-8601 格式（不更新请不传）", required = false) String deadline
    ) {

        try {
            Task task = taskService.updateTask(id, title, description, priority, startTime, deadline);

            log.info("update_task 执行成功，taskId={}", id);

            return ToolResult.success(
                    "任务已更新：" + task.getTitle(),
                    TaskSummary.from(task)
            );

        } catch (BusinessException e) {
            log.warn("update_task 执行失败，taskId={}, 原因={}", id, e.getMessage());

            return ToolResult.failure(
                    "无法更新任务：" + e.getMessage()
                            + "。请向用户确认 ID 和要修改的字段，必要时先用 list_tasks 查看。");
        }
    }

    @Tool(
            name = "delete_task",
            description = "永久删除指定的任务，此操作不可恢复！"
                    + "当用户明确要求删除任务时使用（例如'删除任务 2'、'把某个任务删掉'）。"
                    + "需要任务 ID；如果用户不确定 ID，先用 list_tasks 或 search_tasks 查找。"
                    + "本工具不会立即删除，而是返回一个待确认信息（含确认码）；"
                    + "你必须把待确认信息完整转述给用户并等待用户明确同意，"
                    + "用户同意后再调用 confirm_action 并传入该确认码。")
    public ToolResult<TaskSummary> deleteTask(
            @ToolParam(description = "要删除的任务 ID，纯数字") Long id
    ) {

        if (id == null) {
            log.warn("delete_task 缺少任务 id");
            return ToolResult.failure(
                    "缺少任务 ID，无法确认要删除哪个任务。请先用 list_tasks 查看任务及其 ID。");
        }

        // 第一步：先校验任务存在（不给用户确认一个不存在的任务）
        Task task;
        try {
            task = taskService.getTaskById(id);
        } catch (BusinessException e) {
            log.warn("delete_task 前置校验失败，taskId={}, 原因={}", id, e.getMessage());
            return ToolResult.failure(
                    "无法删除任务：" + e.getMessage()
                            + "。请向用户确认任务 ID 是否正确，必要时先用 list_tasks 查看。");
        }

        // 第二步：按风险分级决策——HIGH 走待确认，其余直接执行
        if (ToolRiskRegistry.requiresConfirmation(ToolRiskRegistry.DELETE_TASK)) {

            String actionId = generateActionId();
            LocalDateTime now = LocalDateTime.now();

            PendingAction action = new PendingAction(
                    actionId,
                    ToolRiskRegistry.DELETE_TASK,
                    Map.of("id", id),                          // 参数快照，确认后按它执行
                    currentUserProvider.getCurrentUserId(),     // 服务端决定，客户端不可伪造
                    ConversationContext.getConversationId(),    // 请求上下文，不经过 LLM
                    now,
                    now.plusMinutes(CONFIRMATION_TTL_MINUTES)
            );
            pendingActionStore.save(action);

            log.info("delete_task 转待确认，actionId={}, taskId={}, userId={}",
                    actionId, id, action.getUserId());

            return ToolResult.pending(
                    "这是一个不可恢复的操作，需要用户确认后才能执行。"
                            + "任务「" + task.getTitle() + "」(id=" + id + ") 即将被永久删除。"
                            + "请把以上信息和确认码完整转述给用户，并等待用户明确同意。"
                            + "确认码：" + actionId + "（有效期 " + CONFIRMATION_TTL_MINUTES + " 分钟）。"
                            + "用户明确同意后，调用 confirm_action 并传入 actionId=\"" + actionId + "\"；"
                            + "用户未明确同意时，不要调用 confirm_action。");
        }

        Task deleted = taskService.deleteTask(id);
        log.info("delete_task 执行成功（无需确认），taskId={}, title={}", id, deleted.getTitle());
        return ToolResult.success("任务已永久删除：" + deleted.getTitle(), TaskSummary.from(deleted));
    }

    @Tool(
            name = "confirm_action",
            description = "确认并执行此前被标记为待确认的高风险操作（如删除任务）。"
                    + "仅当用户对上一条待确认信息给出明确同意（例如'确认'、'是的，删吧'、'同意'）时调用。"
                    + "必须传入你向用户转述过的那个确认码。"
                    + "用户没有明确同意时，不要调用本工具。")
    public ToolResult<TaskSummary> confirmAction(
            @ToolParam(description = "待确认操作的确认码，例如 3F9A1C7E2B4D8056") String actionId
    ) {

        if (actionId == null || actionId.isBlank()) {
            return ToolResult.failure("缺少确认码，无法执行确认。请重新发起该操作。");
        }

        // 归一化：存储时为大写，容忍用户/LLM 传入小写
        String normalizedActionId = actionId.trim().toUpperCase();

        ConsumeResult result = pendingActionStore.consume(
                normalizedActionId,
                currentUserProvider.getCurrentUserId(),
                ConversationContext.getConversationId()
        );

        if (!result.isSuccess()) {
            log.warn("confirm_action 消费失败，actionId={}, outcome={}",
                    normalizedActionId, result.outcome());

            return ToolResult.failure(switch (result.outcome()) {
                case ALREADY_CONSUMED -> "该操作已经执行过了，无需重复确认。";
                case EXPIRED -> "确认已超时（有效期 " + CONFIRMATION_TTL_MINUTES
                        + " 分钟），操作已取消。如仍需执行，请重新发起。";
                // NOT_FOUND 与 FORBIDDEN 统一话术：不泄露"确认码存在但不属于你"
                default -> "确认码无效或已失效，操作已取消。如仍需执行，请重新发起。";
            });
        }

        PendingAction action = result.actionIfSuccess()
                .orElseThrow(() -> new IllegalStateException("consume 返回 SUCCESS 但未携带 action"));

        log.info("confirm_action 消费成功，actionId={}, toolName={}",
                normalizedActionId, action.getToolName());

        return executeConfirmedAction(action);
    }

    /** 按待办记录的动作类型分发执行。 */
    private ToolResult<TaskSummary> executeConfirmedAction(PendingAction action) {

        if (ToolRiskRegistry.DELETE_TASK.equals(action.getToolName())) {

            Long taskId = ((Number) action.getArgs().get("id")).longValue();

            try {
                Task task = taskService.deleteTask(taskId);
                log.info("确认后执行删除成功，taskId={}, title={}", taskId, task.getTitle());
                return ToolResult.success("任务已永久删除：" + task.getTitle(), TaskSummary.from(task));
            } catch (BusinessException e) {
                log.warn("确认后执行删除失败，taskId={}, 原因={}", taskId, e.getMessage());
                return ToolResult.failure("执行失败：" + e.getMessage());
            }
        }

        log.error("确认动作类型未实现，toolName={}", action.getToolName());
        return ToolResult.failure("不支持的确认操作类型：" + action.getToolName());
    }

    /** 16 位十六进制确认码（64 bit 随机空间）。 */
    private String generateActionId() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, ACTION_ID_LENGTH)
                .toUpperCase();
    }



}