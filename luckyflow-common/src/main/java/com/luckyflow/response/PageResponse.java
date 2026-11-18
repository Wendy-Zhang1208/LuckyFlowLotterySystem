package com.luckyflow.response;

import java.util.List;

/**
 * 对外列表接口统一使用的一基分页结构。
 *
 * @param records 当前页数据
 * @param page 当前页码，从 1 开始
 * @param size 每页条数
 * @param total 满足条件的总记录数
 */
public record PageResponse<T>(List<T> records, long page, long size, long total) {

    public PageResponse {
        records = records == null ? List.of() : List.copyOf(records);
        if (page < 1 || size < 1 || total < 0) {
            throw new IllegalArgumentException("Invalid page response metadata");
        }
    }
}
