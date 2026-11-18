package com.luckyflow.exception;
import lombok.Getter;

/**
 * 表示预期内的业务规则拒绝，可安全转换为面向客户端的 API 错误。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    /**
     * 使用应用默认业务错误码创建业务异常。
     *
     * @param message 可安全返回客户端的操作拒绝说明
     */
    public BusinessException(String message) {
        this(1001, message);
    }

    /**
     * 使用稳定的机器可读错误码创建业务异常，HTTP 客户端不需要解析提示文案判断分支。
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
