package com.dkzch.personal_productivity_agent.aspect;

import com.dkzch.personal_productivity_agent.model.dto.ToolResult;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Tool 调用的统一观测切面。
 *
 * <p>切所有带 {@code @Tool} 注解的方法，记录：工具名、入参、耗时、结果、异常。
 * 业务代码（TaskTool）内部不写任何观测日志——观察与功能解耦。
 */
@Aspect
@Component
public class ToolCallObserver {

    private static final Logger log = LoggerFactory.getLogger(ToolCallObserver.class);

    /** 单个参数值的最大记录长度，防止超长文本刷爆日志 */
    private static final int MAX_ARG_LENGTH = 120;

    @Around("@annotation(tool)")
    public Object observe(ProceedingJoinPoint pjp, Tool tool) throws Throwable {

        String toolName = tool.name();
        String args = describeArgs(pjp.getArgs());
        long startNanos = System.nanoTime();

        try {
            Object result = pjp.proceed();
            long costMs = (System.nanoTime() - startNanos) / 1_000_000;

            log.info("tool 调用完成 | name={} | 耗时={}ms | 结果={} | 参数={} | 返回={}",
                    toolName, costMs, describeOutcome(result), args, describeResult(result));

            return result;

        } catch (Throwable e) {
            long costMs = (System.nanoTime() - startNanos) / 1_000_000;

            log.warn("tool 调用异常 | name={} | 耗时={}ms | 参数={} | 异常={}: {}",
                    toolName, costMs, args, e.getClass().getSimpleName(), e.getMessage());

            // 必须原样抛出：观测不能改变调用行为
            throw e;
        }
    }

    /** 入参摘要：逐个截断，避免长文本/敏感内容整段入日志 */
    private String describeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "无";
        }
        return Arrays.stream(args)
                .map(this::abbreviate)
                .collect(Collectors.joining(", "));
    }

    private String abbreviate(Object value) {
        if (value == null) {
            return "null";
        }
        String text = String.valueOf(value);
        if (text.length() <= MAX_ARG_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_ARG_LENGTH) + "…(共" + text.length() + "字符)";
    }

    /** 结果摘要：区分 SUCCESS / FAILURE / PENDING 三态 */
    private String describeOutcome(Object result) {
        if (result instanceof ToolResult<?> toolResult) {
            if (toolResult.isPending()) {
                return "PENDING";
            }
            return toolResult.isSuccess() ? "SUCCESS" : "FAILURE";
        }
        return "UNKNOWN";
    }

    /**
     * 只记录 ToolResult 的 message，不记录 data。
     * data 可能包含大量结构化业务数据，会显著增加日志体积和敏感信息暴露面；
     * 当前 Step 主要验证 Tool 调用链，因此暂不把完整业务数据复制进日志。
     */
    private String describeResult(Object result) {
        if (result instanceof ToolResult<?> toolResult) {
            return abbreviate(toolResult.getMessage());
        }
        return (result == null) ? "null" : result.getClass().getSimpleName();
    }

}
