package com.moyue.common.exception;

import com.moyue.common.core.domain.ResultCode;

/**
 * 业务异常。携带业务错误码，由全局异常处理器统一转换为 R&lt;T&gt;。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码（对应 ResultCode.code） */
    private final int code;

    public BizException(ResultCode code) {
        super(code.getMessage());
        this.code = code.getCode();
    }

    public BizException(ResultCode code, String message) {
        super(message);
        this.code = code.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
