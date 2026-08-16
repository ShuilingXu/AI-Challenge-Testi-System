package com.autohr.common.api;

import com.autohr.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageResponseTest {

    @Test
    void slicesItemsAndKeepsTheOriginalTotal() {
        PageResponse<Integer> page = PageResponse.slice(List.of(1, 2, 3, 4, 5), PageQuery.of(2, 2));

        assertEquals(List.of(3, 4), page.getItems());
        assertEquals(5, page.getTotal());
        assertEquals(2, page.getPage());
        assertEquals(2, page.getPageSize());
    }

    @Test
    void rejectsInvalidPaginationParameters() {
        assertThrows(BusinessException.class, () -> PageQuery.of(0, 20));
        assertThrows(BusinessException.class, () -> PageQuery.of(1, 0));
        assertThrows(BusinessException.class, () -> PageQuery.of(1, PageQuery.MAX_PAGE_SIZE + 1));
    }
}
