package com.moyue.common.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.util.ServletUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 元对象填充：create_time / update_time / create_by / update_by 自动维护。
 *
 * <p>操作人取网关注入的 X-User-Id（架构说明书 11.2：禁止信任前端传入的用户身份）。
 *
 * @author moyue
 */
@Slf4j
@Component
public class MetaObjectFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        if (operator != null) {
            this.strictInsertFill(metaObject, "createBy", String.class, operator);
            this.strictInsertFill(metaObject, "updateBy", String.class, operator);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        String operator = currentOperator();
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        if (operator != null) {
            this.strictUpdateFill(metaObject, "updateBy", String.class, operator);
        }
    }

    /** 当前操作人：优先取网关注入的请求头，无请求上下文时返回 null（定时任务等场景） */
    private String currentOperator() {
        String userId = ServletUtils.getHeader(Constants.HEADER_USER_ID);
        return Objects.isNull(userId) || userId.isBlank() ? null : userId;
    }
}
