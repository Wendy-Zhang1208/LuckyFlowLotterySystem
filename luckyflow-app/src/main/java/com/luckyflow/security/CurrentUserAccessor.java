package com.luckyflow.security;

/**
 * 为业务 Controller 提供经过认证的当前用户标识。
 *
 * <p>具体实现属于认证切片；调用方不得从请求参数或请求体接受 userId。</p>
 */
public interface CurrentUserAccessor {

    /**
     * 返回当前已经通过认证的用户标识；未认证时由安全实现拒绝访问。
     */
    long requireUserId();
}
