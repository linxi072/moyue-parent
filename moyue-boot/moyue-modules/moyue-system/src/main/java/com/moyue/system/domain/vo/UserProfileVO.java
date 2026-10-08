package com.moyue.system.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 个人中心视图：用户基本信息 + 角色标识 + 权限串。
 *
 * <p>【架构缺口 G-11】说明书 7.6 ①用户管理只列了 10 个管理端点，没有定义
 * 「当前登录者资料」端点；但后台「个人中心」是必备功能，故在此补齐：
 * <ul>
 *   <li>{@code GET  /admin/system/users/profile} 读资料（含 roleKeys / permissions）</li>
 *   <li>{@code PUT  /admin/system/users/profile} 改资料（仅昵称 / 手机 / 邮箱 / 头像，防越权）</li>
 *   <li>{@code PUT  /admin/system/users/profile/password} 改密码（校验原密码）</li>
 * </ul>
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserProfileVO extends UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色标识列表，如 content:admin */
    private List<String> roleKeys;

    /** 权限串列表，超管返回 *:*:* */
    private List<String> permissions;
}
