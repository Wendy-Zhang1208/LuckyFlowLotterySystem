package com.luckyflow.controller.admin;

import com.luckyflow.exception.ApiErrorCodes;
import com.luckyflow.exception.BusinessException;
import com.luckyflow.marketing.entity.Prize;
import com.luckyflow.marketing.service.PrizeService;
import com.luckyflow.publicapi.HttpIdParser;
import com.luckyflow.response.ApiResponse;
import com.luckyflow.response.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 运营共享奖品目录管理；活动发布后共享 Prize 定义不可再改，需创建新 Prize。 */
@RestController
@RequestMapping("/api/admin/prizes")
public class AdminPrizeController {

    private final PrizeService prizeService;

    public AdminPrizeController(PrizeService prizeService) {
        this.prizeService = prizeService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PrizeView>> findPrizes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PrizeService.PrizePage result = prizeService.findPrizes(
                keyword,
                type,
                status,
                page,
                size
        );
        return ApiResponse.success(new PageResponse<>(
                result.records().stream().map(PrizeView::from).toList(),
                page,
                size,
                result.total()
        ));
    }

    @GetMapping("/{prizeId}")
    public ApiResponse<PrizeView> findPrize(@PathVariable String prizeId) {
        return ApiResponse.success(PrizeView.from(requirePrize(
                HttpIdParser.requirePositiveLong(prizeId, "prizeId")
        )));
    }

    @PostMapping
    public ApiResponse<PrizeView> createPrize(@RequestBody PrizeInput request) {
        return ApiResponse.success(PrizeView.from(prizeService.createPrize(request.toEntity(null))));
    }

    @PatchMapping("/{prizeId}")
    public ApiResponse<PrizeView> updatePrize(
            @PathVariable String prizeId,
            @RequestBody PrizeInput request
    ) {
        long id = HttpIdParser.requirePositiveLong(prizeId, "prizeId");
        prizeService.updatePrizeConfiguration(request.toEntity(id));
        return ApiResponse.success(PrizeView.from(requirePrize(id)));
    }

    private Prize requirePrize(long prizeId) {
        Prize prize = prizeService.findPrize(prizeId);
        if (prize == null) {
            throw new BusinessException(ApiErrorCodes.RESOURCE_NOT_FOUND, "奖品不存在");
        }
        return prize;
    }

    public record PrizeInput(
            String name,
            String description,
            String illustration,
            Integer type,
            Integer totalInventory,
            Integer availableInventory,
            BigDecimal value,
            Integer status
    ) {
        Prize toEntity(Long id) {
            Prize prize = new Prize();
            prize.setId(id);
            prize.setName(name);
            prize.setDescription(description);
            prize.setIllustration(illustration);
            prize.setType(type);
            prize.setTotalInventory(totalInventory);
            prize.setAvailableInventory(availableInventory);
            prize.setValue(value);
            prize.setStatus(status);
            return prize;
        }
    }

    public record PrizeView(
            String id,
            String name,
            String description,
            String illustration,
            Integer type,
            Integer totalInventory,
            Integer availableInventory,
            BigDecimal value,
            Integer status,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
        static PrizeView from(Prize prize) {
            return new PrizeView(
                    Long.toString(prize.getId()),
                    prize.getName(),
                    prize.getDescription(),
                    prize.getIllustration(),
                    prize.getType(),
                    prize.getTotalInventory(),
                    prize.getAvailableInventory(),
                    prize.getValue(),
                    prize.getStatus(),
                    prize.getCreateTime(),
                    prize.getUpdateTime()
            );
        }
    }
}
