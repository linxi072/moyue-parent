package com.moyue.api.content.client;

import com.moyue.api.content.dto.BookshelfSummaryDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.read.entity.BookshelfEntity;
import com.moyue.read.service.ReadService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 书架服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-content) 已移除 OpenFeign，改为直接注入 {@link ReadService} 委托调用。
 * 取书架书籍 ID 列表，用于推荐画像构建。
 */
@Component
public class BookshelfClient {

    private final ReadService readService;

    public BookshelfClient(ReadService readService) {
        this.readService = readService;
    }

    /** 取用户书架书籍 ID 列表（仅含 bookId） */
    public R<List<BookshelfSummaryDTO>> getBookshelf(Long userId) {
        try {
            List<BookshelfEntity> entities = readService.getShelf(userId);
            List<BookshelfSummaryDTO> dtos = entities == null ? new ArrayList<>()
                    : entities.stream().map(e -> {
                        BookshelfSummaryDTO d = new BookshelfSummaryDTO();
                        d.setBookId(e.getBookId());
                        return d;
                    }).collect(Collectors.toList());
            return R.ok(dtos);
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}
