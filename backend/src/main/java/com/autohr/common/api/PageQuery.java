package com.autohr.common.api;

import com.autohr.common.exception.BusinessException;

public record PageQuery(int page, int pageSize) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

    public static PageQuery of(Integer page, Integer pageSize) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPage < 1) {
            throw new BusinessException("page必须大于等于1");
        }
        if (resolvedPageSize < 1 || resolvedPageSize > MAX_PAGE_SIZE) {
            throw new BusinessException("pageSize必须在1到" + MAX_PAGE_SIZE + "之间");
        }
        return new PageQuery(resolvedPage, resolvedPageSize);
    }
}
