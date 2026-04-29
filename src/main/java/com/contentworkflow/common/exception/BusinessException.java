package com.contentworkflow.common.exception;

/**
 * BusinessException 类，负责当前模块的业务实现。
 */
public class BusinessException extends RuntimeException {

    /**
     * 当前异常对应的业务错误码。
     */
    private final String code;

    /**
     * 构造当前类型实例，并注入运行所需依赖。
     * @param code 参数 code。
     * @param message 参数 message。
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取 getCode 相关业务信息。
     * @return 方法执行后的结果对象。
     */
    public String getCode() {
        return code;
    }
}
