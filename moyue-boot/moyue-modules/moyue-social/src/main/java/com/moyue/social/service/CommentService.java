package com.moyue.social.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.social.domain.dto.query.CommentQuery;
import com.moyue.social.domain.vo.CommentVO;

/**
 * 评论服务（C 端互动）。
 *
 * @author moyue
 */
public interface CommentService {

    /** 评论分页（按 book_id 聚合） */
    PageResult<CommentVO> pageComments(CommentQuery query);

    /** 发表评论（评论人取身份上下文，防伪造） */
    Long createComment(Long userId, Long bookId, Long chapterId, Long replyTo, String content);

    /** 删除评论（仅本人） */
    void deleteComment(Long userId, Long commentId);

    /**
     * 点赞切换（唯一键防重，幂等）。
     *
     * @return 切换后的点赞数
     */
    int toggleLike(Long userId, Long commentId);

    // ============ 运营端（管理后台） ============

    /** 评论分页（运营全局视角，支持 status / top 过滤） */
    PageResult<CommentVO> pageAdminComments(CommentQuery query);

    /** 运营删除评论（逻辑删除，无视作者身份） */
    boolean adminDelete(Long commentId);

    /** 运营置顶 / 取消置顶 */
    boolean top(Long commentId, int top);

    /** 运营审核：status 0 正常 / 1 待审核 / 2 已下架 */
    boolean audit(Long commentId, Integer status);
}
