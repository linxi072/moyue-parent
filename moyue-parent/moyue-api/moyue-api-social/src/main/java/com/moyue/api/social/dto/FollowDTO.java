package com.moyue.api.social.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 关注关系数据传输对象（跨服务 / 接口共享）。
 */
@Data
public class FollowDTO implements Serializable {

    /** 关注关系主键 */
    private Long id;

    /** 粉丝（关注发起方）ID → user.id */
    private Long fanId;

    /** 被关注作者 ID → user.id */
    private Long authorId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
