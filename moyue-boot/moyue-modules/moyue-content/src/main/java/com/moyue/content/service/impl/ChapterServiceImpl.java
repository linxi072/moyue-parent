package com.moyue.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.content.domain.dto.query.ChapterQuery;
import com.moyue.content.domain.entity.Chapter;
import com.moyue.content.domain.vo.ChapterVO;
import com.moyue.content.mapper.ChapterMapper;
import com.moyue.content.mapper.BookMapper;
import com.moyue.content.service.ChapterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 章节域实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterServiceImpl implements ChapterService {

    private final ChapterMapper chapterMapper;
    private final BookMapper bookMapper;

    /** 草稿 */
    private static final int STATUS_DRAFT = 0;
    /** 已发布 */
    private static final int STATUS_PUBLISHED = 1;
    /** 定时发布 */
    private static final int STATUS_SCHEDULED = 2;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createChapter(Chapter entity) {
        if (entity.getBookId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "章节必须归属作品");
        }
        if (!StringUtils.hasText(entity.getTitle())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "章节标题不能为空");
        }
        if (bookMapper.selectById(entity.getBookId()) == null) {
            throw BusinessException.notFound("作品");
        }
        // 序号自动取该作品 max+1，保证目录连续
        Integer maxNo = chapterMapper.selectMaxNo(entity.getBookId());
        entity.setChapterNo(maxNo == null ? 1 : maxNo + 1);
        entity.setStatus(STATUS_DRAFT);
        entity.setWordCount(countWords(entity.getContent()));
        chapterMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateChapter(Chapter entity) {
        Chapter exist = chapterMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("章节");
        }
        if (StringUtils.hasText(entity.getTitle())) {
            exist.setTitle(entity.getTitle());
        }
        if (entity.getContent() != null) {
            exist.setContent(entity.getContent());
            exist.setWordCount(countWords(entity.getContent()));
        }
        if (entity.getStatus() != null && entity.getStatus() != STATUS_PUBLISHED) {
            exist.setStatus(entity.getStatus());
        }
        return chapterMapper.updateById(exist) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterVO publish(Long chapterId, LocalDateTime publishTime) {
        Chapter exist = chapterMapper.selectById(chapterId);
        if (exist == null) {
            throw BusinessException.notFound("章节");
        }
        LocalDateTime now = LocalDateTime.now();
        if (publishTime != null && publishTime.isAfter(now)) {
            exist.setStatus(STATUS_SCHEDULED);
            exist.setPublishTime(publishTime);
        } else {
            exist.setStatus(STATUS_PUBLISHED);
            exist.setPublishTime(now);
        }
        chapterMapper.updateById(exist);
        return toVO(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reorder(Long chapterId, int targetNo) {
        if (targetNo < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "章节序号最小为 1");
        }
        Chapter src = chapterMapper.selectById(chapterId);
        if (src == null) {
            throw BusinessException.notFound("章节");
        }
        Chapter dst = chapterMapper.selectByNo(src.getBookId(), targetNo);
        int srcNo = src.getChapterNo();
        if (dst == null || dst.getId().equals(src.getId())) {
            // 目标位置空洞或自身：直接落到目标序号
            src.setChapterNo(targetNo);
            chapterMapper.updateById(src);
            return true;
        }
        // 互换序号，保持目录连续无冲突
        dst.setChapterNo(srcNo);
        src.setChapterNo(targetNo);
        chapterMapper.updateById(dst);
        chapterMapper.updateById(src);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteChapter(Long chapterId) {
        if (chapterMapper.selectById(chapterId) == null) {
            throw BusinessException.notFound("章节");
        }
        return chapterMapper.deleteById(chapterId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean offshelf(Long chapterId) {
        Chapter exist = chapterMapper.selectById(chapterId);
        if (exist == null) {
            throw BusinessException.notFound("章节");
        }
        Chapter upd = new Chapter();
        upd.setId(chapterId);
        upd.setStatus(STATUS_DRAFT);
        return chapterMapper.updateById(upd) > 0;
    }

    @Override
    public List<ChapterVO> catalog(Long bookId, Integer status) {
        var list = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getBookId, bookId)
                .eq(status != null, Chapter::getStatus, status)
                .orderByAsc(Chapter::getChapterNo));
        return list.stream().map(this::toCatalogVO).toList();
    }

    @Override
    public PageResult<ChapterVO> pageChapters(ChapterQuery query) {
        var page = PageUtils.<Chapter>page(query);
        var result = chapterMapper.selectPage(page, new LambdaQueryWrapper<Chapter>()
                .eq(query.getBookId() != null, Chapter::getBookId, query.getBookId())
                .eq(query.getStatus() != null, Chapter::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getTitle()), Chapter::getTitle, query.getTitle())
                .orderByDesc(Chapter::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    public ChapterVO detail(Long chapterId) {
        Chapter exist = chapterMapper.selectById(chapterId);
        if (exist == null) {
            throw BusinessException.notFound("章节");
        }
        return toVO(exist);
    }

    private int countWords(String content) {
        if (content == null) {
            return 0;
        }
        // 去除空白后按字符计数（中文按字、英文按词近似），足够榜单/统计使用
        return content.replaceAll("\\s+", "").length();
    }

    private ChapterVO toVO(Chapter e) {
        return ChapterVO.builder()
                .id(e.getId()).bookId(e.getBookId()).chapterNo(e.getChapterNo())
                .title(e.getTitle()).content(e.getContent()).wordCount(e.getWordCount())
                .status(e.getStatus()).publishTime(e.getPublishTime())
                .createTime(e.getCreateTime()).updateTime(e.getUpdateTime())
                .build();
    }

    private ChapterVO toCatalogVO(Chapter e) {
        // 目录不返回正文，节省带宽
        return ChapterVO.builder()
                .id(e.getId()).bookId(e.getBookId()).chapterNo(e.getChapterNo())
                .title(e.getTitle()).wordCount(e.getWordCount()).status(e.getStatus())
                .publishTime(e.getPublishTime()).createTime(e.getCreateTime())
                .build();
    }
}
