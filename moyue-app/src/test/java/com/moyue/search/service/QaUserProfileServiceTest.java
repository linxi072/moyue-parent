package com.moyue.search.service;

import com.moyue.api.content.client.BookClient;
import com.moyue.api.content.client.BookshelfClient;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.content.dto.BookshelfSummaryDTO;
import com.moyue.common.R;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * UserProfileService 纯 Mockito 单测（项目禁用 Docker，不启 Spring）。
 * 覆盖：null 用户 / 客户端缺失 / 降级响应 / 正常画像聚合。
 */
@ExtendWith(MockitoExtension.class)
class QaUserProfileServiceTest {

    @Mock
    private BookshelfClient bookshelfClient;

    @Mock
    private BookClient bookClient;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    void userId_null_returnsEmpty() {
        assertTrue(userProfileService.buildProfile(null).isEmpty());
    }

    @Test
    void shelfDegraded_returnsEmpty() {
        when(bookshelfClient.getBookshelf(1L)).thenReturn(R.fail(40002, "degraded"));
        assertTrue(userProfileService.buildProfile(1L).isEmpty());
    }

    @Test
    void shelfEmpty_returnsEmpty() {
        when(bookshelfClient.getBookshelf(1L)).thenReturn(R.ok(List.of()));
        assertTrue(userProfileService.buildProfile(1L).isEmpty());
    }

    @Test
    void normal_aggregatesCategoryAndAuthorWeights() {
        BookshelfSummaryDTO s1 = new BookshelfSummaryDTO(); s1.setBookId(101L);
        BookshelfSummaryDTO s2 = new BookshelfSummaryDTO(); s2.setBookId(102L);
        when(bookshelfClient.getBookshelf(7L)).thenReturn(R.ok(List.of(s1, s2)));

        BookSummaryDTO b1 = new BookSummaryDTO(); b1.setCategory("玄幻"); b1.setAuthor("张三");
        BookSummaryDTO b2 = new BookSummaryDTO(); b2.setCategory("玄幻"); b2.setAuthor("李四");
        when(bookClient.getBook(101L)).thenReturn(R.ok(b1));
        when(bookClient.getBook(102L)).thenReturn(R.ok(b2));

        UserInterestProfile profile = userProfileService.buildProfile(7L);
        assertFalse(profile.isEmpty());
        assertEquals(Map.of("玄幻", 2), profile.getCategoryWeights());
        assertEquals(Map.of("张三", 1, "李四", 1), profile.getAuthorWeights());
        assertTrue(profile.getShelfBookIds().contains(101L));
        assertTrue(profile.getShelfBookIds().contains(102L));
    }

    @Test
    void bookClientMissing_enrichmentSkippedButShelfKept() {
        BookshelfSummaryDTO s1 = new BookshelfSummaryDTO(); s1.setBookId(101L);
        when(bookshelfClient.getBookshelf(7L)).thenReturn(R.ok(List.of(s1)));
        // 模拟 @Autowired(required=false) 未注入 bookClient：逐本强化被跳过，但书架仍用于去重
        ReflectionTestUtils.setField(userProfileService, "bookClient", null);

        UserInterestProfile profile = userProfileService.buildProfile(7L);
        assertTrue(profile.getShelfBookIds().contains(101L));
        assertTrue(profile.getCategoryWeights().isEmpty());
        assertTrue(profile.getAuthorWeights().isEmpty());
    }
}
