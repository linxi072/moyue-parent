package com.moyue.common.redis.constant;

/**
 * 缓存名称常量：集中管理，避免散落字符串。
 *
 * <p>字典与参数变更后需<b>主动失效</b>（架构说明书 D-14）。
 *
 * @author moyue
 */
public final class CacheNames {

    private CacheNames() {
    }

    /** 字典数据：key = cacheKey + "::" + dictType */
    public static final String DICT_DATA = "moyue:dict:data";

    /** 系统参数：key = cacheKey + "::" + configKey */
    public static final String SYS_CONFIG = "moyue:config";

    /** 用户权限（角色标识集合）：key = cacheKey + "::" + userId */
    public static final String USER_ROLES = "moyue:user:roles";

    /** 用户菜单树：key = cacheKey + "::" + userId */
    public static final String USER_MENUS = "moyue:user:menus";

    /** 在线会话：key = cacheKey + "::" + tokenId */
    public static final String ONLINE_TOKEN = "moyue:online:token";

    /** 登录失败次数（账号锁定）：key = cacheKey + "::" + username */
    public static final String LOGIN_FAIL = "moyue:login:fail";

    /** 幂等键：key = cacheKey + "::" + idemKey */
    public static final String IDEMPOTENT = "moyue:idem";
}
