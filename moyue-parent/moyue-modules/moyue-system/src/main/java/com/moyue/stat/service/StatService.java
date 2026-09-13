package com.moyue.stat.service;

import com.moyue.common.core.domain.PageResult;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.stat.mapper.StatMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务：全站聚合统计 + 作者维度下钻 + 留存漏斗 + 明细导出。
 *
 * <p>所有 SQL 直接写在 {@link StatMapper} 上（注解方式，与既有代码保持一致），
 * 只读跨表聚合，不引入任何实体与物理表。</p>
 */
@Service
public class StatService {

    /** 单次 CSV 导出的最大行数，超出部分截断 */
    private static final int EXPORT_MAX_ROWS = 10000;

    /** 作者维度分页的默认页大小上限，防止 offset 越界与整型溢出 */
    private static final int MAX_PAGE_SIZE = 200;

    /** UTF-8 BOM，Excel 打开中文 CSV 不乱码的必要字节序标记 */
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Autowired
    private StatMapper statMapper;

    // ------------------------------------------------------------------
    // 0) 全站概览
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // 1) 作者维度统计
    // ------------------------------------------------------------------

    /**
     * 作者维度分页统计。
     *
     * <p><b>统计口径</b>（口径是最容易被后人误解的地方，此处逐条固化）：</p>
     * <ol>
     *   <li><b>候选作者</b>：user.role = 2（V1 字典：1 读者 / 2 作者 / 3 管理员）
     *       <b>或</b>在 book 表中至少拥有一本未逻辑删除作品 —— 两者取并集，
     *       避免「有作品但角色未同步为作者」的账户被漏掉。</li>
     *   <li><b>作品数 bookCount</b>：book.is_deleted = 0 的作品行数。</li>
     *   <li><b>总字数 totalWordCount</b>：SUM(book.word_count)，V1 该列为累计字数 INT。</li>
     *   <li><b>总点击 totalClickCount</b>：SUM(book.click_count)，V1 该列为累计点击 BIGINT。</li>
     *   <li><b>打赏收入 rewardAmount</b>：SUM(reward_order.amount)，仅计 status = 1（已支付）
     *       且未逻辑删除的订单，经由 reward_order.book_id → book.author_id 归属作者。
     *       <b>单位为「元」</b>：V1 定义 amount DECIMAL(10,2) 金额（元），本表已是元，未做分⇄元换算。</li>
     *   <li><b>上架章节数 publishedChapterCount</b>：chapter.status = 2（已发布，V1 字典
     *       0 草稿 / 1 审核中 / 2 已发布 / 3 已驳回）且未逻辑删除的章节数，
     *       经 chapter.book_id → book.author_id 归属作者。注意它与「付费章节」无关。</li>
     *   <li><b>author_income 未被使用</b>：该表是「结算过的稿酬流水」，且与 reward_order
     *       口径重叠（并有的行无 author 归属），为避免与打赏收入双算，本接口不取该表。</li>
     * </ol>
     *
     * @param keyword 昵称模糊匹配关键字，空字符串视为不过滤
     * @param page    当前页，从 1 开始
     * @param size    每页大小，超过 {@link #MAX_PAGE_SIZE} 时收敛到上限
     * @return 分页结果，records 中每行见 Mapper 上的列别名说明
     */
    public PageResult<Map<String, Object>> authorStats(String keyword, Integer page, Integer size) {
        int pageNo = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 20 : Math.min(size, MAX_PAGE_SIZE);
        String kw = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();

        long total = statMapper.countAuthorStats(kw);
        long offset = (long) (pageNo - 1) * pageSize;
        List<Map<String, Object>> records = new ArrayList<>();
        if (total > 0 && offset < total) {
            records = statMapper.selectAuthorStats(kw, offset, pageSize);
        }

        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setTotal(total);
        result.setPage(pageNo);
        result.setSize(pageSize);
        result.setRecords(records);
        return result;
    }

    // ------------------------------------------------------------------
    // 2) 留存漏斗
    // ------------------------------------------------------------------

    /**
     * 留存漏斗。
     *
     * <p><b>层级口径</b>：</p>
     * <ol>
     *   <li><b>registered</b>：user.is_deleted = 0 的用户总数。</li>
     *   <li><b>activated</b>：bookshelf.is_deleted = 0 的 DISTINCT user_id —— 全库没有
     *       read_record 类阅读流水表，此处以「加入过书架（含最后阅读章节）」作为阅读行为代理。</li>
     *   <li><b>paying</b>：reward_order.status = 1（已支付）与 points_order.status = 1（已兑换）
     *       两个来源 user_id 的 UNION 去重计数。积分兑换属于「消费」，故一并计入。</li>
     *   <li><b>active7d</b>：近 7 日有行为的用户 —— bookshelf.update_time、
     *       comment.create_time、reward_order.pay_time 三个来源 UNION 去重。
     *       <b>口径受限</b>：user 表无登录时间列（已核对 V1~V10 无 last_login/login_time），
     *       故无法精确定义「登录」活跃，只能以业务行为时间近似，且 active7d 不是
     *       registered→activated→paying 的严格子集。</li>
     *   <li><b>派生比率</b>：activationRate / payingRate / active7dRate 为 Java 侧计算的
     *       <b>整数百分比（0~100，向下取整）</b>，在此换算以规避 SQL 除零。</li>
     * </ol>
     *
     * @return key → 数值，另含 createTime（本次统计生成时刻，毫秒时间戳）
     */
    public Map<String, Long> retention() {
        Map<String, Long> result = new LinkedHashMap<>(12);
        long registered = statMapper.countUser();
        long activated = statMapper.countActivatedUser();
        long paying = statMapper.countPayingUser();
        long active7d = statMapper.countActiveUser7d();

        result.put("registered", registered);
        result.put("activated", activated);
        result.put("paying", paying);
        result.put("active7d", active7d);
        result.put("activationRate", percent(activated, registered));
        result.put("payingRate", percent(paying, activated));
        result.put("active7dRate", percent(active7d, registered));
        result.put("createTime", System.currentTimeMillis());
        return result;
    }

    // ------------------------------------------------------------------
    // 3) 明细导出（CSV）
    // ------------------------------------------------------------------

    /**
     * 导出明细 CSV（UTF-8 带 BOM）。
     *
     * <p><b>为什么不用 JSON / xlsx</b>：CSV 是纯文本表格交换格式，浏览器可直接另存、Excel 可直接打开，
     * 不需要引入额外依赖。代价有两点，均已在本方法处理：
     * ① 单元格含中文，必须写 UTF-8 BOM，否则 Excel 按 ANSI 解码导致乱码；
     * ② {@code = + - @} 等前缀会被表格软件当作公式执行，必须做注入转义（见 {@link #csvCell}）。</p>
     *
     * <p><b>数据量保护</b>：单份导出最多 10000 行（authors 由 LIMIT 截断，
     * retention 本身只有个位数行）。</p>
     *
     * @param type 导出类型：authors（作者维度） / retention（留存漏斗）
     * @return UTF-8 编码（含 BOM）的 CSV 字节
     */
    public byte[] exportCsv(String type) {
        StringBuilder body = new StringBuilder(1024);
        if ("authors".equalsIgnoreCase(type)) {
            appendRow(body, "作者ID", "昵称", "作品数", "总字数", "总点击", "打赏收入(元)", "上架章节数");
            List<Map<String, Object>> rows = statMapper.selectAuthorStats(null, 0L, EXPORT_MAX_ROWS);
            for (Map<String, Object> row : rows) {
                appendRow(body, str(row.get("authorId")), str(row.get("nickname")),
                        str(row.get("bookCount")), str(row.get("totalWordCount")),
                        str(row.get("totalClickCount")), str(row.get("rewardAmount")),
                        str(row.get("publishedChapterCount")));
            }
        } else if ("retention".equalsIgnoreCase(type)) {
            appendRow(body, "指标", "数值");
            Map<String, Long> funnel = retention();
            for (Map.Entry<String, Long> entry : funnel.entrySet()) {
                appendRow(body, entry.getKey(), String.valueOf(entry.getValue()));
            }
        } else {
            throw new BizException(ResultCode.PARAM_ERROR, "导出类型仅支持 authors 或 retention");
        }
        // 统一 CRLF：最后一次 replace 会把行尾 \n 变成 \r\n（Excel 通用）
        return concatBom(body.toString().replace("\n", "\r\n"));
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /**
     * 计算百分比（0~100，向下取整）。分母为 0 时返回 0，规避除零。
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 整数百分比
     */
    private static long percent(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0L;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.FLOOR)
                .longValue();
    }

    /** 拼一行 CSV：单元格经 {@link #csvCell} 处理后以逗号连接，行尾换行 */
    private static void appendRow(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csvCell(cells[i]));
        }
        sb.append('\n');
    }

    /**
     * CSV 单元格清洗，两件事：
     * <ol>
     *   <li><b>注入防护</b>：以 {@code = + - @ Tab CR} 开头的单元格前置一个单引号，
     *       防止 Excel / WPS 当作公式执行；</li>
     *   <li><b>转义</b>：内部双引号翻倍，含逗号/引号/换行时整体加双引号包裹。</li>
     * </ol>
     */
    private static String csvCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String cell = value;
        char head = cell.charAt(0);
        if (head == '=' || head == '+' || head == '-' || head == '@' || head == '\t' || head == '\r') {
            cell = "'" + cell;
        }
        if (cell.indexOf('"') >= 0) {
            cell = cell.replace("\"", "\"\"");
            cell = "\"" + cell + "\"";
        } else if (cell.indexOf(',') >= 0 || cell.indexOf('\n') >= 0) {
            cell = "\"" + cell + "\"";
        }
        return cell;
    }

    /** 对象转字符串，null 转空串 */
    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 前置 UTF-8 BOM */
    private static byte[] concatBom(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + bytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(bytes, 0, result, UTF8_BOM.length, bytes.length);
        return result;
    }
}
