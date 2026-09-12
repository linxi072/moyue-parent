package com.moyue.stat.service;

import com.moyue.stat.mapper.StatMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 统计服务：全站聚合统计。
 */
@Service
public class StatService {

    @Autowired
    private StatMapper statMapper;

    /**
     * 全站概览：用户数 / 书籍数 / 评论数。
     */
    public Map<String, Long> overview() {
        Map<String, Long> result = new HashMap<>(8);
        result.put("userCount", statMapper.countUser());
        result.put("bookCount", statMapper.countBook());
        result.put("commentCount", statMapper.countComment());
        return result;
    }
}
