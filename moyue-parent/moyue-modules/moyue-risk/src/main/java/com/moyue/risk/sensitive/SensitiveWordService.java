package com.moyue.risk.sensitive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感词业务：增删改查、CSV 导入导出、命中统计与词库热刷新。
 * <p>启动时从 sensitive_word 表加载 status=1 且未删除的词构建匹配引擎
 * （{@link SensitiveWordEngine}，Aho-Corasick 简化版）；管理端任何写操作后
 * 同步调用 {@link #refresh()} 重建引擎快照，保证机审实时生效。</p>
 */
@Service
public class SensitiveWordService {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordService.class);

    /** 逻辑删除：已删除 */
    private static final int DELETED = 1;

    /** 敏感词等级上限（1 拦截 / 2 告警） */
    private static final int MAX_LEVEL = 2;

    @Autowired
    private SensitiveWordMapper sensitiveWordMapper;

    private final SensitiveWordEngine engine = new SensitiveWordEngine();

    // ------------------------------ 引擎管理 ------------------------------

    /**
     * 启动加载词库。失败只告警不阻断启动（引擎为空词库时机审放行，
     * 词库恢复后管理端刷新即可重建）。
     */
    @PostConstruct
    public void initEngine() {
        try {
            refresh();
            log.info("[sensitive-word] 词库加载完成，启用词数：{}", engine.size());
        } catch (Exception ex) {
            log.warn("[sensitive-word] 词库启动加载失败（机审将以空词库运行）：{}", ex.getMessage());
        }
    }

    /** 手动刷新：从 DB 重建引擎快照，返回当前启用词数 */
    @Transactional(readOnly = true)
    public int refresh() {
        List<SensitiveWordEntity> actives = sensitiveWordMapper.selectList(
                Wrappers.<SensitiveWordEntity>lambdaQuery()
                        .eq(SensitiveWordEntity::getStatus, 1)
                        .eq(SensitiveWordEntity::getIsDeleted, 0));
        engine.reload(actives);
        return engine.size();
    }

    /** 匹配引擎（供机审服务调用） */
    public SensitiveWordEngine engine() {
        return engine;
    }

    // ------------------------------ 查询 ------------------------------

    /** 分页查询（keyword 模糊匹配词、category/level/status 精确过滤） */
    public PageResult<SensitiveWordEntity> page(int page, int size, String keyword, String category,
                                                Integer level, Integer status) {
        Page<SensitiveWordEntity> param = new Page<>(page, size);
        LambdaQueryWrapper<SensitiveWordEntity> q = Wrappers.<SensitiveWordEntity>lambdaQuery()
                .eq(SensitiveWordEntity::getIsDeleted, 0)
                .like(keyword != null && !keyword.isBlank(), SensitiveWordEntity::getWord, keyword)
                .eq(category != null && !category.isBlank(), SensitiveWordEntity::getCategory, category)
                .eq(level != null, SensitiveWordEntity::getLevel, level)
                .eq(status != null, SensitiveWordEntity::getStatus, status)
                .orderByDesc(SensitiveWordEntity::getId);
        IPage<SensitiveWordEntity> result = sensitiveWordMapper.selectPage(param, q);

        PageResult<SensitiveWordEntity> pr = new PageResult<>();
        pr.setTotal(result.getTotal());
        pr.setPage(page);
        pr.setSize(size);
        pr.setRecords(result.getRecords());
        return pr;
    }

    /** 命中次数统计 TopN（机审命中累计，倒序） */
    public List<SensitiveWordEntity> hitStats(int limit) {
        int topN = Math.min(Math.max(limit, 1), 200);
        return sensitiveWordMapper.selectList(Wrappers.<SensitiveWordEntity>lambdaQuery()
                .eq(SensitiveWordEntity::getIsDeleted, 0)
                .gt(SensitiveWordEntity::getHitCount, 0)
                .orderByDesc(SensitiveWordEntity::getHitCount)
                .last("LIMIT " + topN));
    }

    // ------------------------------ 写操作 ------------------------------

    /** 新增敏感词（词唯一校验；写后热刷新引擎） */
    @Transactional
    public SensitiveWordEntity create(String word, Integer level, String category, Integer status) {
        String w = requireWord(word);
        int lv = normalizeLevel(level);
        if (sensitiveWordMapper.selectCount(Wrappers.<SensitiveWordEntity>lambdaQuery()
                .eq(SensitiveWordEntity::getWord, w)
                .eq(SensitiveWordEntity::getIsDeleted, 0)) > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "敏感词已存在：" + w);
        }
        SensitiveWordEntity e = new SensitiveWordEntity();
        e.setWord(w);
        e.setLevel(lv);
        e.setCategory(category == null || category.isBlank() ? null : category.trim());
        e.setStatus(status == null ? 1 : status);
        e.setHitCount(0);
        e.setIsDeleted(0);
        sensitiveWordMapper.insert(e);
        refresh();
        return e;
    }

    /** 编辑敏感词（仅更新非空字段；词变更时唯一校验；写后热刷新引擎） */
    @Transactional
    public SensitiveWordEntity update(Long id, String word, Integer level, String category, Integer status) {
        SensitiveWordEntity e = requireEntity(id);
        if (word != null) {
            String w = requireWord(word);
            if (!w.equals(e.getWord()) && sensitiveWordMapper.selectCount(
                    Wrappers.<SensitiveWordEntity>lambdaQuery()
                            .eq(SensitiveWordEntity::getWord, w)
                            .eq(SensitiveWordEntity::getIsDeleted, 0)
                            .ne(SensitiveWordEntity::getId, id)) > 0) {
                throw new BizException(ResultCode.PARAM_ERROR, "敏感词已存在：" + w);
            }
            e.setWord(w);
        }
        if (level != null) {
            e.setLevel(normalizeLevel(level));
        }
        if (category != null) {
            e.setCategory(category.isBlank() ? null : category.trim());
        }
        if (status != null) {
            e.setStatus(status);
        }
        sensitiveWordMapper.updateById(e);
        refresh();
        return e;
    }

    /** 删除敏感词（逻辑删除；写后热刷新引擎） */
    @Transactional
    public void delete(Long id) {
        SensitiveWordEntity e = requireEntity(id);
        e.setIsDeleted(DELETED);
        sensitiveWordMapper.updateById(e);
        refresh();
    }

    /** 批量停用（status → 0；写后热刷新引擎），返回停用条数 */
    @Transactional
    public int batchDisable(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "请选择要停用的敏感词");
        }
        int affected = sensitiveWordMapper.update(null, Wrappers.<SensitiveWordEntity>lambdaUpdate()
                .in(SensitiveWordEntity::getId, ids)
                .eq(SensitiveWordEntity::getIsDeleted, 0)
                .set(SensitiveWordEntity::getStatus, 0));
        refresh();
        return affected;
    }

    // ------------------------------ CSV 导入导出 ------------------------------

    /**
     * CSV 导入：每行格式 {@code 词,等级(1|2),分类(可空)}，首行表头自动跳过；
     * 库内已存在（未删除）的词自动跳过。返回 (导入条数, 跳过条数)。
     */
    @Transactional
    public int[] importCsv(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "导入内容为空");
        }
        // 既有未删除词集合，用于跳过重复
        Set<String> existing = new HashSet<>();
        sensitiveWordMapper.selectList(Wrappers.<SensitiveWordEntity>lambdaQuery()
                        .eq(SensitiveWordEntity::getIsDeleted, 0)
                        .select(SensitiveWordEntity::getWord))
                .forEach(w -> existing.add(w.getWord()));

        int imported = 0;
        int skipped = 0;
        String[] lines = csvContent.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] cols = line.split(",", -1);
            String word = cols[0] == null ? "" : cols[0].trim();
            // 表头 / 空词行跳过
            if (word.isEmpty() || "word".equalsIgnoreCase(word) || "敏感词".equals(word)) {
                continue;
            }
            if (existing.contains(word)) {
                skipped++;
                continue;
            }
            int level = cols.length > 1 && !cols[1].isBlank() ? parseLevel(cols[1].trim()) : 1;
            String category = cols.length > 2 && !cols[2].isBlank() ? cols[2].trim() : null;
            SensitiveWordEntity e = new SensitiveWordEntity();
            e.setWord(word);
            e.setLevel(level);
            e.setCategory(category);
            e.setStatus(1);
            e.setHitCount(0);
            e.setIsDeleted(0);
            try {
                sensitiveWordMapper.insert(e);
                existing.add(word);
                imported++;
            } catch (Exception ex) {
                // 单行失败（如并发唯一键冲突）不中断整批导入
                skipped++;
                log.warn("[sensitive-word] CSV 导入单行失败已跳过：word={}, err={}", word, ex.getMessage());
            }
        }
        refresh();
        return new int[]{imported, skipped};
    }

    /** CSV 导出：全部未删除词（含停用），UTF-8 BOM 便于 Excel 打开 */
    public byte[] exportCsv() {
        List<SensitiveWordEntity> all = sensitiveWordMapper.selectList(
                Wrappers.<SensitiveWordEntity>lambdaQuery()
                        .eq(SensitiveWordEntity::getIsDeleted, 0)
                        .orderByAsc(SensitiveWordEntity::getId));
        StringBuilder sb = new StringBuilder("word,level,category,status,hit_count\n");
        for (SensitiveWordEntity e : all) {
            sb.append(e.getWord() == null ? "" : e.getWord()).append(',')
                    .append(e.getLevel() == null ? 1 : e.getLevel()).append(',')
                    .append(e.getCategory() == null ? "" : e.getCategory()).append(',')
                    .append(e.getStatus() == null ? 1 : e.getStatus()).append(',')
                    .append(e.getHitCount() == null ? 0 : e.getHitCount()).append('\n');
        }
        // UTF-8 BOM：Excel 识别中文
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] out = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(body, 0, out, bom.length, body.length);
        return out;
    }

    // ------------------------------ 机审联动 ------------------------------

    /** 机审命中后累计 hit_count（失败只告警：统计不影响机审主链路） */
    public void increaseHitCount(List<String> words) {
        if (words == null || words.isEmpty()) {
            return;
        }
        try {
            sensitiveWordMapper.update(null, Wrappers.<SensitiveWordEntity>lambdaUpdate()
                    .in(SensitiveWordEntity::getWord, words)
                    .eq(SensitiveWordEntity::getIsDeleted, 0)
                    .setSql("hit_count = hit_count + 1"));
        } catch (Exception ex) {
            log.warn("[sensitive-word] 命中次数累计失败（已忽略）：words={}, err={}", words, ex.getMessage());
        }
    }

    // ------------------------------ 内部工具 ------------------------------

    private SensitiveWordEntity requireEntity(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "ID 不能为空");
        }
        SensitiveWordEntity e = sensitiveWordMapper.selectOne(
                Wrappers.<SensitiveWordEntity>lambdaQuery()
                        .eq(SensitiveWordEntity::getId, id)
                        .eq(SensitiveWordEntity::getIsDeleted, 0));
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "敏感词不存在");
        }
        return e;
    }

    /** 词必填且长度 ≤ 64（与 DDL 对齐） */
    private String requireWord(String word) {
        if (word == null || word.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "敏感词不能为空");
        }
        String w = word.trim();
        if (w.length() > 64) {
            throw new BizException(ResultCode.PARAM_ERROR, "敏感词长度不能超过 64 个字符");
        }
        return w;
    }

    /** 等级归一：仅接受 1 / 2 */
    private int normalizeLevel(Integer level) {
        if (level == null || level < 1 || level > MAX_LEVEL) {
            throw new BizException(ResultCode.PARAM_ERROR, "等级非法（仅支持 1 拦截 / 2 告警）");
        }
        return level;
    }

    private int parseLevel(String text) {
        try {
            int lv = Integer.parseInt(text);
            return (lv == 1 || lv == 2) ? lv : 1;
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    /** 供命中统计展示（列表页附加字段透传用，当前实体已含 hitCount，保留扩展位） */
    public List<SensitiveWordEntity> listAllActive() {
        return new ArrayList<>(sensitiveWordMapper.selectList(
                Wrappers.<SensitiveWordEntity>lambdaQuery()
                        .eq(SensitiveWordEntity::getStatus, 1)
                        .eq(SensitiveWordEntity::getIsDeleted, 0)));
    }

    /** 时间戳工具（导入/导出文件名用） */
    public static String nowStamp() {
        return LocalDateTime.now().toString().replace(':', '-');
    }
}
