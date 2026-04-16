package com.contentworkflow.common.exception;

/**
 * 业务异常类型，用于表达可预期的业务失败场景并向上层传递错误码。
 */
public class BusinessException extends RuntimeException {

    /**
     * 当前异常对应的业务错误码。
     */
    private final String code;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param code 业务错误码
     * @param message 提示信息
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */
    public String getCode() {
        return code;
    }
}
