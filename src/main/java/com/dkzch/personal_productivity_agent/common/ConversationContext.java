package com.dkzch.personal_productivity_agent.common;

//当前请求的会话上下文
public final class ConversationContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private ConversationContext() {
    }

    /** 只接受已通过 ConversationIds.isValid 校验并归一化后的值。 */
    public static void set(String conversationId) {
        HOLDER.set(conversationId);
    }

    /**
     * 未设置时抛异常而非返回默认值：这是编程错误
     * （Controller 忘了 set），必须立刻暴露，不能静默降级。
     */
    public static String getConversationId() {
        String value = HOLDER.get();
        if (value == null) {
            throw new IllegalStateException(
                    "ConversationContext 未设置：Controller 进入请求时必须调用 ConversationContext.set(...)");
        }
        return value;
    }

    /** 必须在请求结束时调用——Tomcat 线程池复用线程，不清理会串会话。 */
    public static void clear() {
        HOLDER.remove();
    }
}
