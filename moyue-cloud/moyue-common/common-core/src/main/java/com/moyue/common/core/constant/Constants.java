package com.moyue.common.core.constant;

/**
 * 全局常量。
 *
 * @author moyue
 */
public final class Constants {

    private Constants() {
    }

    /** 网关 / 内置过滤器注入的身份请求头：当前用户 ID（写操作归属校验一律取此值） */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 主体性质：1 读者 / 2 作者 / 3 运营 */
    public static final String HEADER_USER_TYPE = "X-User-Type";

    /** 角色标识，多个用逗号分隔 */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /** 当前用户名（审计用） */
    public static final String HEADER_USER_NAME = "X-User-Name";

    /** 链路追踪 ID */
    public static final String TRACE_ID = "traceId";

    /** 逻辑删除：未删除 */
    public static final int NOT_DELETED = 0;

    /** 逻辑删除：已删除 */
    public static final int DELETED = 1;

    /** 状态：停用 / 禁用 */
    public static final int STATUS_DISABLE = 0;

    /** 状态：正常 / 启用 */
    public static final int STATUS_ENABLE = 1;

    /** 主体性质：读者 */
    public static final int USER_TYPE_READER = 1;

    /** 主体性质：作者 */
    public static final int USER_TYPE_AUTHOR = 2;

    /** 主体性质：运营（可进入后台） */
    public static final int USER_TYPE_OPERATOR = 3;

    /** 后台路径前缀（需 user_type=3 且至少拥有一个后台角色） */
    public static final String ADMIN_PATH_PREFIX = "/api/v1/admin";

    /** 接口统一前缀 */
    public static final String API_PREFIX = "/api/v1";

    // ------------------------------------------------------------------
    // 权限与菜单（V5.0 补齐：供个人中心、鉴权切面、代码生成共用，避免各处硬编码字面量）
    // ------------------------------------------------------------------

    /**
     * 超级管理员角色标识：拥有该角色即视为全权限。
     *
     * <p><b>取值必须与 V12 初始化数据 {@code sys_role.role_key} 一致</b>（建表脚本写入的是
     * {@code ROLE_ADMIN}）。此前此处写 {@code admin}，导致登录鉴权认为你是超管、
     * 而权限汇总认为你不是，后台按钮全部 403——三处现已统一引用本常量。
     */
    public static final String SUPER_ROLE_KEY = "ROLE_ADMIN";

    /** 通配权限串 */
    public static final String ALL_PERMISSION = "*:*:*";

    /** 菜单类型：目录 */
    public static final String MENU_TYPE_DIR = "M";

    /** 菜单类型：菜单 */
    public static final String MENU_TYPE_MENU = "C";

    /** 菜单类型：按钮（权限串挂在按钮上） */
    public static final String MENU_TYPE_BUTTON = "F";
}
