package com.moyue.ai.engine;

import lombok.Data;

/**
 * 单轮对话（历史上下文用）：role 1 用户 / 2 助手。
 */
@Data
public class ChatTurn {

    /** 角色：1 用户提问 / 2 助手回复 */
    private int role;

    /** 消息内容 */
    private String content;

    public ChatTurn() {
    }

    public ChatTurn(int role, String content) {
        this.role = role;
        this.content = content;
    }
}
