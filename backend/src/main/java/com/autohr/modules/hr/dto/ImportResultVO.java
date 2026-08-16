package com.autohr.modules.hr.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResultVO {
    private int successCount;
    private int failureCount;
    private List<RowResult> rows = new ArrayList<>();

    public void success(int row, String message) { success(row, message, null, null); }
    public void success(int row, String message, Long employeeId, String salaryMonth) {
        successCount++;
        rows.add(new RowResult(row, true, message, employeeId, salaryMonth));
    }
    public void failure(int row, String message) {
        failureCount++;
        rows.add(new RowResult(row, false, message, null, null));
    }

    @Data
    public static class RowResult {
        private final int row;
        private final boolean success;
        private final String message;
        private final Long employeeId;
        private final String salaryMonth;
    }
}
