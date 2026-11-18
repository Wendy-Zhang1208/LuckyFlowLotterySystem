package com.luckyflow.user.service.impl;

import com.luckyflow.user.entity.User;
import com.luckyflow.user.mapper.UserMapper;
import com.luckyflow.user.model.UserContact;
import com.luckyflow.user.service.UserContactService;
import org.springframework.stereotype.Service;

/** User 模块内部的联系方式查询实现。 */
@Service
public class UserContactServiceImpl implements UserContactService {

    private final UserMapper userMapper;

    public UserContactServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserContact findContact(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return new UserContact(user.getId(), user.getPhone(), user.getEmail());
    }
}
