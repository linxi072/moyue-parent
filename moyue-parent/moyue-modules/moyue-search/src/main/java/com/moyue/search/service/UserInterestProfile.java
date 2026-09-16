package com.moyue.search.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 用户兴趣画像（规则画像 v1）：由书架书籍的类目 / 作者偏好聚合而成。
 *
 * <p>权重即该用户书架中属于某类目 / 某作者的书籍数（书架=收藏=兴趣信号）。
 * {@link #EMPTY} 表示无画像（新用户 / 服务降级），调用方据此回退热门推荐。</p>
 */
public class UserInterestProfile {

    /** 空画像（不可变），表示无法构建个性化信号 */
    public static final UserInterestProfile EMPTY =
            new UserInterestProfile(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(), true);

    private final Map<String, Integer> categoryWeights;
    private final Map<String, Integer> authorWeights;
    private final Set<Long> shelfBookIds;
    private final boolean empty;

    public UserInterestProfile(Map<String, Integer> categoryWeights,
                               Map<String, Integer> authorWeights,
                               Set<Long> shelfBookIds) {
        this(categoryWeights, authorWeights, shelfBookIds, false);
    }

    private UserInterestProfile(Map<String, Integer> categoryWeights,
                                Map<String, Integer> authorWeights,
                                Set<Long> shelfBookIds,
                                boolean empty) {
        this.categoryWeights = categoryWeights;
        this.authorWeights = authorWeights;
        this.shelfBookIds = shelfBookIds;
        this.empty = empty;
    }

    /** 类目名 → 偏好权重（书架中该类目书籍数） */
    public Map<String, Integer> getCategoryWeights() {
        return categoryWeights;
    }

    /** 作者名 → 偏好权重（书架中该作者书籍数） */
    public Map<String, Integer> getAuthorWeights() {
        return authorWeights;
    }

    /** 书架书籍 ID 集合（用于个性化召回时排除已书架书籍） */
    public Set<Long> getShelfBookIds() {
        return shelfBookIds;
    }

    public boolean isEmpty() {
        return empty;
    }

    /** 合并两个权重表（用于单测构造） */
    public static Map<String, Integer> mergeInto(Map<String, Integer> target, Map<String, Integer> src) {
        Map<String, Integer> out = new HashMap<>(target);
        if (src != null) {
            src.forEach((k, v) -> out.merge(k, v, Integer::sum));
        }
        return out;
    }

    public static UserInterestProfile of(Map<String, Integer> cat, Map<String, Integer> auth, Set<Long> shelf) {
        boolean noWeights = (cat == null || cat.isEmpty()) && (auth == null || auth.isEmpty());
        boolean noShelf = (shelf == null || shelf.isEmpty());
        // 书架书籍本身是兴趣信号（用于去重），即便取不到类目/作者元数据也不视为空画像
        if (noWeights && noShelf) {
            return EMPTY;
        }
        return new UserInterestProfile(
                cat == null ? Collections.emptyMap() : cat,
                auth == null ? Collections.emptyMap() : auth,
                shelf == null ? Collections.emptySet() : shelf);
    }
}
