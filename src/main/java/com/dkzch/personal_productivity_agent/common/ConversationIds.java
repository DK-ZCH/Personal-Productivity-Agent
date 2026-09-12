package com.dkzch.personal_productivity_agent.common;

import java.util.regex.Pattern;

//conversationId 的格式契约。
public final class ConversationIds {

    public static final int MAX_LENGTH = 64;

    /** 仅允许字母、数字、下划线、连字符。 */
    private static final Pattern ALLOWED_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    private ConversationIds() {
    }

    public static boolean isValid(String conversationId) {
        if (conversationId == null) {
            return false;
        }
        String trimmed = conversationId.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return false;
        }
        return ALLOWED_PATTERN.matcher(trimmed).matches();
    }

    //归一化：trim 后的值
    public static String normalize(String conversationId) {
        return conversationId.trim();
    }
}
