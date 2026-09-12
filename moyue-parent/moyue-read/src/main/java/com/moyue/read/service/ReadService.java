package com.moyue.read.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.moyue.read.entity.BookshelfEntity;
import com.moyue.read.mapper.BookshelfMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 阅读业务：书架查询。
 */
@Service
public class ReadService {

    @Autowired
    private BookshelfMapper bookshelfMapper;

    /** 按用户 ID 查询书架（按加入时间倒序） */
    public List<BookshelfEntity> getShelf(Long userId) {
        QueryWrapper<BookshelfEntity> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        qw.orderByDesc("create_time");
        return bookshelfMapper.selectList(qw);
    }
}
