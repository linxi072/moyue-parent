package com.moyue.chapter.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.content.client.BookClient;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.content.dto.ChapterDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.chapter.entity.ChapterEntity;
import com.moyue.chapter.mapper.ChapterMapper;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.cache.CacheNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 章节业务：章节详情、目录分页，以及作者写作链路（草稿箱、增删改、定时发布、排序）。
 * 归属校验通过 Feign BookClient 取 book.authorId；客户端不可用时降级为仅角色校验，不阻断创作。
 */
@Service
public class ChapterService {

    private static final int ROLE_AUTHOR = 2;
    private static final int ROLE_ADMIN = 3;

    @Autowired
    private ChapterMapper chapterMapper;

    @Autowired(required = false)
    private BookClient bookClient;

    /**
     * 按 ID 查询章节（含正文）。
     * 缓存名 {@link CacheNames#CHAPTER_CONTENT}（TTL 30 分钟），key = 章节 ID。
     * 写操作（编辑 / 删除 / 发布 / 排序 / 审核回写）会同时失效该 key 与所属作品的目录缓存。
     */
    @Cacheable(cacheNames = CacheNames.CHAPTER_CONTENT, key = "#id")
    public ChapterEntity getById(Long id) {
        return chapterMapper.selectById(id);
    }

    /**
     * 按作品 ID 查询目录（分页，按章节序号升序）。
     * 缓存名 {@link CacheNames#CHAPTER_CATALOG}（TTL 10 分钟），key = bookId:page:size。
     * 注意：本接口未过滤 status（草稿 / 驳回章节同样返回，与既有行为一致），缓存后最多有 10 分钟延迟。
     */
    @Cacheable(cacheNames = CacheNames.CHAPTER_CATALOG, key = "#bookId + ':' + #page + ':' + #size")
    public PageResult<ChapterEntity> listByBook(Long bookId, int page, int size) {
        Page<ChapterEntity> p = new Page<>(page, size);
        QueryWrapper<ChapterEntity> qw = new QueryWrapper<>();
        qw.eq("book_id", bookId);
        qw.orderByAsc("chapter_no");
        chapterMapper.selectPage(p, qw);

        PageResult<ChapterEntity> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        result.setRecords(p.getRecords());
        return result;
    }

    /** 草稿箱：按作品查 status=0 的章节（分页，序号升序） */
    public PageResult<ChapterEntity> listDrafts(Long bookId, int page, int size) {
        Page<ChapterEntity> p = new Page<>(page, size);
        QueryWrapper<ChapterEntity> qw = new QueryWrapper<>();
        qw.eq("book_id", bookId).eq("status", 0).orderByAsc("chapter_no");
        chapterMapper.selectPage(p, qw);

        PageResult<ChapterEntity> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        result.setRecords(p.getRecords());
        return result;
    }

    /** 创建章节：序号为空则取当前最大序号 +1；wordCount 由正文长度推导 */
    // 新增章节必然改变目录（可能翻页），目录 key 含 page/size 维度无法逐个推导，故整表失效
    @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    @Transactional
    public ChapterEntity createChapter(long userId, int role, Long bookId, String title, String content,
                                       Integer chapterNo, Integer status) {
        requireAuthor(role);
        if (bookId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "作品 ID 不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节标题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节正文不能为空");
        }
        checkBookOwner(bookId, userId, role);

        ChapterEntity e = new ChapterEntity();
        e.setBookId(bookId);
        e.setTitle(title.trim());
        e.setContent(content);
        e.setWordCount(content.length());
        e.setChapterNo(chapterNo != null ? chapterNo : nextChapterNo(bookId));
        e.setStatus(status != null ? status : 0);
        e.setIsDeleted(0);
        try {
            chapterMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            // uk_book_no(book_id, chapter_no) 冲突
            throw new BizException(ResultCode.PARAM_ERROR, "章节序号已存在");
        }
        return e;
    }

    /** 编辑章节：仅更新非空字段；正文变更时重算字数 */
    // 章节内容失效按 key；目录受序号/状态变化影响且 key 含 page/size，故整表失效
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CONTENT, key = "#chapterId"),
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    })
    @Transactional
    public ChapterEntity updateChapter(Long chapterId, long userId, int role, String title, String content,
                                       Integer chapterNo, Integer status, LocalDateTime publishTime) {
        ChapterEntity e = chapterMapper.selectById(chapterId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkBookOwner(e.getBookId(), userId, role);
        if (title != null) {
            e.setTitle(title);
        }
        if (content != null) {
            e.setContent(content);
            e.setWordCount(content.length());
        }
        if (chapterNo != null) {
            e.setChapterNo(chapterNo);
        }
        if (status != null) {
            e.setStatus(status);
        }
        if (publishTime != null) {
            e.setPublishTime(publishTime);
        }
        try {
            chapterMapper.updateById(e);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节序号已存在");
        }
        return e;
    }

    /** 删除章节（全局逻辑删除：update is_deleted=1） */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CONTENT, key = "#chapterId"),
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    })
    @Transactional
    public void deleteChapter(Long chapterId, long userId, int role) {
        ChapterEntity e = chapterMapper.selectById(chapterId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkBookOwner(e.getBookId(), userId, role);
        chapterMapper.deleteById(chapterId);
    }

    /** 发布 / 定时发布：到点时间未到则置 status=1（审核中，待调度器到点转 2） */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CONTENT, key = "#chapterId"),
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    })
    @Transactional
    public ChapterEntity publish(Long chapterId, long userId, int role, LocalDateTime publishTime) {
        ChapterEntity e = chapterMapper.selectById(chapterId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkBookOwner(e.getBookId(), userId, role);
        LocalDateTime now = LocalDateTime.now();
        if (publishTime == null || !publishTime.isAfter(now)) {
            e.setStatus(2);
            e.setPublishTime(now);
        } else {
            // TODO: 定时到点由 1→2 需调度器（当前无 XXL-Job，超出本次范围）
            e.setStatus(1);
            e.setPublishTime(publishTime);
        }
        chapterMapper.updateById(e);
        return e;
    }

    /** 章节排序：修改序号（uk_book_no 冲突转为参数错误） */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CONTENT, key = "#chapterId"),
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    })
    @Transactional
    public ChapterEntity reorder(Long chapterId, long userId, int role, Integer chapterNo) {
        ChapterEntity e = chapterMapper.selectById(chapterId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkBookOwner(e.getBookId(), userId, role);
        if (chapterNo == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节序号不能为空");
        }
        e.setChapterNo(chapterNo);
        try {
            chapterMapper.updateById(e);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节序号已存在");
        }
        return e;
    }

    /**
     * 审核回写（内部端点专用，不经网关）：status 2=已发布 / 3=已驳回。
     * 由 moyue-audit 审核裁决后经 Feign 调用；置为已发布且无发布时间时补当前时间。
     * 不做归属校验——调用方为受信的内部审核流程。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CONTENT, key = "#chapterId"),
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    })
    @Transactional
    public void auditChapter(Long chapterId, Integer status) {
        if (status == null || (status != 2 && status != 3)) {
            throw new BizException(ResultCode.PARAM_ERROR, "审核状态非法（仅支持 2 已发布 / 3 已驳回）");
        }
        ChapterEntity e = chapterMapper.selectById(chapterId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        e.setStatus(status);
        if (status == 2 && e.getPublishTime() == null) {
            e.setPublishTime(LocalDateTime.now());
        }
        chapterMapper.updateById(e);
    }

    // ------------------------------ 实体 → DTO 转换 ------------------------------

    /**
     * 单章转换（含正文）。
     * 16-11：Feign {@code ChapterClient#getChapter} 声明返回 {@code R<ChapterDTO>}，
     * 控制器必须回 DTO 而非实体，否则跨服务字段调整会静默漂移。
     */
    public static ChapterDTO toDto(ChapterEntity e) {
        if (e == null) {
            return null;
        }
        ChapterDTO d = new ChapterDTO();
        d.setId(e.getId());
        d.setBookId(e.getBookId());
        d.setChapterNo(e.getChapterNo());
        d.setTitle(e.getTitle());
        d.setWordCount(e.getWordCount());
        d.setContent(e.getContent());
        d.setStatus(e.getStatus());
        d.setPublishTime(e.getPublishTime());
        return d;
    }

    /** 列表转换（不含正文，避免目录接口把整本书正文打进响应体） */
    public static ChapterDTO toDtoLite(ChapterEntity e) {
        if (e == null) {
            return null;
        }
        ChapterDTO d = toDto(e);
        d.setContent(null);
        return d;
    }

    /** 分页转换（列表口径，不含正文） */
    public static PageResult<ChapterDTO> toDtoPage(PageResult<ChapterEntity> src) {
        PageResult<ChapterDTO> r = new PageResult<>();
        r.setTotal(src.getTotal());
        r.setPage(src.getPage());
        r.setSize(src.getSize());
        r.setRecords(src.getRecords() == null
                ? List.of()
                : src.getRecords().stream().map(ChapterService::toDtoLite).toList());
        return r;
    }

    // ------------------------------ 内部工具 ------------------------------

    /** 取下一位章节序号 = 当前最大序号 +1（全局逻辑删除会自动过滤已删章节） */
    private int nextChapterNo(Long bookId) {
        QueryWrapper<ChapterEntity> qw = new QueryWrapper<>();
        qw.eq("book_id", bookId).orderByDesc("chapter_no").last("LIMIT 1");
        ChapterEntity last = chapterMapper.selectOne(qw);
        return last == null || last.getChapterNo() == null ? 1 : last.getChapterNo() + 1;
    }

    /** 写操作需作者或管理员 */
    private void requireAuthor(int role) {
        if (role < ROLE_AUTHOR) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * 归属校验：管理员放行；否则经 BookClient 取书籍作者比对。
     * 书籍明确不存在（下游返回 RESOURCE_NOT_FOUND）时直接拒绝，避免给不存在的作品写章节；
     * BookClient 不可用（未注册 / 网络异常）时降级为仅角色校验，不阻断作者创作。
     */
    private void checkBookOwner(Long bookId, long userId, int role) {
        if (role == ROLE_ADMIN) {
            return;
        }
        if (bookClient == null) {
            return;
        }
        try {
            R<BookSummaryDTO> resp = bookClient.getBook(bookId);
            // 全局异常处理器以 HTTP 200 + R.code 承载业务错误，Feign 不抛异常，故须显式判码
            if (resp != null && resp.getCode() == ResultCode.RESOURCE_NOT_FOUND.getCode()) {
                throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "作品不存在或已下架");
            }
            BookSummaryDTO book = resp == null ? null : resp.getData();
            if (book != null && book.getAuthorId() != null && !Objects.equals(book.getAuthorId(), userId)) {
                throw new BizException(ResultCode.FORBIDDEN);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            // 书籍服务不可用：降级为仅角色校验
        }
    }
}
