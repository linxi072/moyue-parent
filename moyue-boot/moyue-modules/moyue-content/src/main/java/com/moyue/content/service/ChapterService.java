package com.moyue.content.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.content.domain.dto.query.ChapterQuery;
import com.moyue.content.domain.entity.Chapter;
import com.moyue.content.domain.vo.ChapterVO;

import java.util.List;

/**
 * 章节域服务（C 端内容消费核心）。
 *
 * <p>生命周期：草稿(0) → 发布(1) / 定时发布(2)。
 * 序号由发布逻辑自动排定（取该作品 max+1），保证目录连续。
 *
 * @author moyue
 */
public interface ChapterService {

    /** 新建章节（默认草稿，chapterNo = max+1） */
    Long createChapter(Chapter entity);

    /** 编辑章节（标题/正文/状态），重算字数 */
    boolean updateChapter(Chapter entity);

    /** 发布：立即或定时（publishTime 在未来则进入定时发布） */
    ChapterVO publish(Long chapterId, java.time.LocalDateTime publishTime);

    /** 调整章节序号（与同作品目标序号章节互换，保持连续） */
    boolean reorder(Long chapterId, int targetNo);

    /** 逻辑删除 */
    boolean deleteChapter(Long chapterId);

    /** 下架（回到草稿态 status=0，对读者不可见） */
    boolean offshelf(Long chapterId);

    /** 章节目录（不含正文，按序号升序） */
    List<ChapterVO> catalog(Long bookId, Integer status);

    /** 章节分页（管理/检索用） */
    PageResult<ChapterVO> pageChapters(ChapterQuery query);

    /** 章节详情（含正文） */
    ChapterVO detail(Long chapterId);
}
