package com.moyue.commerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.commerce.domain.entity.PointsAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户积分账户数据访问。
 *
 * @author moyue
 */
@Mapper
public interface PointsAccountMapper extends BaseMapper<PointsAccount> {
}
