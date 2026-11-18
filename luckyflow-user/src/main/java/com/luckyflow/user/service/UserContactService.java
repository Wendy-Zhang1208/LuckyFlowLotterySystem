package com.luckyflow.user.service;

import com.luckyflow.user.model.UserContact;

/** 向其他模块提供用户联系方式查询能力，隐藏 User 模块内部 Mapper。 */
public interface UserContactService {

    /**
     * 查询用户当前手机号和邮箱；用户不存在时返回 {@code null}。
     */
    UserContact findContact(Long userId);
}
