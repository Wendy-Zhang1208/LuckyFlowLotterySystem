package com.luckyflow.user.model;

/**
 * 用户当前联系方式的只读投影，避免跨模块查询时暴露密码摘要等无关敏感字段。
 */
public record UserContact(Long userId, String phone, String email) {
}
