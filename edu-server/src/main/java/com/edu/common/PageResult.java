package com.edu.common;

import lombok.Data;
import java.util.List;

/** 分页结果 */
@Data
public class PageResult<T> {
    private long total;
    private List<T> records;

    public static <T> PageResult<T> of(long total, List<T> records) {
        PageResult<T> p = new PageResult<>();
        p.total = total; p.records = records;
        return p;
    }
}
