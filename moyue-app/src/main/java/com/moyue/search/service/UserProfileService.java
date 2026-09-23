package com.moyue.search.service;

import com.moyue.api.content.client.BookClient;
import com.moyue.api.content.client.BookshelfClient;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.content.dto.BookshelfSummaryDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户兴趣画像服务（P1-3 规则画像）：聚合书架书籍的类目 / 作者偏好。
 *
 * <p>取数路径：{@link BookshelfClient} 取用户书架 bookId 列表 → 逐本 {@link BookClient#getBook(Long)}
 * 取类目名 / 作者名 → 统计权重。任一 Feign 客户端不可用（{@code @Autowired(required = false)}）
 * 即退化为 {@link UserInterestProfile#EMPTY}，个性化推荐据此回退热门，不阻断主流程。</p>
 */
@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    /** 参与画像的书架书籍上限（避免大书架 N 次 BookClient 往返拖慢推荐） */
    private static final int MAX_SHELF_BOOKS = 50;

    @Autowired(required = false)
    private BookshelfClient bookshelfClient;

    @Autowired(required = false)
    private BookClient bookClient;

    /**
     * 构建用户兴趣画像。
     *
     * @param userId 用户 ID（null 返回空画像）
     * @return 兴趣画像；无信号 / 降级时返回 {@link UserInterestProfile#EMPTY}
     */
    public UserInterestProfile buildProfile(Long userId) {
        if (userId == null) {
            return UserInterestProfile.EMPTY;
        }
        if (bookshelfClient == null) {
            log.warn("[profile] 书架客户端不可用，画像退化为空：userId={}", userId);
            return UserInterestProfile.EMPTY;
        }
        R<List<BookshelfSummaryDTO>> shelfResp = bookshelfClient.getBookshelf(userId);
        if (shelfResp == null || shelfResp.getCode() != ResultCode.SUCCESS.getCode()
                || shelfResp.getData() == null || shelfResp.getData().isEmpty()) {
            return UserInterestProfile.EMPTY;
        }

        Map<String, Integer> categoryWeights = new HashMap<>();
        Map<String, Integer> authorWeights = new HashMap<>();
        Set<Long> shelfBookIds = new HashSet<>();
        int scanned = 0;
        for (BookshelfSummaryDTO s : shelfResp.getData()) {
            if (s.getBookId() == null) {
                continue;
            }
            shelfBookIds.add(s.getBookId());
            if (bookClient == null || scanned >= MAX_SHELF_BOOKS) {
                continue;
            }
            scanned++;
            R<BookSummaryDTO> bookResp = bookClient.getBook(s.getBookId());
            if (bookResp == null || bookResp.getCode() != ResultCode.SUCCESS.getCode() || bookResp.getData() == null) {
                continue;
            }
            BookSummaryDTO book = bookResp.getData();
            if (book.getCategory() != null && !book.getCategory().isBlank()) {
                categoryWeights.merge(book.getCategory(), 1, Integer::sum);
            }
            if (book.getAuthor() != null && !book.getAuthor().isBlank()) {
                authorWeights.merge(book.getAuthor(), 1, Integer::sum);
            }
        }

        if (categoryWeights.isEmpty() && authorWeights.isEmpty()) {
            // 有书架但取不到书籍元数据：仍保留 shelfBookIds 供去重，但无偏好权重 → 视为空画像
            return UserInterestProfile.of(categoryWeights, authorWeights, shelfBookIds);
        }
        return UserInterestProfile.of(categoryWeights, authorWeights, shelfBookIds);
    }
}
