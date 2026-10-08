package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.log.enums.OperatorType;
import com.moyue.common.log.util.IpUtils;
import com.moyue.common.security.context.UserContext;
import com.moyue.system.domain.dto.request.FormSubmitRequest;
import com.moyue.system.service.SysFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线构建器（⑯）渲染端：1 个端点。
 *
 * <p>单独成类是因为该端点<strong>不走 /admin 前缀</strong>（架构说明书 7.6 为
 * {@code POST /api/v1/system/forms/{formId}/submit}），Spring MVC 的方法级路径无法
 * 覆盖类级 {@code @RequestMapping} 前缀。
 *
 * <p>端点不挂 {@code @RequiresPermissions}：C 端匿名表单也要能提交，鉴权下沉到
 * 服务层（仅校验表单是否已发布）。
 *
 * @author moyue
 */
@Tag(name = "表单渲染", description = "C 端表单提交")
@Validated
@RestController
@RequestMapping(Constants.API_PREFIX + "/system/forms")
@RequiredArgsConstructor
public class SysFormRenderController {

    private final SysFormService formService;

    @Operation(summary = "提交表单数据", description = "仅已发布表单可提交；按字段定义做必填校验与白名单过滤")
    @Log(title = "表单提交", businessType = BusinessType.INSERT, operatorType = OperatorType.OTHER)
    @PostMapping("/{formId}/submit")
    public R<Long> submit(@PathVariable Long formId,
                          @RequestBody FormSubmitRequest request,
                          HttpServletRequest httpRequest) {
        request.setFormId(formId);
        Long userId = UserContext.getUserId();
        String username = httpRequest.getHeader(Constants.HEADER_USER_NAME);
        String ip = IpUtils.getIp(httpRequest);
        return R.ok(formService.submit(formId, request.getData(), ip, userId, username));
    }
}
