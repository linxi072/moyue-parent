package com.moyue.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.moyue.common.core.result.PageResult;
import com.moyue.system.domain.entity.SysForm;
import com.moyue.system.domain.entity.SysFormData;
import com.moyue.system.domain.entity.SysFormItem;

import java.util.List;
import java.util.Map;

/**
 * 在线构建器域（⑯）服务。
 *
 * @author moyue
 */
public interface SysFormService extends IService<SysForm> {

    PageResult<SysForm> pageForms(String formName, Integer status, int page, int size);

    SysForm detail(Long formId);

    Long createForm(SysForm entity);

    boolean updateForm(SysForm entity);

    /**
     * 删除表单。
     *
     * <p>已收集数据且 force = false 时抛 {@link com.moyue.common.core.exception.BusinessException}
     * 并回带数据条数，由前端据提示二次确认后传 force = true。
     */
    boolean deleteForm(Long formId);

    /** 强制删除：连带清除已收集数据与历史版本 */
    boolean deleteForm(Long formId, boolean force);

    /** 读取表单 Schema（整体配置 + 组件树） */
    Map<String, Object> getSchema(Long formId);

    /** 保存表单 Schema，items 为整包替换语义 */
    boolean saveSchema(Long formId, Map<String, Object> schema);

    Long copyForm(Long formId);

    boolean changeStatus(Long formId, Integer status);

    /** 预览渲染结果（返回 Schema 供前端渲染） */
    Map<String, Object> preview(Long formId);

    List<Map<String, Object>> history(Long formId);

    /** 回滚到指定历史版本 */
    boolean rollback(Long formId, Long versionId);

    /** 【渲染端】提交表单数据 */
    Long submit(Long formId, Map<String, Object> data, String ip, Long userId, String username);

    PageResult<SysFormData> pageData(Long formId, int page, int size);

    SysFormData dataDetail(Long formId, Long dataId);

    boolean deleteData(Long formId, Long dataId);

    List<SysFormData> listDataForExport(Long formId);

    /** 字段项列表，供渲染与导出表头使用 */
    List<SysFormItem> listItems(Long formId);
}
