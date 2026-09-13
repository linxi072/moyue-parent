package com.moyue.operation.controller;

import com.moyue.common.core.domain.PageResult;
import com.moyue.common.core.domain.R;
import com.moyue.operation.entity.AnnouncementEntity;
import com.moyue.operation.service.OperationService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营公告接口（后台）。
 * 完整路径 /api/v1/admin/announcements，与网关 /api/v1/admin/announcements/** 路由匹配；
 * AdminRoleInterceptor（moyue-common）已对该前缀做 role=3 断言。
 */
@RestController
@RequestMapping("/api/v1/admin/announcements")
public class AnnouncementController {

    @Autowired
    private OperationService operationService;

    /** 公告分页（可选 status 过滤）：GET /api/v1/admin/announcements?status=1 */
    @GetMapping
    public R<PageResult<AnnouncementEntity>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) Integer status) {
        return R.ok(operationService.listAnnouncements(page, size, status));
    }

    /** 新建公告 */
    @PostMapping
    public R<AnnouncementEntity> create(@RequestBody AnnouncementRequest req) {
        return R.ok(operationService.createAnnouncement(req.getTitle(), req.getContent(),
                req.getType(), req.getIsTop(), req.getStatus()));
    }

    /** 编辑公告（仅更新非空字段） */
    @PutMapping("/{id}")
    public R<AnnouncementEntity> update(@PathVariable Long id, @RequestBody AnnouncementRequest req) {
        return R.ok(operationService.updateAnnouncement(id, req.getTitle(), req.getContent(),
                req.getType(), req.getIsTop(), req.getStatus()));
    }

    /** 删除公告（逻辑删除） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        operationService.deleteAnnouncement(id);
        return R.ok();
    }

    /** 公告请求体（字段均可选，编辑时仅更新非空字段） */
    @Data
    public static class AnnouncementRequest {
        private String title;
        private String content;
        /** 1 站内公告 / 2 活动 / 3 系统维护 */
        private Integer type;
        /** 0 草稿 / 1 已发布 / 2 已下线 */
        private Integer status;
        /** 0 否 / 1 置顶 */
        private Integer isTop;
    }
}
