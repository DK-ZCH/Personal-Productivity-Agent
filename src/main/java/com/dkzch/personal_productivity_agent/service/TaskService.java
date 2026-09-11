package com.dkzch.personal_productivity_agent.service;

import com.dkzch.personal_productivity_agent.common.BusinessException;
import com.dkzch.personal_productivity_agent.common.CurrentUserProvider;
import com.dkzch.personal_productivity_agent.common.TaskParamParser;
import com.dkzch.personal_productivity_agent.model.dto.CreateTaskRequest;
import com.dkzch.personal_productivity_agent.model.entity.Task;
import com.dkzch.personal_productivity_agent.model.enums.TaskStatus;
import com.dkzch.personal_productivity_agent.repository.TaskRepository;
import org.springframework.stereotype.Service;
import com.dkzch.personal_productivity_agent.model.enums.TaskPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final CurrentUserProvider currentUserProvider;

    public TaskService(TaskRepository taskRepository, CurrentUserProvider currentUserProvider) {
        this.taskRepository = taskRepository;
        this.currentUserProvider = currentUserProvider;
    }

    //service 内部统一获取当前用户 ID 的入口。
    private Long currentUserId() {
        return currentUserProvider.getCurrentUserId();
    }

    public Task createTask(CreateTaskRequest request) {

        validateCreateRequest(request);
        Task task = new Task();

        task.setUserId(currentUserId());
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setPriority(request.getPriority());
        task.setStatus(TaskStatus.TODO);
        task.setCreatedAt(LocalDateTime.now());
        task.setStartTime(request.getStartTime());
        task.setDeadline(request.getDeadline());

        Task saved = taskRepository.save(task);

        log.info("任务已创建: id={}, title={}", saved.getId(), saved.getTitle());
        return saved;
    }

    //查单条任务：不属于当前用户时统一抛"不存在"，
    public Task getTaskById(Long id) {

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("任务不存在，id=" + id));

        if (!task.getUserId().equals(currentUserId())) {
            throw new BusinessException("任务不存在，id=" + id);
        }
        return task;
    }

    public List<Task> getAllTasks() {
        return taskRepository.findByUserId(currentUserId());
    }
    public List<Task> searchTasks(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return getAllTasks();
        }
        return taskRepository.searchByUserIdAndKeyword(currentUserId(), keyword.trim());
    }


    /**
     * 将任务标记为已完成。
     *
     * 状态流转规则：
     * TODO / IN_PROGRESS → COMPLETED（允许）
     * COMPLETED         → 拒绝（不能重复完成）
     * CANCELLED         → 拒绝（已取消不能完成）
     */
    public Task completeTask(Long id) {

        Task task = getTaskById(id);

        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new BusinessException("任务已经是完成状态，不能重复完成，id=" + id);
        }

        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new BusinessException("任务已取消，不能标记为完成，id=" + id);
        }

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());

        return taskRepository.save(task);
    }

    /**
     * 部分更新任务。所有可更新字段都可选（null 表示不更新），但至少要更新一个字段。
     *
     * 可更新字段：title、description、priority、startTime、deadline
     * 不可更新字段：id、userId、status、createdAt、completedAt（系统管理）
     * 不能更新已完成的任务（避免改历史）
     *
     * 实现原则：先解析并校验所有新值，全部通过后再一次性写回，
     * 保证校验失败时不会留下部分修改（内存存储没有事务回滚）。
     */
    public Task updateTask(Long id, String title, String description,
                           String priority, String startTime, String deadline) {

        Task task = getTaskById(id);

        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new BusinessException("任务已完成，不能修改，id=" + id);
        }

        // 至少更新一个字段
        if (title == null && description == null && priority == null
                && startTime == null && deadline == null) {
            throw new BusinessException("至少需要更新一个字段（title/description/priority/startTime/deadline）");
        }

        //1.解析新值
        LocalDateTime parsedStart = null;
        if (startTime != null) {
            parsedStart = TaskParamParser.parseDateTime(startTime);
            if (parsedStart == null) {
                throw new BusinessException("开始时间格式无法识别：" + startTime);
            }
        }

        LocalDateTime parsedDeadline = null;
        if (deadline != null) {
            parsedDeadline = TaskParamParser.parseDateTime(deadline);
            if (parsedDeadline == null) {
                throw new BusinessException("截止时间格式无法识别：" + deadline);
            }
        }

        //2.算出最终值
        String finalTitle = (title != null) ? title : task.getTitle();
        String finalDescription = (description != null) ? description : task.getDescription();
        TaskPriority finalPriority = (priority != null)
                ? TaskParamParser.parsePriority(priority)
                : task.getPriority();
        LocalDateTime finalStart = (parsedStart != null) ? parsedStart : task.getStartTime();
        LocalDateTime finalDeadline = (parsedDeadline != null) ? parsedDeadline : task.getDeadline();

        //3.用最终值做完整校验
        if (finalTitle == null || finalTitle.isBlank()) {
            throw new BusinessException("标题不能为空");
        }
        // 防御：存量数据若时间字段为空，给出明确业务错误而非 NPE
        if (finalStart == null || finalDeadline == null) {
            throw new BusinessException("开始时间和截止时间都不能为空。");
        }
        if (!finalDeadline.isAfter(finalStart)) {
            throw new BusinessException(
                    "截止时间必须晚于开始时间。开始时间：" + finalStart
                            + "，截止时间：" + finalDeadline);
        }

        if (finalStart.isBefore(LocalDateTime.now())) {
            throw new BusinessException("开始时间不能早于当前时间：" + finalStart);
        }

        //4.一次性写回
        task.setTitle(finalTitle);
        task.setDescription(finalDescription);
        task.setPriority(finalPriority);
        task.setStartTime(finalStart);
        task.setDeadline(finalDeadline);

        return taskRepository.save(task);
    }

    //永久删除任务（硬删除）。
    public Task deleteTask(Long id) {

        Task task = getTaskById(id);
        taskRepository.delete(task);

        log.info("任务已删除: id={}, title={}", id, task.getTitle());
        return task;
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
}