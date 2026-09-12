package com.dkzch.personal_productivity_agent;

import com.dkzch.personal_productivity_agent.common.BusinessException;
import com.dkzch.personal_productivity_agent.common.ConsumeResult;
import com.dkzch.personal_productivity_agent.common.ConversationContext;
import com.dkzch.personal_productivity_agent.common.CurrentUserProvider;
import com.dkzch.personal_productivity_agent.common.PendingAction;
import com.dkzch.personal_productivity_agent.common.PendingActionStore;
import com.dkzch.personal_productivity_agent.common.ToolRiskRegistry;
import com.dkzch.personal_productivity_agent.model.dto.TaskSummary;
import com.dkzch.personal_productivity_agent.model.dto.ToolResult;
import com.dkzch.personal_productivity_agent.model.entity.Task;
import com.dkzch.personal_productivity_agent.model.enums.TaskStatus;
import com.dkzch.personal_productivity_agent.service.TaskService;
import com.dkzch.personal_productivity_agent.tool.TaskTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskToolTest {

    private static final String CONVERSATION_ID = "conv-test";
    private static final Long USER_ID = 2L;
    private static final String ACTION_ID = "172DEFD9FDE145B5";

    @Mock
    private TaskService taskService;

    @Mock
    private PendingActionStore pendingActionStore;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private TaskTool taskTool;

    @BeforeEach
    void setUp() {
        taskTool = new TaskTool(taskService, pendingActionStore, currentUserProvider);

        // ConversationContext 是 ThreadLocal：测试与 Tool 在同一线程，
        // 直接 set 即可，不需要 mockStatic
        ConversationContext.set(CONVERSATION_ID);

        // lenient：并非每个用例都会用到当前用户（如参数校验失败路径），
        // 避免 STRICT_STUBS 报 UnnecessaryStubbingException
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        ConversationContext.clear();
    }

    private Task taskWithId(Long id, String title) {
        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setUserId(USER_ID);
        task.setStatus(TaskStatus.TODO);
        return task;
    }

    // ==================== delete_task：高风险应"只登记，不执行" ====================

    @Nested
    @DisplayName("delete_task（高风险 → 转待确认）")
    class DeleteTask {

        @Test
        @DisplayName("⭐ 只登记待办并返回 pending，绝不真正删除")
        void registersPendingActionWithoutDeleting() {

            when(taskService.getTaskById(4L)).thenReturn(taskWithId(4L, "控笔训练"));

            ToolResult<TaskSummary> result = taskTool.deleteTask(4L);

            assertTrue(result.isPending(), "高风险操作应返回 pending");
            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("确认码"), "应把确认码告知 LLM");

            ArgumentCaptor<PendingAction> captor = ArgumentCaptor.forClass(PendingAction.class);
            verify(pendingActionStore).save(captor.capture());

            PendingAction saved = captor.getValue();
            assertEquals(ToolRiskRegistry.DELETE_TASK, saved.getToolName());
            assertEquals(USER_ID, saved.getUserId());
            assertEquals(CONVERSATION_ID, saved.getConversationId());
            assertEquals(4L, saved.getArgs().get("id"));
            assertFalse(saved.isConsumed());
            assertNotNull(saved.getActionId());
            assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));

            // 核心断言：服务端一次都没碰删除
            verify(taskService, never()).deleteTask(any());
        }

        @Test
        @DisplayName("任务不存在：返回失败，且不登记任何待办")
        void missingTaskDoesNotRegisterPending() {

            when(taskService.getTaskById(999L))
                    .thenThrow(new BusinessException("任务不存在，id=999"));

            ToolResult<TaskSummary> result = taskTool.deleteTask(999L);

            assertFalse(result.isSuccess());
            assertFalse(result.isPending());
            verify(pendingActionStore, never()).save(any());
            verify(taskService, never()).deleteTask(any());
        }

        @Test
        @DisplayName("id 为 null：Tool 边界拦截，不进入业务层、不登记待办")
        void nullIdRejected() {

            ToolResult<TaskSummary> result = taskTool.deleteTask(null);

            assertFalse(result.isSuccess());
            verifyNoInteractions(taskService, pendingActionStore);
        }

    }

    // ==================== confirm_action：四道闸门 ====================

    @Nested
    @DisplayName("confirm_action（四道闸门）")
    class ConfirmAction {

        private void givenConsumeReturns(ConsumeResult.Outcome outcome) {
            when(pendingActionStore.consume(ACTION_ID, USER_ID, CONVERSATION_ID))
                    .thenReturn(ConsumeResult.failure(outcome));
        }

        @Test
        @DisplayName("闸门 1：NOT_FOUND → 提示无效，且不删除")
        void notFound() {
            givenConsumeReturns(ConsumeResult.Outcome.NOT_FOUND);

            ToolResult<TaskSummary> result = taskTool.confirmAction(ACTION_ID);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("无效"));
            verify(taskService, never()).deleteTask(any());
        }

        @Test
        @DisplayName("闸门 2：EXPIRED → 明确提示超时，且不删除")
        void expired() {
            givenConsumeReturns(ConsumeResult.Outcome.EXPIRED);

            ToolResult<TaskSummary> result = taskTool.confirmAction(ACTION_ID);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("超时"));
            verify(taskService, never()).deleteTask(any());
        }

        @Test
        @DisplayName("闸门 3：ALREADY_CONSUMED → 提示已执行过，且不重复删除")
        void alreadyConsumed() {
            givenConsumeReturns(ConsumeResult.Outcome.ALREADY_CONSUMED);

            ToolResult<TaskSummary> result = taskTool.confirmAction(ACTION_ID);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("已经执行过"));
            verify(taskService, never()).deleteTask(any());
        }

        @Test
        @DisplayName("⭐ 闸门 4：FORBIDDEN 与 NOT_FOUND 话术必须完全相同（不泄露细分原因）")
        void forbiddenMessageIsIdenticalToNotFound() {

            givenConsumeReturns(ConsumeResult.Outcome.FORBIDDEN);
            String forbiddenMessage = taskTool.confirmAction(ACTION_ID).getMessage();

            givenConsumeReturns(ConsumeResult.Outcome.NOT_FOUND);
            String notFoundMessage = taskTool.confirmAction(ACTION_ID).getMessage();

            assertEquals(notFoundMessage, forbiddenMessage,
                    "两种失败必须同一话术，否则可被用来探测确认码是否存在");
            verify(taskService, never()).deleteTask(any());
        }

        @Test
        @DisplayName("四道闸门全过：执行删除并返回成功")
        void successExecutesDelete() {

            PendingAction action = new PendingAction(
                    ACTION_ID, ToolRiskRegistry.DELETE_TASK, Map.of("id", 4L),
                    USER_ID, CONVERSATION_ID,
                    LocalDateTime.now(), LocalDateTime.now().plusMinutes(10));

            when(pendingActionStore.consume(ACTION_ID, USER_ID, CONVERSATION_ID))
                    .thenReturn(ConsumeResult.success(action));
            when(taskService.deleteTask(4L)).thenReturn(taskWithId(4L, "控笔训练"));

            ToolResult<TaskSummary> result = taskTool.confirmAction(ACTION_ID);

            assertTrue(result.isSuccess());
            assertEquals("控笔训练", result.getData().getTitle());
            verify(taskService).deleteTask(4L);
        }


        @Test
        @DisplayName("确认码归一化：前后空格 + 小写也能匹配（trim + toUpperCase）")
        void actionIdIsTrimmedAndUppercased() {

            when(pendingActionStore.consume(ACTION_ID, USER_ID, CONVERSATION_ID))
                    .thenReturn(ConsumeResult.failure(ConsumeResult.Outcome.NOT_FOUND));

            // 输入同时带前导/尾随空格与小写
            taskTool.confirmAction("  " + ACTION_ID.toLowerCase() + "  ");

            // 未归一化会以原串查询，stub 不匹配 → 返回 null → 测试失败
            verify(pendingActionStore).consume(ACTION_ID, USER_ID, CONVERSATION_ID);
        }

        @Test
        @DisplayName("空白确认码：直接失败，根本不碰 store")
        void blankActionIdShortCircuits() {

            ToolResult<TaskSummary> result = taskTool.confirmAction("   ");

            assertFalse(result.isSuccess());
            verifyNoInteractions(pendingActionStore);
        }

        @Test
        @DisplayName("防御断言：SUCCESS 却未携带 action 应立即抛异常")
        void successWithoutActionThrows() {

            when(pendingActionStore.consume(ACTION_ID, USER_ID, CONVERSATION_ID))
                    .thenReturn(new ConsumeResult(ConsumeResult.Outcome.SUCCESS, null));

            assertThrows(IllegalStateException.class,
                    () -> taskTool.confirmAction(ACTION_ID));
        }
    }
}
