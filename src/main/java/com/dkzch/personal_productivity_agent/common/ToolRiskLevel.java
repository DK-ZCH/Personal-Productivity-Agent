package com.dkzch.personal_productivity_agent.common;

//Agent Tool 的风险等级。
public enum ToolRiskLevel {

    //只读或可逆、影响面小：直接执行
    LOW,

    //会修改既有数据但可恢复：建议确认
    MEDIUM,

    //删除、不可逆、影响面大：必须确认后执行
    HIGH
}
