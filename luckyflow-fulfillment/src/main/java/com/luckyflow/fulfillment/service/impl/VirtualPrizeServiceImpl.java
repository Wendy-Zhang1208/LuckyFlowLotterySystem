package com.luckyflow.fulfillment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.luckyflow.fulfillment.entity.UserVirtualPrize;
import com.luckyflow.fulfillment.mapper.UserVirtualPrizeMapper;
import com.luckyflow.fulfillment.service.VirtualPrizeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.luckyflow.fulfillment.constant.UserVirtualPrizeStatusConstants.USER_VIRTUAL_PRIZE_STATUS_UNUSED;

/** 基于中奖记录唯一约束发放虚拟奖品。 */
@Service
public class VirtualPrizeServiceImpl implements VirtualPrizeService {

    private final UserVirtualPrizeMapper userVirtualPrizeMapper;

    public VirtualPrizeServiceImpl(UserVirtualPrizeMapper userVirtualPrizeMapper) {
        this.userVirtualPrizeMapper = userVirtualPrizeMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void initializeVirtualPrize(
            Long winningRecordId,
            Long userId,
            Long prizeId,
            LocalDateTime now
    ) {
        UserVirtualPrize virtualPrize = new UserVirtualPrize();
        virtualPrize.setWinningRecordId(winningRecordId);
        virtualPrize.setUserId(userId);
        virtualPrize.setPrizeId(prizeId);
        virtualPrize.setStatus(USER_VIRTUAL_PRIZE_STATUS_UNUSED);
        virtualPrize.setExpireTime(null);
        virtualPrize.setUseTime(null);
        virtualPrize.setCreateTime(now);
        virtualPrize.setUpdateTime(now);

        try {
            int affectedRows = userVirtualPrizeMapper.insert(virtualPrize);
            if (affectedRows != 1) {
                throw new IllegalStateException("Failed to initialize user virtual prize");
            }
            if (virtualPrize.getId() == null) {
                throw new IllegalStateException("User virtual prize ID was not generated");
            }
        } catch (DuplicateKeyException duplicatePrize) {
            // 唯一键竞争后使用锁定当前读核对事实，避免把冲突数据误判为幂等成功。
            UserVirtualPrize existingPrize =
                    userVirtualPrizeMapper.selectByWinningRecordIdForShare(winningRecordId);
            validateExistingPrize(existingPrize, winningRecordId, userId, prizeId);
        }
    }

    @Override
    public UserVirtualPrize findByWinningRecordId(Long winningRecordId) {
        return userVirtualPrizeMapper.selectOne(
                Wrappers.<UserVirtualPrize>lambdaQuery()
                        .eq(UserVirtualPrize::getWinningRecordId, winningRecordId)
        );
    }

    private void validateExistingPrize(
            UserVirtualPrize existingPrize,
            Long winningRecordId,
            Long userId,
            Long prizeId
    ) {
        if (existingPrize == null) {
            throw new IllegalStateException(
                    "Duplicate virtual prize was reported but the existing record was not found"
            );
        }
        if (!Objects.equals(existingPrize.getWinningRecordId(), winningRecordId)
                || !Objects.equals(existingPrize.getUserId(), userId)
                || !Objects.equals(existingPrize.getPrizeId(), prizeId)) {
            throw new IllegalStateException(
                    "Existing virtual prize conflicts with the current winning fact"
            );
        }
    }
}
