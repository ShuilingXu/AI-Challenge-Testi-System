package com.autohr.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> items;
    private long total;
    private int page;
    private int pageSize;

    public static <T> PageResponse<T> of(List<T> items, long total, PageQuery query) {
        return new PageResponse<>(List.copyOf(items), total, query.page(), query.pageSize());
    }

    public static <T> PageResponse<T> slice(List<T> items, PageQuery query) {
        int fromIndex = (int) Math.min((long) (query.page() - 1) * query.pageSize(), items.size());
        int toIndex = Math.min(fromIndex + query.pageSize(), items.size());
        return of(items.subList(fromIndex, toIndex), items.size(), query);
    }
}
