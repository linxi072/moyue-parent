package com.moyue.commerce.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.commerce.domain.dto.query.PointsAccountQuery;
import com.moyue.commerce.domain.dto.query.PointsLogQuery;
import com.moyue.commerce.domain.vo.PointsAccountVO;
import com.moyue.commerce.domain.vo.PointsLogVO;

import java.math.BigDecimal;

/**
 * 积分账户服务：余额原子增减（不足即拒）+ 账户 / 流水查询。
 *
 * @author moyue
 */
public interface PointsService {

    /**
     * 积分变动（核心入口）。
     *
     * @param userId  用户 ID
     * @param bizType 业务类型：1 充值 / 2 打赏 / 3 消费 / 4 退款
     * @param amount  变动积分（正数增加 / 负数扣减）
     * @param refId   关联业务单号
     * @return 变动后余额
     * @throws com.moyue.common.core.exception.BusinessException 余额不足
     */
    BigDecimal changePoints(Long userId, int bizType, BigDecimal amount, String refId);

    /**
     * 积分变动（带备注，供运营手动调整）。
     *
     * @return 变动后余额
     */
    BigDecimal changePoints(Long userId, int bizType, BigDecimal amount, String refId, String remark);

    /** 账户分页 */
    PageResult<PointsAccountVO> pageAccounts(PointsAccountQuery query);

    /** 流水分页 */
    PageResult<PointsLogVO> pageLogs(PointsLogQuery query);

    /** 查询单个用户账户（不存在返回 null） */
    PointsAccountVO getAccount(Long userId);
}
