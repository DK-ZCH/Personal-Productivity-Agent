package com.dkzch.personal_productivity_agent.model.dto;

//Agent Tool 的统一返回契约。
public class ToolResult<T> {

    private boolean success;

    private String message;

    private T data;

    private ToolResult(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    //true 表示"等待用户确认"，既非成功也非失败。
    private boolean pending;

    public static <T> ToolResult<T> pending(String message) {
        ToolResult<T> r = new ToolResult<>(false, message, null);
        r.pending = true;
        return r;
    }

    public boolean isPending() {
        return pending;
    }


    public static <T> ToolResult<T> success(String message, T data) {
        return new ToolResult<>(true, message, data);
    }

    public static <T> ToolResult<T> failure(String message) {
        return new ToolResult<>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
