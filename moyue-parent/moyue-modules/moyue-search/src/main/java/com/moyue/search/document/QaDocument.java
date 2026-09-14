package com.moyue.search.document;

import com.moyue.api.search.dto.QaIndexDTO;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * AI 客服问答搜索文档，映射 Elasticsearch 索引 moyue-qa。
 * 一轮对话（用户提问 + 助手回复）合并为一条文档，question / answer 均为 IK 分词 Text；
 * 以 messageId（助手回复消息 ID）为文档 _id，重复推送即覆盖更新（幂等）。
 *
 * <p>该索引含用户对话数据，仅供管理端客服知识检索（@RequiresPermissions 管控），读者端不开放。</p>
 */
@Data
@Document(indexName = "moyue-qa")
public class QaDocument {

    /** 助手回复消息 ID → ai_message.id（作为 ES 文档 _id，幂等覆盖） */
    @Id
    private Long messageId;

    /** 会话 ID → ai_session.id（keyword，按会话过滤 / 删除用） */
    @Field(type = FieldType.Keyword)
    private Long sessionId;

    /** 用户提问内容（IK 分词全文匹配） */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String question;

    /** 助手回复内容（IK 分词全文匹配） */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String answer;

    /** 回复时间（取助手消息 create_time，时间范围 filter 用） */
    @Field(type = FieldType.Date)
    private Date createTime;

    /**
     * 由跨服务索引载荷构建 ES 文档（messageId 作为 _id，幂等覆盖）。
     *
     * @param dto AI 域推送的问答索引 DTO；为 {@code null} 时返回 {@code null}
     * @return 可直接落索引的 {@link QaDocument}
     */
    public static QaDocument fromIndexDto(QaIndexDTO dto) {
        if (dto == null) {
            return null;
        }
        QaDocument doc = new QaDocument();
        doc.setMessageId(dto.getMessageId());
        doc.setSessionId(dto.getSessionId());
        doc.setQuestion(dto.getQuestion());
        doc.setAnswer(dto.getAnswer());
        doc.setCreateTime(toDate(dto.getCreateTime()));
        return doc;
    }

    /** LocalDateTime → java.util.Date（ES Date 字段类型要求），使用系统默认时区转换 */
    private static Date toDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
