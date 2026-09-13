package com.moyue.book.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.core.domain.PageResult;
import com.moyue.book.entity.BookEntity;
import com.moyue.book.entity.BookFinishApplyEntity;
import com.moyue.book.mapper.BookFinishApplyMapper;
import com.moyue.book.mapper.BookMapper;
import com.moyue.common.exception.BizException;
import com.moyue.common.core.domain.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 作品完结申请审核流（V11 book_finish_apply）。
 * <p>与 {@link BookService} 职责分离：本类只处理「申请 → 裁决」，
 * 通过时直接经本地 {@link BookMapper} 把 book.status 置 2（已完结，同库同模块，无需跨服务 Feign）。</p>
 * 状态语义：申请 0 待审核 / 1 通过 / 2 驳回；作品 1 连载中 / 2 已完结 / 3 已下架。
 */
@Service
public class BookFinishApplyService {

    /** 申请状态：待审核 */
    private static final int APPLY_PENDING = 0;
    /** 申请状态：通过 */
    private static final int APPLY_PASSED = 1;
    /** 申请状态：驳回 */
    private static final int APPLY_REJECTED = 2;

    /** 作品状态：连载中 */
    private static final int BOOK_SERIALIZING = 1;
    /** 作品状态：已完结 */
    private static final int BOOK_FINISHED = 2;

    @Autowired
    private BookFinishApplyMapper finishApplyMapper;

    @Autowired
    private BookMapper bookMapper;

    /**
     * 提交完结申请。
     * 校验：作品存在 → 本人作品 → 连载中 → 无在途申请。
     */
    @Transactional
    public BookFinishApplyEntity apply(Long bookId, Long userId, String reason) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (!Objects.equals(book.getAuthorId(), userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (book.getStatus() == null || book.getStatus() != BOOK_SERIALIZING) {
            throw new BizException(ResultCode.PARAM_ERROR, "仅连载中的作品可申请完结");
        }
        Long pending = finishApplyMapper.selectCount(new LambdaQueryWrapper<BookFinishApplyEntity>()
                .eq(BookFinishApplyEntity::getBookId, bookId)
                .eq(BookFinishApplyEntity::getStatus, APPLY_PENDING));
        if (pending != null && pending > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "该作品已有在途的完结申请");
        }

        BookFinishApplyEntity apply = new BookFinishApplyEntity();
        apply.setBookId(bookId);
        apply.setAuthorId(userId);
        apply.setReason(reason);
        apply.setStatus(APPLY_PENDING);
        apply.setIsDeleted(0);
        finishApplyMapper.insert(apply);
        return apply;
    }

    /** 查询某作品最新一条完结申请（可为 null，代表从未申请过） */
    public BookFinishApplyEntity latestByBook(Long bookId) {
        Page<BookFinishApplyEntity> p = new Page<>(1, 1);
        LambdaQueryWrapper<BookFinishApplyEntity> q = new LambdaQueryWrapper<BookFinishApplyEntity>()
                .eq(BookFinishApplyEntity::getBookId, bookId)
                .orderByDesc(BookFinishApplyEntity::getId);
        finishApplyMapper.selectPage(p, q);
        return p.getRecords().isEmpty() ? null : p.getRecords().get(0);
    }

    /** 后台分页查询完结申请；status 为 null 不过滤，按创建时间倒序 */
    public PageResult<BookFinishApplyEntity> pageApplies(Integer status, int page, int size) {
        Page<BookFinishApplyEntity> p = new Page<>(page, size);
        LambdaQueryWrapper<BookFinishApplyEntity> q = new LambdaQueryWrapper<BookFinishApplyEntity>();
        if (status != null) {
            q.eq(BookFinishApplyEntity::getStatus, status);
        }
        q.orderByDesc(BookFinishApplyEntity::getCreateTime).orderByDesc(BookFinishApplyEntity::getId);
        finishApplyMapper.selectPage(p, q);

        PageResult<BookFinishApplyEntity> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        result.setRecords(p.getRecords());
        return result;
    }

    /**
     * 管理员裁决完结申请。
     * 通过 → 作品状态置 2（已完结）；驳回 → 仅记录意见，作品维持连载。
     */
    @Transactional
    public BookFinishApplyEntity decide(Long applyId, boolean passed, String remark, Long auditorId) {
        BookFinishApplyEntity apply = finishApplyMapper.selectById(applyId);
        if (apply == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (apply.getStatus() == null || apply.getStatus() != APPLY_PENDING) {
            throw new BizException(ResultCode.PARAM_ERROR, "该申请已处理，请勿重复裁决");
        }
        if (passed) {
            BookEntity book = new BookEntity();
            book.setId(apply.getBookId());
            book.setStatus(BOOK_FINISHED);
            book.setUpdateTime(LocalDateTime.now());
            bookMapper.updateById(book);
        }
        BookFinishApplyEntity upd = new BookFinishApplyEntity();
        upd.setId(applyId);
        upd.setStatus(passed ? APPLY_PASSED : APPLY_REJECTED);
        upd.setAuditorId(auditorId);
        upd.setAuditRemark(remark);
        upd.setUpdateTime(LocalDateTime.now());
        finishApplyMapper.updateById(upd);
        return finishApplyMapper.selectById(applyId);
    }
}
