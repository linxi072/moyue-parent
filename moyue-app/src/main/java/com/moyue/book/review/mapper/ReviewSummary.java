package com.moyue.book.review.mapper;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ReviewSummary {
    private BigDecimal avg;
    private Long cnt;
}
