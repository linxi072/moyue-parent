package com.moyue.content.service;

import com.moyue.content.domain.entity.ReadProgress;
import com.moyue.content.domain.vo.ReadProgressVO;

/**
 * 阅读进度服务（跨端回写，upsert 语义）。
 *
 * @author moyue
 */
public interface ReadProgressService {

    /** 回写进度：无则插入，有则更新（同一 user+book 一行） */
    void upsert(Long userId, Long bookId, int chapterNo, int position);

    /** 读取进度 */
    ReadProgressVO get(Long userId, Long bookId);
}
