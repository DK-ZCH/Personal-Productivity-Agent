package com.dkzch.personal_productivity_agent;

import com.dkzch.personal_productivity_agent.common.BusinessException;
import com.dkzch.personal_productivity_agent.model.dto.CreateTaskRequest;
import com.dkzch.personal_productivity_agent.model.entity.Task;
import com.dkzch.personal_productivity_agent.model.enums.TaskPriority;
import com.dkzch.personal_productivity_agent.model.enums.TaskStatus;
import com.dkzch.personal_productivity_agent.repository.TaskRepository;
import com.dkzch.personal_productivity_agent.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    private TaskService taskService;

    /** 模拟数据库 AUTO_INCREMENT：save 时为无 id 的实体回填 id */
    private final AtomicLong idCounter = new AtomicLong(1);

    private LocalDateTime futureStart;
    private LocalDateTime futureDeadline;

    @BeforeEach
    void setUp() {
        // 手动构造器注入：依赖关系一目了然
        taskService = new TaskService(taskRepository);
        futureStart = LocalDateTime.now().plusDays(1).withNano(0);
        futureDeadline = futureStart.plusHours(1);

        lenient().when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(idCounter.getAndIncrement());
            }
            return t;
        });
    }

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
        @DisplayName("空白标题抛 BusinessException")
        void blankTitleRejected() {
            CreateTaskRequest request = new CreateTaskRequest();
            request.setTitle("   ");
            request.setPriority(TaskPriority.MEDIUM);
            request.setStartTime(futureStart);
            request.setDeadline(futureDeadline);

            assertThrows(BusinessException.class, () -> taskService.createTask(request));
        }

        @Test
        @DisplayName("过去时间抛 BusinessException")
        void pastTimeRejected() {
            CreateTaskRequest request = new CreateTaskRequest();
            request.setTitle("过去任务");
            request.setPriority(TaskPriority.MEDIUM);
            request.setStartTime(LocalDateTime.now().minusDays(1));
            request.setDeadline(LocalDateTime.now().plusDays(1));

            assertThrows(BusinessException.class, () -> taskService.createTask(request));
        }

        @Test
        @DisplayName("deadline 等于 startTime 也应拒绝")
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

            when(taskRepository.findById(created.getId()))
                    .thenReturn(Optional.of(created));

            Task found = taskService.getTaskById(created.getId());
            assertEquals(created.getTitle(), found.getTitle());
        }

        @Test
        @DisplayName("不存在的 id 抛 BusinessException")
        void missingTaskThrows() {
            when(taskRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> taskService.getTaskById(999L));
        }
    }

    @Nested
    @DisplayName("searchTasks")
    class SearchTasks {

        @Test
        @DisplayName("title 匹配：关键字委托给 Repository")
        void matchTitle() {
            Task created = createOneTask("控笔训练");

            when(taskRepository.searchByKeyword("控笔")).thenReturn(List.of(created));

            List<Task> result = taskService.searchTasks("控笔");

            assertEquals(1, result.size());
            assertEquals("控笔训练", result.get(0).getTitle());
        }

        @Test
        @DisplayName("description 匹配：关键字委托给 Repository")
        void matchDescription() {
            Task created = createOneTask("数学");

            when(taskRepository.searchByKeyword("描述-数学")).thenReturn(List.of(created));

            List<Task> result = taskService.searchTasks("描述-数学");

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("无匹配返回空列表")
        void noMatchReturnsEmpty() {
            when(taskRepository.searchByKeyword("编程")).thenReturn(List.of());

            assertTrue(taskService.searchTasks("编程").isEmpty());
        }

        @Test
        @DisplayName("空白关键字返回全部")
        void blankKeywordReturnsAll() {
            Task t1 = createOneTask("任务1");
            Task t2 = createOneTask("任务2");

            when(taskRepository.findAll()).thenReturn(List.of(t1, t2));

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

            // ⭐ 显式 stub：Service 内部会 findById 取回这个任务
            when(taskRepository.findById(task.getId()))
                    .thenReturn(Optional.of(task));

            Task completed = taskService.completeTask(task.getId());

            assertEquals(TaskStatus.COMPLETED, completed.getStatus());
            assertNotNull(completed.getCompletedAt());
        }

        @Test
        @DisplayName("重复完成抛 BusinessException")
        void doubleCompleteThrows() {
            Task task = createOneTask("阅读");
            when(taskRepository.findById(task.getId()))
                    .thenReturn(Optional.of(task));

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
            when(taskRepository.findById(task.getId()))
                    .thenReturn(Optional.of(task));

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
            // ⭐ 必须显式 stub：否则 findById 返回 empty，
            //    测试会因"任务不存在"假通过，而非"至少一个字段"被拒
            when(taskRepository.findById(task.getId()))
                    .thenReturn(Optional.of(task));

            assertThrows(BusinessException.class,
                    () -> taskService.updateTask(task.getId(), null, null, null, null, null));
        }

        @Test
        @DisplayName("⭐ 失败原子性：校验失败时原任务不被改动")
        void failedUpdateKeepsOriginal() {
            Task task = createOneTask("阅读");
            when(taskRepository.findById(task.getId()))
                    .thenReturn(Optional.of(task));
            LocalDateTime originalDeadline = task.getDeadline();

            String earlyDeadline = futureStart.minusHours(2)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            assertThrows(BusinessException.class,
                    () -> taskService.updateTask(
                            task.getId(), null, null, null, null, earlyDeadline));

            assertEquals(originalDeadline, task.getDeadline());
        }

        @Test
        @DisplayName("已完成的任务不能修改")
        void completedTaskCannotUpdate() {
            Task task = createOneTask("阅读");
            when(taskRepository.findById(task.getId()))
                    .thenReturn(Optional.of(task));

            taskService.completeTask(task.getId());

            assertThrows(BusinessException.class,
                    () -> taskService.updateTask(task.getId(), "新标题", null, null, null, null));
        }
    }

    @Nested
    @DisplayName("deleteTask")
    class DeleteTask {

        @Test
        @DisplayName("删除调用 Repository 的 delete")
        void deleteDelegatesToRepository() {
            Task task = createOneTask("任务1");
            when(taskRepository.findById(task.getId()))
                    .thenReturn(Optional.of(task));

            Task deleted = taskService.deleteTask(task.getId());

            assertEquals(task.getTitle(), deleted.getTitle());
            verify(taskRepository).delete(task);
        }

        @Test
        @DisplayName("删除不存在的任务抛 BusinessException")
        void deleteMissingThrows() {
            when(taskRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> taskService.deleteTask(999L));
        }
    }
}
