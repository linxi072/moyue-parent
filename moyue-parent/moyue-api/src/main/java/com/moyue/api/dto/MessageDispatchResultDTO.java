package com.moyue.api.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 消息触达结果 DTO（跨服务共享）：一次分发在各渠道上的汇总结果。
 */
@Data
public class MessageDispatchResultDTO implements Serializable {

    /** 尝试的渠道总数 */
    private int total;

    /** 成功渠道数 */
    private int success;

    /** 失败渠道数 */
    private int failed;

    /** 逐渠道明细（如「INBOX=OK」「SMS=PENDING 未接入供应商」） */
    private List<String> channelDetails;
}
