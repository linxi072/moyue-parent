package com.moyue.social.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.core.result.PageQuery;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.social.domain.vo.ImConversationVO;
import com.moyue.social.domain.vo.ImMessageVO;
import com.moyue.social.service.ImService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * IM 会话管理（互动域·运营端）：列表 / 查看消息 / 禁用。
 *
 * @author moyue
 */
@Tag(name = "会话管理", description = "会话列表 / 查看消息 / 禁用")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/social/im/conversations")
@RequiredArgsConstructor
public class ConversationAdminController {

    private final ImService imService;

    @Operation(summary = "会话分页（运营全局视角）")
    @RequiresPermissions("social:im:list")
    @GetMapping
    public R<PageResult<ImConversationVO>> page(PageQuery query) {
        return R.ok(imService.adminPageConversations(query));
    }

    @Operation(summary = "会话消息列表（运营查看，无视成员身份）")
    @RequiresPermissions("social:im:list")
    @GetMapping("/{id}/messages")
    public R<List<ImMessageVO>> messages(@PathVariable Long id,
                                         @RequestParam(defaultValue = "20") int size) {
        return R.ok(imService.adminListMessages(id, size));
    }

    @Operation(summary = "禁用 / 启用会话", description = "disabled=1 禁用 / 0 启用")
    @RequiresPermissions("social:im:disable")
    @Log(title = "会话管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/disable")
    public R<Boolean> disable(@PathVariable Long id, @RequestParam int disabled) {
        return R.ok(imService.disableConversation(id, disabled));
    }
}
