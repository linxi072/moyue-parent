package com.moyue.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyue.risk.domain.entity.SensitiveWord;
import com.moyue.risk.mapper.SensitiveWordMapper;
import com.moyue.risk.service.SensitiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 敏感词检测实现：加载启用词表，做本地子串匹配，命中即累加计数。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveServiceImpl implements SensitiveService {

    private final SensitiveWordMapper sensitiveWordMapper;

    /** 命中的词：用于回显与计数 */
    private static class Hit {
        final Long id;
        final String word;
        final int level;
        Hit(Long id, String word, int level) {
            this.id = id;
            this.word = word;
            this.level = level;
        }
    }

    private Hit detect(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<SensitiveWord> words = sensitiveWordMapper.selectList(
                new LambdaQueryWrapper<SensitiveWord>()
                        .eq(SensitiveWord::getEnabled, SensitiveWord.ENABLED_ON)
                        .select(SensitiveWord::getId, SensitiveWord::getWord, SensitiveWord::getLevel));
        Hit best = null;
        for (SensitiveWord w : words) {
            if (w.getWord() == null || w.getWord().isEmpty()) {
                continue;
            }
            if (text.contains(w.getWord())) {
                // 拦截(1) 优先于 告警(2)
                if (best == null || w.getLevel() < best.level) {
                    best = new Hit(w.getId(), w.getWord(), w.getLevel());
                }
            }
        }
        if (best != null) {
            // 命中计数 +1（同步更新，保证计数准确）
            sensitiveWordMapper.update(null, new LambdaUpdateWrapper<SensitiveWord>()
                    .eq(SensitiveWord::getId, best.id)
                    .setSql("hit_count = hit_count + 1"));
        }
        return best;
    }

    @Override
    public boolean contains(String text) {
        return detect(text) != null;
    }

    @Override
    public Optional<Integer> matchLevel(String text) {
        Hit h = detect(text);
        return h == null ? Optional.empty() : Optional.of(h.level);
    }

    @Override
    public Optional<String> firstHit(String text) {
        Hit h = detect(text);
        return h == null ? Optional.empty() : Optional.of(h.word);
    }
}
