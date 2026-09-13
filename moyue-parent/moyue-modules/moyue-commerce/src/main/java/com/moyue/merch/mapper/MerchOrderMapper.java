package com.moyue.merch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.merch.entity.MerchOrderEntity;
import org.apache.ibatis.annotations.Mapper;

/** 周边订单 Mapper */
@Mapper
public interface MerchOrderMapper extends BaseMapper<MerchOrderEntity> {
}
