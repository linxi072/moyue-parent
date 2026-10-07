package com.moyue.read.mapper;

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
 * BookshelfMapper 扩展方法测试（H2 真库）。
 * 验证 countByBookId / countByBookIds 仅统计 is_deleted=0（自定义 @Select 不触发 @TableLogic）。
 */
@SpringBootTest
@ActiveProfiles("test")
class BookshelfMapperTest {

    @Autowired
    private BookshelfMapper bookshelfMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long B1 = 5000L;
    private static final long B2 = 6000L;
    private static final long B3 = 9999L;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM bookshelf WHERE book_id IN (?,?,?)", B1, B2, B3);
    }

    @Test
    @DisplayName("countByBookId 仅统计 is_deleted=0")
    void countByBookId_countsOnlyNotDeleted() {
        jdbcTemplate.update("INSERT INTO bookshelf (id,user_id,book_id,is_deleted) VALUES (90001,100,?,0)", B1);
        jdbcTemplate.update("INSERT INTO bookshelf (id,user_id,book_id,is_deleted) VALUES (90002,101,?,0)", B1);
        jdbcTemplate.update("INSERT INTO bookshelf (id,user_id,book_id,is_deleted) VALUES (90003,102,?,1)", B1);
        jdbcTemplate.update("INSERT INTO bookshelf (id,user_id,book_id,is_deleted) VALUES (90004,103,?,0)", B3);

        assertThat(bookshelfMapper.countByBookId(B1)).isEqualTo(2);
    }

    @Test
    @DisplayName("countByBookIds 仅统计 is_deleted=0")
    void countByBookIds_countsOnlyNotDeleted() {
        jdbcTemplate.update("INSERT INTO bookshelf (id,user_id,book_id,is_deleted) VALUES (90001,100,?,0)", B1);
        jdbcTemplate.update("INSERT INTO bookshelf (id,user_id,book_id,is_deleted) VALUES (90002,101,?,0)", B1);
        jdbcTemplate.update("INSERT INTO bookshelf (id,user_id,book_id,is_deleted) VALUES (90003,102,?,0)", B2);
        jdbcTemplate.update("INSERT INTO bookshelf (id,user_id,book_id,is_deleted) VALUES (90004,103,?,1)", B2);

        // B1 贡献 2 条未删（id 90001/90002），B2 贡献 1 条未删（id 90003，id 90004 已删）
        assertThat(bookshelfMapper.countByBookIds(Arrays.asList(B1, B2))).isEqualTo(3);
    }
}
