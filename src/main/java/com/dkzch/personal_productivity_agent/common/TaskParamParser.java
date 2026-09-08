package com.dkzch.personal_productivity_agent.common;

import com.dkzch.personal_productivity_agent.model.enums.TaskPriority;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

//Tool 参数解析工具。
public final class TaskParamParser {

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final Map<String, TaskPriority> PRIORITY_ALIASES = Map.of(
            "高", TaskPriority.HIGH,
            "高优先级", TaskPriority.HIGH,
            "紧急", TaskPriority.HIGH,
            "URGENT", TaskPriority.HIGH,

            "中", TaskPriority.MEDIUM,
            "中优先级", TaskPriority.MEDIUM,
            "普通", TaskPriority.MEDIUM,

            "低", TaskPriority.LOW,
            "低优先级", TaskPriority.LOW
    );

    private TaskParamParser() {
    }

    //解析任务优先级。无法识别时降级为 MEDIUM。
    public static TaskPriority parsePriority(String raw) {

        if (raw == null || raw.isBlank()) {
            return TaskPriority.MEDIUM;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);

        try {
            return TaskPriority.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
        }

        TaskPriority alias = PRIORITY_ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }

        return TaskPriority.MEDIUM;
    }


    // 解析日期时间。无法识别时返回 null。
    public static LocalDateTime parseDateTime(String raw) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String text = raw.trim();

        try {
            return LocalDateTime.parse(text, ISO_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其他格式
        }

        // 兼容 "2026-09-08 19:00:00" 这类用空格分隔日期与时间的写法
        try {
            return LocalDateTime.parse(text.replace(' ', 'T'), ISO_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
