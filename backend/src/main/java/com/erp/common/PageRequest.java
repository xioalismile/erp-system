package com.erp.common;

import lombok.Data;

/**
 * 通用分页请求参数
 */
@Data
public class PageRequest {
    private int current = 1;
    private int size = 20;

    public int getPage() { return current; }
    public int getPageSize() { return size; }
    public long getOffset() { return (long) (current - 1) * size; }
}
