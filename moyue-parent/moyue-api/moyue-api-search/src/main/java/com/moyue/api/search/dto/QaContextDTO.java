package com.moyue.api.search.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * AI 客服 RAG 召回结果 DTO（跨服务共享）：moyue-search 按问题检索 topK 问答片段，
 * 经 {@code QaSearchClient} 返回给 moyue-ai 注入大模型参考知识库。
 */
@Data
public class QaContextDTO implements Serializable {

    /** 召回片段（"Q: ...\nA: ..." 文本），可空 */
    private List<String> passages;
}
