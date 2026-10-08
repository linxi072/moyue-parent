package com.moyue.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.content.domain.entity.ReadProgress;
import com.moyue.content.domain.vo.ReadProgressVO;
import com.moyue.content.mapper.ReadProgressMapper;
import com.moyue.content.service.ReadProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 阅读进度实现（upsert）。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadProgressServiceImpl implements ReadProgressService {

    private final ReadProgressMapper progressMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(Long userId, Long bookId, int chapterNo, int position) {
        ReadProgress exist = progressMapper.selectOne(new LambdaQueryWrapper<ReadProgress>()
                .eq(ReadProgress::getUserId, userId)
                .eq(ReadProgress::getBookId, bookId));
        if (exist == null) {
            ReadProgress p = new ReadProgress();
            p.setUserId(userId);
            p.setBookId(bookId);
            p.setChapterNo(chapterNo);
            p.setPosition(position);
            progressMapper.insert(p);
            return;
        }
        exist.setChapterNo(chapterNo);
        exist.setPosition(position);
        progressMapper.updateById(exist);
    }

    @Override
    public ReadProgressVO get(Long userId, Long bookId) {
        ReadProgress exist = progressMapper.selectOne(new LambdaQueryWrapper<ReadProgress>()
                .eq(ReadProgress::getUserId, userId)
                .eq(ReadProgress::getBookId, bookId));
        if (exist == null) {
            return ReadProgressVO.builder().bookId(bookId).chapterNo(0).position(0).build();
        }
        return ReadProgressVO.builder()
                .bookId(exist.getBookId())
                .chapterNo(exist.getChapterNo() == null ? 0 : exist.getChapterNo())
                .position(exist.getPosition() == null ? 0 : exist.getPosition())
                .updateTime(exist.getUpdateTime() == null ? LocalDateTime.now() : exist.getUpdateTime())
                .build();
    }
}
