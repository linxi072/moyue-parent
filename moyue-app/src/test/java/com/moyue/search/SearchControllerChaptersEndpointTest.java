package com.moyue.search;

import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import com.moyue.search.controller.SearchController;
import com.moyue.search.dto.ChapterSearchResultDTO;
import com.moyue.search.service.ChapterSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SearchController GET /api/v1/search/chapters 回归测试（回归 S4）：
 * 章节检索端点存在且透传 ChapterSearchService.search（keyword/bookId/page/size），
 * 结果以 R.ok 包裹（含正文高亮片段的 ChapterSearchResultDTO）。
 */
class SearchControllerChaptersEndpointTest {

    private final ChapterSearchService chapterSearchService = mock(ChapterSearchService.class);

    private SearchController createController() {
        SearchController controller = new SearchController();
        ReflectionTestUtils.setField(controller, "chapterSearchService", chapterSearchService);
        // searchService / recommendService 与本端点无关，不注入
        return controller;
    }

    @Test
    @DisplayName("GET /search/chapters：keyword 必填 + bookId 可选，参数原样透传，R.ok 包裹")
    void searchChaptersShouldDelegateToService() {
        PageResult<ChapterSearchResultDTO> page = new PageResult<>();
        page.setTotal(1);
        page.setPage(2);
        page.setSize(10);
        ChapterSearchResultDTO dto = new ChapterSearchResultDTO();
        dto.setChapterId(1L);
        dto.setChapterTitle("第一章");
        dto.setHighlights(List.of("<em>韩立</em>"));
        page.setRecords(List.of(dto));
        when(chapterSearchService.search("韩立", 5L, 2, 10)).thenReturn(page);

        R<PageResult<ChapterSearchResultDTO>> resp =
                createController().searchChapters("韩立", 5L, 2, 10);

        verify(chapterSearchService).search("韩立", 5L, 2, 10);
        assertThat(resp.getCode()).isZero();
        assertThat(resp.getData()).isSameAs(page);
        assertThat(resp.getData().getRecords().get(0).getHighlights()).containsExactly("<em>韩立</em>");
    }

    @Test
    @DisplayName("GET /search/chapters：bookId 缺省为 null（跨作品全站章节检索）")
    void searchChaptersShouldPassNullBookIdWhenAbsent() {
        when(chapterSearchService.search("剑", null, 1, 20))
                .thenReturn(new PageResult<>());

        R<PageResult<ChapterSearchResultDTO>> resp =
                createController().searchChapters("剑", null, 1, 20);

        verify(chapterSearchService).search("剑", null, 1, 20);
        assertThat(resp.getCode()).isZero();
    }
}
