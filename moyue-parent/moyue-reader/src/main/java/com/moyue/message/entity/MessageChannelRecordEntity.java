package com.moyue.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息触达记录实体，映射 message_channel_record 表（V13）。
 *
 * <p>每次分发对每个渠道写一行；{@code status}：0 待发（含桩实现 pending）/ 1 成功 / 2 失败。</p>
 */
@Data
@TableName("message_channel_record")
public class MessageChannelRecordEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 接收用户 ID → user.id */
    private Long userId;

    /** 渠道：1 站内信 / 2 邮件 / 3 短信 / 4 推送 */
    private Integer channel;

    /** 模板编码 */
    private String templateCode;

    /** 投递地址（邮箱 / 手机号） */
    private String target;

    /** 渲染后的标题 */
    private String title;

    /** 渲染后的内容 */
    private String content;

    /** 业务类型，如 AUDIT / REPORT */
    private String bizType;

    /** 业务主键 */
    private Long bizId;

    /** 状态：0 待发 / 1 成功 / 2 失败 */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    /** 重试次数 */
    private Integer retryCount;

    /** 发送时间 */
    private LocalDateTime sendTime;

    /** 逻辑删除标记：0 未删除 / 1 已删除 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
