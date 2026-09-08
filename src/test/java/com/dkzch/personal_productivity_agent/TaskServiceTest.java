package com.dkzch.personal_productivity_agent;

import com.dkzch.personal_productivity_agent.common.BusinessException;
import com.dkzch.personal_productivity_agent.model.dto.CreateTaskRequest;
import com.dkzch.personal_productivity_agent.model.entity.Task;
import com.dkzch.personal_productivity_agent.model.enums.TaskPriority;
import com.dkzch.personal_productivity_agent.model.enums.TaskStatus;
import com.dkzch.personal_productivity_agent.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskServiceTest {

    private TaskService taskService;

    /** 稳定的未来时间：比 now 晚 1 天并去掉纳秒，避免时间边界导致测试不稳定 */
    private LocalDateTime futureStart;
    private LocalDateTime futureDeadline;

    @BeforeEach
    void setUp() {
        taskService = new TaskService();
        futureStart = LocalDateTime.now().plusDays(1).withNano(0);
        futureDeadline = futureStart.plusHours(1);
    }

    /** 造一条合法任务并返回创建结果 */
    private Task createOneTask(String title) {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle(title);
        request.setDescription("描述-" + title);
        request.setPriority(TaskPriority.MEDIUM);
        request.setStartTime(futureStart);
        request.setDeadline(futureDeadline);
        return taskService.createTask(request);
    }

    @Nested
    @DisplayName("createTask")
    class CreateTask {

        @Test
        @DisplayName("合法请求创建成功，系统字段由 Service 设置")
        void createSuccessfully() {
            Task task = createOneTask("阅读");

            assertEquals("阅读", task.getTitle());
            assertEquals(TaskStatus.TODO, task.getStatus());
            assertEquals(1L, task.getUserId());
            assertNotNull(task.getCreatedAt());
            assertNotNull(task.getId());
        }

        @Test
        @DisplayName("连续创建 id 应单调递增")
        void idsIncrease() {
            Task t1 = createOneTask("任务1");
            Task t2 = createOneTask("任务2");
            assertEquals(t1.getId() + 1, t2.getId());
        }

        @Test
        @DisplayName("空白标题应抛 BusinessException")
        void blankTitleRejected() {
            CreateTaskRequest request = new CreateTaskRequest();
            request.setTitle("   ");
            request.setPriority(TaskPriority.MEDIUM);
            request.setStartTime(futureStart);
            request.setDeadline(futureDeadline);

            assertThrows(BusinessException.class, () -> taskService.createTask(request));
        }

        @Test
        @DisplayName("过去时间应抛 BusinessException")
        void pastTimeRejected() {
            CreateTaskRequest request = new CreateTaskRequest();
            request.setTitle("过去任务");
            request.setPriority(TaskPriority.MEDIUM);
            request.setStartTime(LocalDateTime.now().minusDays(1));
            request.setDeadline(LocalDateTime.now().plusDays(1));

            assertThrows(BusinessException.class, () -> taskService.createTask(request));
        }

        @Test
        @DisplayName("deadline 等于 startTime 也应拒绝（必须严格晚于）")
        void deadlineEqualToStartRejected() {
            CreateTaskRequest request = new CreateTaskRequest();
            request.setTitle("倒置任务");
            request.setPriority(TaskPriority.MEDIUM);
            request.setStartTime(futureStart);
            request.setDeadline(futureStart);

            assertThrows(BusinessException.class, () -> taskService.createTask(request));
        }
    }

    @Nested
    @DisplayName("getTaskById")
    class GetTaskById {

        @Test
        @DisplayName("存在的 id 返回任务")
        void foundTask() {
            Task created = createOneTask("阅读");
            Task found = taskService.getTaskById(created.getId());
            assertEquals(created.getTitle(), found.getTitle());
        }

        @Test
        @DisplayName("不存在的 id 抛 BusinessException")
        void missingTaskThrows() {
            assertThrows(BusinessException.class, () -> taskService.getTaskById(999L));
        }
    }

    @Nested
    @DisplayName("searchTasks")
    class SearchTasks {

        @Test
        @DisplayName("按 title 关键字匹配")
        void matchTitle() {
            createOneTask("控笔训练");
            createOneTask("阅读");

            List<Task> result = taskService.searchTasks("控笔");

            assertEquals(1, result.size());
            assertEquals("控笔训练", result.get(0).getTitle());
        }

        @Test
        @DisplayName("按 description 关键字匹配")
        void matchDescription() {
            createOneTask("数学"); // description = "描述-数学"

            List<Task> result = taskService.searchTasks("描述-数学");

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("无匹配返回空列表")
        void noMatchReturnsEmpty() {
            createOneTask("阅读");

            assertTrue(taskService.searchTasks("编程").isEmpty());
        }

        @Test
        @DisplayName("空白关键字返回全部")
        void blankKeywordReturnsAll() {
            createOneTask("任务1");
            createOneTask("任务2");

            assertEquals(2, taskService.searchTasks("  ").size());
        }
    }

    @Nested
    @DisplayName("completeTask")
    class CompleteTask {

        @Test
        @DisplayName("完成后状态为 COMPLETED 且设置 completedAt")
        void completeSuccessfully() {
            Task task = createOneTask("阅读");

            Task completed = taskService.completeTask(task.getId());

            assertEquals(TaskStatus.COMPLETED, completed.getStatus());
            assertNotNull(completed.getCompletedAt());
        }

        @Test
        @DisplayName("重复完成抛 BusinessException")
        void doubleCompleteThrows() {
            Task task = createOneTask("阅读");
            taskService.completeTask(task.getId());

            assertThrows(BusinessException.class,
                    () -> taskService.completeTask(task.getId()));
        }
    }

    @Nested
    @DisplayName("updateTask")
    class UpdateTask {

        @Test
        @DisplayName("部分更新：未传入的字段保持原值")
        void partialUpdate() {
            Task task = createOneTask("阅读");

            Task updated = taskService.updateTask(
                    task.getId(), "深度学习", null, null, null, null);

            assertEquals("深度学习", updated.getTitle());
            assertEquals("描述-阅读", updated.getDescription());
            assertEquals(futureStart, updated.getStartTime());
        }

        @Test
        @DisplayName("全部字段为 null 抛 BusinessException")
        void noFieldsThrows() {
            Task task = createOneTask("阅读");

            assertThrows(BusinessException.class,
                    () -> taskService.updateTask(task.getId(), null, null, null, null, null));
        }

        @Test
        @DisplayName("⭐ 失败原子性：校验失败时原任务不被改动")
        void failedUpdateKeepsOriginal() {
            Task task = createOneTask("阅读");
            LocalDateTime originalDeadline = task.getDeadline();

            // 构造一个比 startTime 更早的 deadline → 必然校验失败
            String earlyDeadline = futureStart.minusHours(2)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            assertThrows(BusinessException.class,
                    () -> taskService.updateTask(
                            task.getId(), null, null, null, null, earlyDeadline));

            // 关键断言：原任务的 deadline 没有被改坏（先算后写生效）
            assertEquals(originalDeadline, task.getDeadline());
        }

        @Test
        @DisplayName("已完成的任务不能修改")
        void completedTaskCannotUpdate() {
            Task task = createOneTask("阅读");
            taskService.completeTask(task.getId());

            assertThrows(BusinessException.class,
                    () -> taskService.updateTask(task.getId(), "新标题", null, null, null, null));
        }
    }

    @Nested
    @DisplayName("deleteTask")
    class DeleteTask {

        @Test
        @DisplayName("删除后从列表消失，返回被删的任务")
        void deleteSuccessfully() {
            createOneTask("任务1");
            Task t2 = createOneTask("任务2");

            Task deleted = taskService.deleteTask(t2.getId());

            assertEquals(t2.getTitle(), deleted.getTitle());
            assertEquals(1, taskService.getAllTasks().size());
            assertThrows(BusinessException.class,
                    () -> taskService.getTaskById(t2.getId()));
        }

        @Test
        @DisplayName("⭐ 删除后新建任务 id 不复用（AtomicLong 回归）")
        void idNotReusedAfterDelete() {
            Task t1 = createOneTask("任务1");
            taskService.deleteTask(t1.getId());

            Task newTask = createOneTask("新任务");

            assertTrue(newTask.getId() > t1.getId());
        }
    }
}
