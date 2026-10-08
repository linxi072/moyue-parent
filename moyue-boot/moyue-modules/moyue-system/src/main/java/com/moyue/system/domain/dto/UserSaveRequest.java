package com.moyue.system.domain.dto;

import com.moyue.system.domain.entity.SysUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 用户新增 / 编辑请求：SysUser 字段平铺 + 角色 ID 列表。
 *
 * <p>Spring MVC 不允许一个方法出现两个 {@code @RequestBody}，故用继承把实体字段平铺，
 * 前端 JSON 保持 {@code {"username": "...", "roleIds": [1,2]}} 的自然结构。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserSaveRequest extends SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 待分配角色 ID。
     *
     * <p>新增时为空表示不分配；编辑时为 null 表示不调整角色，为空数组表示清空角色。
     */
    private List<Long> roleIds;
}
