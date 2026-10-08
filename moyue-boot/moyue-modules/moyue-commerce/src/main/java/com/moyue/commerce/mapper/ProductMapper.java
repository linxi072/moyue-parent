package com.moyue.commerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.commerce.domain.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 积分兑换商品数据访问。
 *
 * @author moyue
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
