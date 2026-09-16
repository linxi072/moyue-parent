package com.moyue.ai.engine;

import lombok.Data;

/**
 * 回复引擎输出。
 * <ul>
 *   <li>{@code content} 为 null 表示本引擎无法回答，调用方（AiService）应级联到下一引擎；</li>
 *   <li>{@code confident=false} 表示引擎把握不足，调用方应追加「转人工」提示。</li>
 * </ul>
 */
@Data
public class ReplyResult {

    /** 回复正文；null 表示本引擎无法回答 */
    private String content;

    /** 是否有把握；false 时建议转人工 */
    private boolean confident;

    /** 引擎标识（便于前端展示与日志排查） */
    private String engineName;

    public ReplyResult() {
    }

    public ReplyResult(String content, boolean confident, String engineName) {
        this.content = content;
        this.confident = confident;
        this.engineName = engineName;
    }
}
