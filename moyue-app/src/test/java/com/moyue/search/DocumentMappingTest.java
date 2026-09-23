package com.moyue.search;

import com.moyue.api.search.dto.ChapterIndexDTO;
import com.moyue.api.search.dto.QaIndexDTO;
import com.moyue.search.document.ChapterDocument;
import com.moyue.search.document.QaDocument;
import com.moyue.search.dto.ChapterSearchResultDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.SearchHit;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 文档 / DTO 注解与映射走查（逻辑单测）：
 * ChapterDocument / QaDocument 的 @Document 索引名、@Id 主键、fromIndexDto 映射
 * （null 载荷、status 缺省回填已发布、时间转换）与 ChapterSearchResultDTO 高亮映射。
 */
class DocumentMappingTest {

    @Test
    @DisplayName("ChapterDocument：@Document 索引名 moyue-chapter，@Id 字段为 chapterId")
    void chapterDocumentShouldBindMoyueChapterIndex() {
        org.springframework.data.elasticsearch.annotations.Document doc =
                ChapterDocument.class.getAnnotation(
                        org.springframework.data.elasticsearch.annotations.Document.class);
        assertThat(doc).isNotNull();
        assertThat(doc.indexName()).isEqualTo("moyue-chapter");

        assertThat(new ChapterDocument().getChapterId()).isNull(); // @Id 字段存在性（getter 校验）
    }

    @Test
    @DisplayName("QaDocument：@Document 索引名 moyue-qa，@Id 字段为 messageId")
    void qaDocumentShouldBindMoyueQaIndex() {
        org.springframework.data.elasticsearch.annotations.Document doc =
                QaDocument.class.getAnnotation(
                        org.springframework.data.elasticsearch.annotations.Document.class);
        assertThat(doc).isNotNull();
        assertThat(doc.indexName()).isEqualTo("moyue-qa");

        assertThat(new QaDocument().getMessageId()).isNull();
    }

    @Test
    @DisplayName("ChapterDocument.fromIndexDto：全字段映射，publishTime LocalDateTime→Date")
    void chapterFromIndexDtoShouldMapAllFields() {
        ChapterIndexDTO dto = new ChapterIndexDTO();
        dto.setChapterId(1L);
        dto.setBookId(2L);
        dto.setBookTitle("凡人修仙传");
        dto.setChapterTitle("第一章");
        dto.setContent("正文内容");
        dto.setStatus(ChapterIndexDTO.STATUS_PUBLISHED);
        dto.setPublishTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        ChapterDocument doc = ChapterDocument.fromIndexDto(dto);

        assertThat(doc.getChapterId()).isEqualTo(1L);
        assertThat(doc.getBookId()).isEqualTo(2L);
        assertThat(doc.getBookTitle()).isEqualTo("凡人修仙传");
        assertThat(doc.getChapterTitle()).isEqualTo("第一章");
        assertThat(doc.getContent()).isEqualTo("正文内容");
        assertThat(doc.getStatus()).isEqualTo(2);
        assertThat(doc.getPublishTime()).isEqualTo(
                Date.from(dto.getPublishTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
    }

    @Test
    @DisplayName("ChapterDocument.fromIndexDto：status 缺省回填已发布（filter 兜底）")
    void chapterFromIndexDtoShouldDefaultStatusToPublished() {
        ChapterIndexDTO dto = new ChapterIndexDTO();
        dto.setChapterId(1L);
        dto.setStatus(null);

        ChapterDocument doc = ChapterDocument.fromIndexDto(dto);
        assertThat(doc.getStatus()).isEqualTo(ChapterIndexDTO.STATUS_PUBLISHED);
    }

    @Test
    @DisplayName("ChapterDocument.fromIndexDto：null 载荷 → null（上层跳过写入）")
    void chapterFromIndexDtoShouldReturnNullOnNullDto() {
        assertThat(ChapterDocument.fromIndexDto(null)).isNull();
    }

    @Test
    @DisplayName("ChapterDocument.fromIndexDto：publishTime 缺省 → publishTime 为 null")
    void chapterFromIndexDtoShouldHandleNullPublishTime() {
        ChapterIndexDTO dto = new ChapterIndexDTO();
        dto.setChapterId(1L);

        ChapterDocument doc = ChapterDocument.fromIndexDto(dto);
        assertThat(doc.getPublishTime()).isNull();
    }

    @Test
    @DisplayName("QaDocument.fromIndexDto：全字段映射（messageId/question/answer/createTime）")
    void qaFromIndexDtoShouldMapAllFields() {
        QaIndexDTO dto = new QaIndexDTO();
        dto.setMessageId(11L);
        dto.setSessionId(22L);
        dto.setQuestion("怎么退款");
        dto.setAnswer("在订单页操作");
        dto.setCreateTime(LocalDateTime.of(2026, 2, 3, 10, 30));

        QaDocument doc = QaDocument.fromIndexDto(dto);

        assertThat(doc.getMessageId()).isEqualTo(11L);
        assertThat(doc.getSessionId()).isEqualTo(22L);
        assertThat(doc.getQuestion()).isEqualTo("怎么退款");
        assertThat(doc.getAnswer()).isEqualTo("在订单页操作");
        assertThat(doc.getCreateTime()).isEqualTo(
                Date.from(dto.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
    }

    @Test
    @DisplayName("QaDocument.fromIndexDto：null 载荷 → null")
    void qaFromIndexDtoShouldReturnNullOnNullDto() {
        assertThat(QaDocument.fromIndexDto(null)).isNull();
    }

    @Test
    @DisplayName("ChapterSearchResultDTO.from：元信息 + content 高亮片段映射")
    void resultDtoShouldMapHitAndHighlights() {
        ChapterDocument doc = new ChapterDocument();
        doc.setChapterId(1L);
        doc.setBookId(2L);
        doc.setBookTitle("凡人修仙传");
        doc.setChapterTitle("第一章");
        doc.setStatus(2);
        doc.setPublishTime(new Date(1700000000000L));

        @SuppressWarnings("unchecked")
        SearchHit<ChapterDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        when(hit.getHighlightField("content")).thenReturn(List.of("<em>韩立</em>"));

        ChapterSearchResultDTO result = ChapterSearchResultDTO.from(hit);

        assertThat(result.getChapterId()).isEqualTo(1L);
        assertThat(result.getBookId()).isEqualTo(2L);
        assertThat(result.getBookTitle()).isEqualTo("凡人修仙传");
        assertThat(result.getChapterTitle()).isEqualTo("第一章");
        assertThat(result.getStatus()).isEqualTo(2);
        assertThat(result.getHighlights()).containsExactly("<em>韩立</em>");
    }
}
