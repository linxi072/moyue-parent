package com.moyue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.system.domain.entity.SysForm;
import com.moyue.system.domain.entity.SysFormData;
import com.moyue.system.domain.entity.SysFormHistory;
import com.moyue.system.domain.entity.SysFormItem;
import com.moyue.system.mapper.SysFormDataMapper;
import com.moyue.system.mapper.SysFormHistoryMapper;
import com.moyue.system.mapper.SysFormItemMapper;
import com.moyue.system.mapper.SysFormMapper;
import com.moyue.system.service.SysFormService;
import com.moyue.system.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线构建器域（⑯）实现：17 个端点。
 *
 * <p>三块职责：<ul>
 *   <li><b>表单定义</b>：sys_form + sys_form_item，Schema = config + items 的组合；</li>
 *   <li><b>版本管理</b>：发布与回滚前各留档一次 sys_form_history，回滚为整包替换；</li>
 *   <li><b>收集数据</b>：sys_form_data 以 JSON 承载动态字段，按表单定义做必填校验与白名单过滤。</li>
 * </ul>
 *
 * <p><b>范围边界</b>（架构说明书 ⑯）：联动规则、条件显隐、审批流、自定义校验表达式
 * 不在本期范围，故 Schema 只做「存储 + 原样返回」，服务端不解释组件语义。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFormServiceImpl extends ServiceImpl<SysFormMapper, SysForm> implements SysFormService {

    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 状态：已发布（渲染端可提交） */
    public static final int STATUS_PUBLISHED = 1;
    /** 状态：已下线 */
    public static final int STATUS_OFFLINE = 2;

    private final SysFormItemMapper itemMapper;
    private final SysFormDataMapper dataMapper;
    private final SysFormHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------ 表单定义

    @Override
    public PageResult<SysForm> pageForms(String formName, Integer status, int page, int size) {
        LambdaQueryWrapper<SysForm> wrapper = new LambdaQueryWrapper<SysForm>()
                .like(StringUtils.isNotBlank(formName), SysForm::getFormName, formName)
                .eq(status != null, SysForm::getStatus, status)
                .orderByDesc(SysForm::getCreateTime);
        return PageUtils.toResult(page(new Page<>(page, size), wrapper));
    }

    @Override
    public SysForm detail(Long formId) {
        SysForm form = getById(formId);
        if (form == null) {
            throw BusinessException.notFound("表单");
        }
        return form;
    }

    @Override
    public Long createForm(SysForm entity) {
        if (lambdaQuery().eq(SysForm::getFormKey, entity.getFormKey()).count() > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "表单标识已存在：" + entity.getFormKey());
        }
        entity.setStatus(entity.getStatus() == null ? STATUS_DRAFT : entity.getStatus());
        entity.setVersion(entity.getVersion() == null ? 1 : entity.getVersion());
        save(entity);
        return entity.getId();
    }

    @Override
    public boolean updateForm(SysForm entity) {
        SysForm exist = detail(entity.getId());
        Long dup = lambdaQuery().eq(SysForm::getFormKey, entity.getFormKey())
                .ne(SysForm::getId, entity.getId()).count();
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "表单标识已存在：" + entity.getFormKey());
        }
        // 标识与状态不允许通过编辑接口变更：标识改动会让已发出的渲染地址失效，
        // 状态变更必须走发布 / 停用接口（要留版本快照）。
        entity.setFormKey(exist.getFormKey());
        entity.setStatus(exist.getStatus());
        entity.setVersion(exist.getVersion());
        return updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteForm(Long formId) {
        return deleteForm(formId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteForm(Long formId, boolean force) {
        detail(formId);
        long dataCount = countData(formId);
        if (dataCount > 0 && !force) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "该表单已收集 " + dataCount + " 条数据，强制删除请传 force=true");
        }
        if (force) {
            dataMapper.delete(new LambdaQueryWrapper<SysFormData>().eq(SysFormData::getFormId, formId));
        }
        historyMapper.delete(new LambdaQueryWrapper<SysFormHistory>().eq(SysFormHistory::getFormId, formId));
        itemMapper.delete(new LambdaQueryWrapper<SysFormItem>().eq(SysFormItem::getFormId, formId));
        return removeById(formId);
    }

    // ------------------------------------------------------------ Schema

    @Override
    public Map<String, Object> getSchema(Long formId) {
        SysForm form = detail(formId);
        return buildSchema(form);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveSchema(Long formId, Map<String, Object> schema) {
        SysForm form = detail(formId);
        if (schema == null) {
            return false;
        }
        Object config = schema.get("config");
        if (config != null) {
            SysForm update = new SysForm();
            update.setId(formId);
            update.setConfig(config instanceof String text ? text : writeJson(config));
            updateById(update);
        }
        Object raw = schema.get("items");
        if (raw instanceof List<?> list) {
            // 整包替换：先清后建，避免设计器里删掉的字段残留
            itemMapper.delete(new LambdaQueryWrapper<SysFormItem>().eq(SysFormItem::getFormId, formId));
            int sort = 1;
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> map)) {
                    continue;
                }
                SysFormItem item = new SysFormItem();
                item.setFormId(formId);
                item.setItemName(str(map.get("itemName")));
                item.setItemKey(str(map.get("itemKey")));
                item.setItemType(str(map.get("itemType")));
                item.setDefaultValue(str(map.get("defaultValue")));
                item.setPlaceholder(str(map.get("placeholder")));
                item.setOptions(map.get("options") instanceof String options
                        ? options : writeJson(map.get("options")));
                item.setRequired(intOf(map.get("required"), 0));
                item.setSort(intOf(map.get("sort"), sort));
                itemMapper.insert(item);
                sort++;
            }
        }
        log.info("表单 Schema 已保存：formId={}, formKey={}", formId, form.getFormKey());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyForm(Long formId) {
        SysForm src = detail(formId);
        SysForm copy = new SysForm();
        copy.setFormName(src.getFormName() + " 副本");
        copy.setFormKey(src.getFormKey() + "_copy_" + System.currentTimeMillis());
        copy.setFormDesc(src.getFormDesc());
        copy.setConfig(src.getConfig());
        copy.setStatus(STATUS_DRAFT);
        copy.setVersion(1);
        save(copy);
        for (SysFormItem item : listItems(formId)) {
            item.setId(null);
            item.setFormId(copy.getId());
            itemMapper.insert(item);
        }
        return copy.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long formId, Integer status) {
        SysForm form = detail(formId);
        if (status == null || status < STATUS_DRAFT || status > STATUS_OFFLINE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态取值非法，仅支持 0 草稿 / 1 发布 / 2 下线");
        }
        if (status == STATUS_PUBLISHED) {
            // 发布：留档当前 Schema，版本号 +1
            snapshot(form, "发布留档");
            return true;
        }
        SysForm update = new SysForm();
        update.setId(formId);
        update.setStatus(status);
        return updateById(update);
    }

    @Override
    public Map<String, Object> preview(Long formId) {
        return buildSchema(detail(formId));
    }

    // ------------------------------------------------------------ 版本

    @Override
    public List<Map<String, Object>> history(Long formId) {
        detail(formId);
        List<SysFormHistory> list = historyMapper.selectList(new LambdaQueryWrapper<SysFormHistory>()
                .eq(SysFormHistory::getFormId, formId)
                .orderByDesc(SysFormHistory::getVersion));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysFormHistory h : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("versionId", h.getId());
            row.put("version", h.getVersion());
            row.put("publishBy", h.getPublishBy());
            row.put("publishTime", h.getPublishTime());
            row.put("remark", h.getRemark());
            row.put("itemCount", countItems(h.getSchemaJson()));
            result.add(row);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollback(Long formId, Long versionId) {
        SysForm form = detail(formId);
        SysFormHistory target = historyMapper.selectById(versionId);
        if (target == null || !formId.equals(target.getFormId())) {
            throw BusinessException.notFound("历史版本");
        }
        int current = form.getVersion() == null ? 1 : form.getVersion();
        // 回滚前先把当前版本留档，保证回滚本身可回退
        historyMapper.insert(buildHistory(form, current, "回滚至 v" + target.getVersion() + " 前自动留档"));
        saveSchema(formId, readJsonMap(target.getSchemaJson()));
        SysForm update = new SysForm();
        update.setId(formId);
        update.setVersion(current + 1);
        updateById(update);
        return true;
    }

    // ------------------------------------------------------------ 收集数据

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long formId, Map<String, Object> data, String ip, Long userId, String username) {
        SysForm form = detail(formId);
        if (!Integer.valueOf(STATUS_PUBLISHED).equals(form.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "表单未发布或已下线，暂不支持提交");
        }
        List<SysFormItem> items = listItems(formId);
        Map<String, Object> payload = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
        for (SysFormItem item : items) {
            Object value = payload.get(item.getItemKey());
            boolean empty = value == null || (value instanceof String text && text.isBlank());
            if (empty) {
                if (Integer.valueOf(1).equals(item.getRequired())) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "字段必填：" + item.getItemName());
                }
                if (StringUtils.isNotBlank(item.getDefaultValue())) {
                    payload.put(item.getItemKey(), item.getDefaultValue());
                }
            }
        }
        // 白名单过滤：只落库表单定义中声明的字段
        Map<String, Object> clean = new LinkedHashMap<>();
        for (SysFormItem item : items) {
            clean.put(item.getItemKey(), payload.get(item.getItemKey()));
        }
        SysFormData record = new SysFormData();
        record.setFormId(formId);
        record.setFormKey(form.getFormKey());
        record.setDataJson(writeJson(clean));
        record.setSubmitBy(userId);
        record.setSubmitName(username);
        record.setSubmitIp(ip);
        record.setSubmitTime(LocalDateTime.now());
        record.setStatus(1);
        dataMapper.insert(record);
        return record.getId();
    }

    @Override
    public PageResult<SysFormData> pageData(Long formId, int page, int size) {
        detail(formId);
        return PageUtils.toResult(dataMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysFormData>()
                        .eq(SysFormData::getFormId, formId)
                        .orderByDesc(SysFormData::getSubmitTime)));
    }

    @Override
    public SysFormData dataDetail(Long formId, Long dataId) {
        SysFormData record = dataMapper.selectOne(new LambdaQueryWrapper<SysFormData>()
                .eq(SysFormData::getId, dataId)
                .eq(SysFormData::getFormId, formId));
        if (record == null) {
            throw BusinessException.notFound("提交数据");
        }
        return record;
    }

    @Override
    public boolean deleteData(Long formId, Long dataId) {
        return dataMapper.delete(new LambdaQueryWrapper<SysFormData>()
                .eq(SysFormData::getId, dataId)
                .eq(SysFormData::getFormId, formId)) > 0;
    }

    @Override
    public List<SysFormData> listDataForExport(Long formId) {
        return dataMapper.selectList(new LambdaQueryWrapper<SysFormData>()
                .eq(SysFormData::getFormId, formId)
                .orderByDesc(SysFormData::getSubmitTime));
    }

    @Override
    public List<SysFormItem> listItems(Long formId) {
        return itemMapper.selectList(new LambdaQueryWrapper<SysFormItem>()
                .eq(SysFormItem::getFormId, formId)
                .orderByAsc(SysFormItem::getSort));
    }

    // ------------------------------------------------------------ 内部方法

    private Map<String, Object> buildSchema(SysForm form) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("formId", form.getId());
        schema.put("formName", form.getFormName());
        schema.put("formKey", form.getFormKey());
        schema.put("formDesc", form.getFormDesc());
        schema.put("status", form.getStatus());
        schema.put("version", form.getVersion());
        schema.put("config", parseJson(form.getConfig()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (SysFormItem item : listItems(form.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", item.getId());
            row.put("itemName", item.getItemName());
            row.put("itemKey", item.getItemKey());
            row.put("itemType", item.getItemType());
            row.put("defaultValue", item.getDefaultValue());
            row.put("placeholder", item.getPlaceholder());
            row.put("options", parseJson(item.getOptions()));
            row.put("required", item.getRequired());
            row.put("sort", item.getSort());
            items.add(row);
        }
        schema.put("items", items);
        return schema;
    }

    /** 发布留档：写历史 + 置为已发布 + 版本号 +1 */
    private void snapshot(SysForm form, String remark) {
        int current = form.getVersion() == null ? 1 : form.getVersion();
        historyMapper.insert(buildHistory(form, current, remark));
        SysForm update = new SysForm();
        update.setId(form.getId());
        update.setStatus(STATUS_PUBLISHED);
        update.setVersion(current + 1);
        updateById(update);
    }

    private SysFormHistory buildHistory(SysForm form, int version, String remark) {
        SysFormHistory history = new SysFormHistory();
        history.setFormId(form.getId());
        history.setVersion(version);
        history.setSchemaJson(writeJson(buildSchema(form)));
        history.setPublishBy(form.getUpdateBy() == null ? form.getCreateBy() : form.getUpdateBy());
        history.setPublishTime(LocalDateTime.now());
        history.setRemark(remark);
        return history;
    }

    private long countData(Long formId) {
        Long count = dataMapper.selectCount(new LambdaQueryWrapper<SysFormData>()
                .eq(SysFormData::getFormId, formId));
        return count == null ? 0L : count;
    }

    private int countItems(String schemaJson) {
        Map<String, Object> map = readJsonMap(schemaJson);
        Object items = map.get("items");
        return items instanceof List<?> list ? list.size() : 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String text) {
        if (StringUtils.isBlank(text)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (Exception e) {
            log.warn("Schema 快照解析失败：{}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Object parseJson(String text) {
        if (StringUtils.isBlank(text)) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (Exception e) {
            return text;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("JSON 序列化失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "JSON 序列化失败");
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intOf(Object value, int def) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null && StringUtils.isNumeric(String.valueOf(value))) {
            return Integer.parseInt(String.valueOf(value));
        }
        return def;
    }
}
