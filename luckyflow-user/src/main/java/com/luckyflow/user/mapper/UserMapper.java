package com.luckyflow.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户账户的 MyBatis-Plus 数据访问接口。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    String AUTH_COLUMNS = "id, account, nickname, password_hash, birth_date, avatar_key, "
            + "phone, phone_verified_at, email, email_verified_at, role, status, create_time, update_time";

    @Select("SELECT " + AUTH_COLUMNS + " FROM `user` WHERE account = #{account} LIMIT 1")
    User selectByAccount(@Param("account") String account);

    @Select("SELECT " + AUTH_COLUMNS + " FROM `user` WHERE email = #{email} LIMIT 1")
    User selectByEmail(@Param("email") String email);

    @Select("SELECT " + AUTH_COLUMNS + " FROM `user` WHERE phone = #{phone} LIMIT 1")
    User selectByPhone(@Param("phone") String phone);

    @Select("SELECT " + AUTH_COLUMNS + " FROM `user` WHERE email = #{email} LIMIT 1 FOR SHARE")
    User selectByEmailForShare(@Param("email") String email);

    @Select("SELECT " + AUTH_COLUMNS + " FROM `user` WHERE phone = #{phone} LIMIT 1 FOR SHARE")
    User selectByPhoneForShare(@Param("phone") String phone);

    @Select("SELECT " + AUTH_COLUMNS + " FROM `user` WHERE id = #{userId} FOR UPDATE")
    User selectByIdForUpdate(@Param("userId") Long userId);

    @Update("UPDATE `user` SET nickname = #{nickname}, birth_date = #{birthDate}, "
            + "avatar_key = #{avatarKey}, update_time = #{updateTime} WHERE id = #{userId}")
    int updateProfile(
            @Param("userId") Long userId,
            @Param("nickname") String nickname,
            @Param("birthDate") LocalDate birthDate,
            @Param("avatarKey") String avatarKey,
            @Param("updateTime") LocalDateTime updateTime
    );

    @Update("UPDATE `user` SET password_hash = #{newHash}, update_time = #{updateTime} "
            + "WHERE id = #{userId} AND ((#{expectedHash} IS NULL AND password_hash IS NULL) "
            + "OR password_hash = #{expectedHash})")
    int replacePassword(
            @Param("userId") Long userId,
            @Param("expectedHash") String expectedHash,
            @Param("newHash") String newHash,
            @Param("updateTime") LocalDateTime updateTime
    );

    @Update("UPDATE `user` SET email = #{email}, email_verified_at = #{verifiedAt}, "
            + "update_time = #{updateTime} WHERE id = #{userId}")
    int updateEmail(
            @Param("userId") Long userId,
            @Param("email") String email,
            @Param("verifiedAt") LocalDateTime verifiedAt,
            @Param("updateTime") LocalDateTime updateTime
    );

    @Update("UPDATE `user` SET phone = #{phone}, phone_verified_at = #{verifiedAt}, "
            + "update_time = #{updateTime} WHERE id = #{userId}")
    int updatePhone(
            @Param("userId") Long userId,
            @Param("phone") String phone,
            @Param("verifiedAt") LocalDateTime verifiedAt,
            @Param("updateTime") LocalDateTime updateTime
    );

    List<User> selectAdminPage(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAdminPage(
            @Param("keyword") String keyword,
            @Param("status") Integer status
    );
}
