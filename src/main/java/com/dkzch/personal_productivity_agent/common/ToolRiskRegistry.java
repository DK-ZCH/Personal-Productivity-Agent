package com.dkzch.personal_productivity_agent.common;

import java.util.Map;

//Tool 风险分级清单（单一事实来源）。
public final class ToolRiskRegistry {

    public static final String CREATE_TASK = "create_task";
    public static final String LIST_TASKS = "list_tasks";
    public static final String GET_TASK = "get_task";
    public static final String SEARCH_TASKS = "search_tasks";
    public static final String UPDATE_TASK = "update_task";
    public static final String COMPLETE_TASK = "complete_task";
    public static final String DELETE_TASK = "delete_task";

    private static final Map<String, ToolRiskLevel> RISK_LEVELS = Map.of(
            // 新增数据：不破坏既有数据，且可通过 update/delete 后续处理
            CREATE_TASK, ToolRiskLevel.LOW,

            // 只读：不改变任何数据
            LIST_TASKS, ToolRiskLevel.LOW,
            GET_TASK, ToolRiskLevel.LOW,
            SEARCH_TASKS, ToolRiskLevel.LOW,

            // 改变既有任务状态；当前系统无 reopen/undo 能力，因此不是"可逆"操作
            COMPLETE_TASK, ToolRiskLevel.MEDIUM,

            // 修改既有数据内容
            UPDATE_TASK, ToolRiskLevel.MEDIUM,

            // 不可恢复
            DELETE_TASK, ToolRiskLevel.HIGH
    );


    private ToolRiskRegistry() {
    }

    //查 Tool 的风险等级。未登记的 Tool 返回 HIGH——
    public static ToolRiskLevel riskLevelOf(String toolName) {
        return RISK_LEVELS.getOrDefault(toolName, ToolRiskLevel.HIGH);
    }

    //是否需要用户确认后才执行。
    public static boolean requiresConfirmation(String toolName) {
        return riskLevelOf(toolName) == ToolRiskLevel.HIGH;
    }
}
