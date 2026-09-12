package com.moyue.common.cache;

/**
 * 全服务共享的缓存名常量（P2-16 缓存基建）。
 * <p>各业务模块在 {@code @Cacheable} / {@code @CacheEvict} / {@code @CachePut} 中统一引用本类常量，
 * 避免缓存名散落成魔法字符串；TTL 由 {@link MoyueCacheAutoConfiguration} 按缓存名集中配置。</p>
 * <p>最终 Redis key 形如 {@code moyue:chapter:catalog::<业务 key>}（前缀见 {@code MoyueCacheAutoConfiguration#KEY_PREFIX}）。</p>
 */
public final class CacheNames {

    /** 书籍详情：建议 TTL 10 分钟（书名 / 简介 / 封面等元数据变更不频繁） */
    public static final String BOOK_DETAIL = "book:detail";

    /** 书籍列表（首页 / 分类 / 榜单）：建议 TTL 5 分钟（含分页维度，数据量较大，容忍度较高） */
    public static final String BOOK_LIST = "book:list";

    /** 作品目录分页：建议 TTL 10 分钟（作者发章为低频写，读者翻目录为高频读） */
    public static final String CHAPTER_CATALOG = "chapter:catalog";

    /** 章节详情 / 正文：建议 TTL 30 分钟（正文为超大字段，命中收益最高，且发布后基本不变） */
    public static final String CHAPTER_CONTENT = "chapter:content";

    /** 用户书架：建议 TTL 5 分钟（写较频繁：加/移书架、翻章更新进度） */
    public static final String READ_BOOKSHELF = "read:bookshelf";

    private CacheNames() {
        // 常量类，禁止实例化
    }
}
