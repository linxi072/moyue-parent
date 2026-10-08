package com.moyue.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.moyue.system.domain.entity.GenTable;
import com.moyue.system.domain.entity.GenTableColumn;

import java.util.List;
import java.util.Map;

/**
 * 代码生成域（⑮）服务。
 *
 * @author moyue
 */
public interface GenTableService extends IService<GenTable> {

    /** 数据库表清单（含是否已导入标记） */
    List<Map<String, Object>> listDbTables(String tableName);

    /** 导入表结构为生成配置 */
    int importTables(List<String> tableNames);

    /** 配置详情：基本信息 + 字段列表 */
    GenTable detail(Long tableId);

    /** 字段列表 */
    List<GenTableColumn> listColumns(Long tableId);

    /** 更新生成配置（含字段映射） */
    boolean updateConfig(GenTable table, List<GenTableColumn> columns);

    /** 删除生成配置（含字段） */
    boolean deleteConfig(Long tableId);

    /** 同步最新表结构 */
    int syncTable(Long tableId);

    /** 代码预览：文件 -> 内容 */
    Map<String, String> preview(Long tableId);

    /** 打包下载 */
    byte[] download(Long tableId);

    /** 生成到服务本地路径（限 dev） */
    List<String> generateToPath(Long tableId, String genPath);
}
