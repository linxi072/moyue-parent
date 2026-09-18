package com.moyue.follow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注关系实体，映射 follow_relation 表（P2-E）。
 *
 * <p>isDeleted 显式 {@code @TableLogic(value="0", delval="1")}：项目全局逻辑删除未启用，
 * 不写会静默物理删。unfollow 走逻辑删（UPDATE is_deleted=1），重关注捕获唯一键冲突后 revive。</p>
 */
@Data
@TableName("follow_relation")
public class FollowEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 粉丝（关注发起方）ID → user.id */
    private Long fanId;

    /** 被关注作者 ID → user.id */
    private Long authorId;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
