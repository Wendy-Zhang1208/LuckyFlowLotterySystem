package com.luckyflow.lottery.service.Interface;

import com.luckyflow.lottery.model.DrawHistoryEntry;
import com.luckyflow.lottery.model.WinningHistoryEntry;

import java.util.List;

/** 对外提供当前用户已提交抽奖事实和中奖事实的只读查询能力。 */
public interface DrawHistoryService {

    record DrawHistoryPage(List<DrawHistoryEntry> records, long total) {
        public DrawHistoryPage {
            records = records == null ? List.of() : List.copyOf(records);
        }
    }

    record WinningHistoryPage(List<WinningHistoryEntry> records, long total) {
        public WinningHistoryPage {
            records = records == null ? List.of() : List.copyOf(records);
        }
    }

    DrawHistoryPage findDrawHistory(Long userId, int page, int size);

    WinningHistoryPage findWinningHistory(Long userId, int page, int size);
}
