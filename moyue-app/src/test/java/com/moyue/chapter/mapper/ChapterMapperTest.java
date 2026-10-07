package com.moyue.chapter.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChapterMapper 扩展方法测试（H2 真库）。
 * 验证 countByBookIdAndStatus / countByBookIdsAndStatus 按 status 过滤且只统计 is_deleted=0。
 */
@SpringBootTest
@ActiveProfiles("test")
class ChapterMapperTest {

    @Autowired
    private ChapterMapper chapterMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long B1 = 5000L;
    private static final long B2 = 6000L;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM chapter WHERE book_id IN (?,?)", B1, B2);
    }

    private void insertChapter(Long id, Long bookId, int chapterNo, int status, int isDeleted) {
        jdbcTemplate.update("INSERT INTO chapter (id,book_id,chapter_no,title,content,word_count,status,is_deleted) " +
                        "VALUES (?,?,?,?,?,?,?,?)",
                id, bookId, chapterNo, "章" + id, "正文" + id, 100, status, isDeleted);
    }

    @Test
    @DisplayName("countByBookIdAndStatus 按状态过滤且仅统计 is_deleted=0")
    void countByBookIdAndStatus_filtersStatusAndDeleted() {
        insertChapter(1L, B1, 1, 0, 0);
        insertChapter(2L, B1, 2, 0, 0);
        insertChapter(3L, B1, 3, 2, 0);
        insertChapter(4L, B1, 4, 0, 1); // 已删
        assertThat(chapterMapper.countByBookIdAndStatus(B1, 0)).isEqualTo(2);
        assertThat(chapterMapper.countByBookIdAndStatus(B1, 2)).isEqualTo(1);
    }

    @Test
    @DisplayName("countByBookIdsAndStatus 跨多书按状态过滤且仅统计 is_deleted=0")
    void countByBookIdsAndStatus_filtersStatusAndDeleted() {
        insertChapter(1L, B1, 1, 2, 0);
        insertChapter(2L, B2, 1, 2, 0);
        insertChapter(3L, B2, 2, 2, 1); // 已删
        insertChapter(4L, B2, 3, 3, 0); // 不同状态
        assertThat(chapterMapper.countByBookIdsAndStatus(Arrays.asList(B1, B2), 2)).isEqualTo(2);
    }
}
