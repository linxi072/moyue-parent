package com.moyue.merch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.merch.entity.MerchCartEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 周边购物车 Mapper */
@Mapper
public interface MerchCartMapper extends BaseMapper<MerchCartEntity> {

    /**
     * 复活软删行并叠加数量：软删行仍占用 uk_user_product 唯一键，
     * 重新加入购物车时 insert 撞键，走本语句将 is_deleted 置 0 并累加数量。
     * 原生 @Update 绕开 MyBatis-Plus 自动追加的 is_deleted=0 过滤。
     */
    @Update("UPDATE merch_cart SET is_deleted = 0, quantity = quantity + #{quantity}, "
            + "update_time = NOW() WHERE user_id = #{userId} AND product_id = #{productId}")
    int revive(@Param("userId") Long userId, @Param("productId") Long productId, @Param("quantity") int quantity);
}
