package com.moyue.common.core.exception;

import lombok.Getter;

/**
 * 业务异常：以 HTTP 200 + code != 0 承载，由 {@link GlobalExceptionHandler} 统一收敛。
 *
 * @author moyue
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /** 便捷构造：无权限 */
    public static BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN);
    }

    /** 便捷构造：未登录 */
    public static BusinessException unauthorized() {
        return new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    /** 便捷构造：资源不存在 */
    public static BusinessException notFound(String what) {
        return new BusinessException(ErrorCode.NOT_FOUND.getCode(), what + "不存在或已下架");
    }
}
