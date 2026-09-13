package com.moyue.search.constant;

/**
 * 检索排序方式（P2-13）。
 *
 * <p>取值（大小写不敏感）：{@code relevance}（默认）/ {@code hot} / {@code latest}；
 * 非法值或缺失一律回退 {@link #RELEVANCE}（不报错，保持既有行为向后兼容）。</p>
 */
public enum SearchSort {

    /** 相关度：ES 默认 _score 降序 */
    RELEVANCE("relevance"),

    /** 热度：hotScore 降序 */
    HOT("hot"),

    /** 最新：updateTime 降序 */
    LATEST("latest");

    private final String value;

    SearchSort(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** 解析排序方式；null / 空白 / 未知值 → {@link #RELEVANCE} */
    public static SearchSort fromValue(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        String v = value.trim().toLowerCase();
        for (SearchSort sort : values()) {
            if (sort.value.equals(v)) {
                return sort;
            }
        }
        return RELEVANCE;
    }
}
