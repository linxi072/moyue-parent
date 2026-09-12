package com.moyue.chapter.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.dto.PageResult;
import com.moyue.chapter.entity.ChapterEntity;
import com.moyue.chapter.mapper.ChapterMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 章节业务：章节详情与目录分页。
 */
@Service
public class ChapterService {

    @Autowired
    private ChapterMapper chapterMapper;

    /** 按 ID 查询章节 */
    public ChapterEntity getById(Long id) {
        return chapterMapper.selectById(id);
    }

    /** 按作品 ID 查询目录（分页，按章节序号升序） */
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
}
