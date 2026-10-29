package com.edu.common;

import java.util.Locale;

/** 列表排序白名单工具：仅允许指定字段参与排序，防止SQL注入 */
public class SafeSort {

    /**
     * 生成ORDER BY子句
     * @param sortBy    前端传入的排序字段(驼峰属性名)
     * @param direction asc/desc
     * @param defaultProp 默认排序字段
     * @param allowed   允许排序的字段白名单
     */
    public static String order(String sortBy, String direction, String defaultProp, String... allowed) {
        String prop = defaultProp;
        if (sortBy != null && !sortBy.isEmpty()) {
            for (String a : allowed) {
                if (a.equalsIgnoreCase(sortBy)) { prop = a; break; }
            }
        }
        String dir = "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
        return " ORDER BY " + camelToSnake(prop) + " " + dir;
    }

    static String camelToSnake(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
