package com.moyue.message.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.message.domain.dto.query.MessageQuery;
import com.moyue.message.domain.entity.Message;
import com.moyue.message.domain.vo.MessageVO;
import com.moyue.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内信管理（消息域）：CRUD + 未读计数。
 *
 * @author moyue
 */
@Tag(name = "站内信", description = "系统 / 活动 / 私信消息增删改查与未读计数")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/message/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "消息分页")
    @RequiresPermissions("message:message:list")
    @GetMapping
    public R<PageResult<MessageVO>> page(MessageQuery query) {
        return R.ok(messageService.pageMessages(query));
    }

    @Operation(summary = "新建消息")
    @RequiresPermissions("message:message:add")
    @Log(title = "站内信", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody Message entity) {
        return R.ok(messageService.createMessage(entity));
    }

    @Operation(summary = "编辑消息")
    @RequiresPermissions("message:message:edit")
    @Log(title = "站内信", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody Message entity) {
        entity.setId(id);
        return R.ok(messageService.updateMessage(entity));
    }

    @Operation(summary = "删除消息")
    @RequiresPermissions("message:message:remove")
    @Log(title = "站内信", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(messageService.deleteMessage(id));
    }

    @Operation(summary = "未读消息数", description = "按接收人统计未读")
    @RequiresPermissions("message:message:list")
    @GetMapping("/unread-count")
    public R<Long> unreadCount(@RequestParam Long toUser) {
        return R.ok(messageService.unreadCount(toUser));
    }

    @Operation(summary = "单发站内信", description = "默认未读；可引用模板编码渲染 ${name}")
    @RequiresPermissions("message:message:send")
    @Log(title = "站内信", businessType = BusinessType.INSERT)
    @PostMapping("/send")
    public R<Long> send(@RequestParam Long toUser,
                        @RequestParam String title,
                        @RequestParam String content,
                        @RequestParam(required = false, defaultValue = "1") Integer type,
                        @RequestParam(required = false) String templateCode,
                        @RequestParam(required = false) String name) {
        return R.ok(messageService.sendOne(toUser, title, content, type, templateCode, name));
    }

    @Operation(summary = "批量群发", description = "每人一条，均默认未读")
    @RequiresPermissions("message:message:send")
    @Log(title = "站内信", businessType = BusinessType.INSERT)
    @PostMapping("/send-batch")
    public R<List<Long>> sendBatch(@RequestParam List<Long> toUserIds,
                                   @RequestParam String title,
                                   @RequestParam String content,
                                   @RequestParam(required = false, defaultValue = "1") Integer type,
                                   @RequestParam(required = false) String templateCode,
                                   @RequestParam(required = false) String name) {
        return R.ok(messageService.sendBatch(toUserIds, title, content, type, templateCode, name));
    }

    @Operation(summary = "全部已读", description = "按消息 ID 列表标记已读")
    @RequiresPermissions("message:message:edit")
    @Log(title = "站内信", businessType = BusinessType.UPDATE)
    @PostMapping("/read-all")
    public R<Boolean> readAll(@RequestBody List<Long> ids) {
        return R.ok(messageService.readAll(ids));
    }
}
