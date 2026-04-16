package com.contentworkflow.testing;

import com.contentworkflow.common.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */

public final class BusinessExceptionAssertions {

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */

    private BusinessExceptionAssertions() {
    }

    /**
     * 处理 assert code 相关逻辑，并返回对应的执行结果。
     *
     * @param ex 异常对象
     * @param expectedCode 参数 expectedCode 对应的业务输入值
     */

    public static void assertCode(BusinessException ex, String expectedCode) {
        assertNotNull(ex, "exception must not be null");
        assertEquals(expectedCode, ex.getCode(), "unexpected BusinessException code");
    }
}

