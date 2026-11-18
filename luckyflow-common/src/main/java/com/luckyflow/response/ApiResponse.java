package com.luckyflow.response;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;

/**
 * LuckyFlow HTTP 接口返回的统一响应结构。
 *
 * @param <T> 响应数据类型
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T data;

    /**
     * 使用给定数据创建成功响应。
     *
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功的 API 响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    /**
     * 创建不包含数据载荷的错误响应。
     *
     * @param code 应用错误码或与 HTTP 对齐的错误码
     * @param message 面向客户端的错误说明
     * @param <T> 声明的数据类型
     * @return 错误 API 响应
     */
    public static <T> ApiResponse<T> error(int code, String message) {
       return new ApiResponse<>(code, message, null);
    }

    
}
