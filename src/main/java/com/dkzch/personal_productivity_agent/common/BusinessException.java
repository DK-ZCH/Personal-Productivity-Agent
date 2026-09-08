package com.dkzch.personal_productivity_agent.common;

//业务规则校验失败时抛出。
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
