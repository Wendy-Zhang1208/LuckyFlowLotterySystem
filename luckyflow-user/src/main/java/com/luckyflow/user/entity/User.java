package com.luckyflow.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * LuckyFlow 用户账户的持久化模型。
 *
 * <p>由于 {@code user} 在部分 SQL 方言中属于保留字，因此表名使用引号包裹。</p>
 */
@Getter
@Setter
@TableName("`user`")
public class User {

    /** 数据库生成的主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String account;

    private String nickname;

    private String passwordHash;

    private LocalDate birthDate;

    private String avatarKey;

    private String phone;

    private LocalDateTime phoneVerifiedAt;

    private String email;

    private LocalDateTime emailVerifiedAt;

    private String role;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
