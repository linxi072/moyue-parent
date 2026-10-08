package com.moyue.commerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.commerce.domain.entity.PayOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 付费订单数据访问。
 *
 * @author moyue
 */
@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrder> {
}
