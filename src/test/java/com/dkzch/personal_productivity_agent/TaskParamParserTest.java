package com.dkzch.personal_productivity_agent;

import com.dkzch.personal_productivity_agent.common.TaskParamParser;
import com.dkzch.personal_productivity_agent.model.enums.TaskPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskParamParserTest {

    @Nested
    @DisplayName("parsePriority")
    class ParsePriority {

        @ParameterizedTest
        @ValueSource(strings = {"HIGH", "MEDIUM", "LOW"})
        @DisplayName("标准英文枚举值直接返回对应枚举")
        void standardEnums(String input) {
            assertEquals(TaskPriority.valueOf(input), TaskParamParser.parsePriority(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {"high", "High", "hIgH"})
        @DisplayName("大小写不敏感")
        void caseInsensitive(String input) {
            assertEquals(TaskPriority.HIGH, TaskParamParser.parsePriority(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {"高", "高优先级", "紧急"})
        @DisplayName("明确中文别名应映射到 HIGH")
        void chineseAliasesForHigh(String input) {
            assertEquals(TaskPriority.HIGH, TaskParamParser.parsePriority(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {"中", "中优先级", "普通"})
        @DisplayName("明确中文别名应映射到 MEDIUM")
        void chineseAliasesForMedium(String input) {
            assertEquals(TaskPriority.MEDIUM, TaskParamParser.parsePriority(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {"低", "低优先级"})
        @DisplayName("明确中文别名应映射到 LOW")
        void chineseAliasesForLow(String input) {
            assertEquals(TaskPriority.LOW, TaskParamParser.parsePriority(input));
        }

        @Test
        @DisplayName("URGENT（非常用英文）应映射到 HIGH")
        void urgentAlias() {
            assertEquals(TaskPriority.HIGH, TaskParamParser.parsePriority("URGENT"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"超级高", "不高", "高高高", "高一点", "abc", " "})
        @DisplayName("无法识别时应降级为 MEDIUM（不抛异常）")
        void unrecognizedDowngrades(String input) {
            assertEquals(TaskPriority.MEDIUM, TaskParamParser.parsePriority(input));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("null 或空字符串应返回 MEDIUM")
        void nullOrEmpty(String input) {
            assertEquals(TaskPriority.MEDIUM, TaskParamParser.parsePriority(input));
        }
    }

    @Nested
    @DisplayName("parseDateTime")
    class ParseDateTime {

        @Test
        @DisplayName("标准 ISO-8601（带 T）应正确解析")
        void isoFormat() {
            LocalDateTime expected = LocalDateTime.of(2026, 9, 8, 19, 0, 0);
            assertEquals(expected, TaskParamParser.parseDateTime("2026-09-08T19:00:00"));
        }

        @Test
        @DisplayName("空格分隔的日期时间应正确解析")
        void spaceFormat() {
            LocalDateTime expected = LocalDateTime.of(2026, 9, 8, 19, 0, 0);
            assertEquals(expected, TaskParamParser.parseDateTime("2026-09-08 19:00:00"));
        }

        @Test
        @DisplayName("仅日期不合法（没有时间）应返回 null")
        void dateOnly() {
            assertNull(TaskParamParser.parseDateTime("2026-09-08"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"明天晚上七点", "今天", "abc", "2026-13-01", "2026-09-08T25:00"})
        @DisplayName("模糊或非法格式应返回 null（不抛异常）")
        void unrecognizedReturnsNull(String input) {
            assertNull(TaskParamParser.parseDateTime(input));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("null 或空字符串应返回 null")
        void nullOrEmpty(String input) {
            assertNull(TaskParamParser.parseDateTime(input));
        }
    }
}
