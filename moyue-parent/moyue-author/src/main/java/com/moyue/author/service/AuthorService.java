package com.moyue.author.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyue.author.entity.AuthorIncomeEntity;
import com.moyue.author.mapper.AuthorIncomeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 作者稿酬业务：按作者查询稿酬流水。
 */
@Service
public class AuthorService {

    @Autowired
    private AuthorIncomeMapper authorIncomeMapper;

    /** 按 author_id 查询该作者的全部稿酬流水，按结算月份倒序 */
    public List<AuthorIncomeEntity> listByAuthor(Long authorId) {
        return authorIncomeMapper.selectList(Wrappers.<AuthorIncomeEntity>lambdaQuery()
                .eq(AuthorIncomeEntity::getAuthorId, authorId)
                .orderByDesc(AuthorIncomeEntity::getSettleMonth));
    }
}
