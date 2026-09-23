package com.moyue.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.member.entity.MemberSubscriptionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 会员订阅记录 Mapper */
@Mapper
public interface MemberSubscriptionMapper extends BaseMapper<MemberSubscriptionEntity> {
}
