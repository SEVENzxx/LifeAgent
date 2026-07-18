package com.lifeagent.dto.behavior;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 活动窗口查询请求参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityWindowQuery {

    /** 窗口开始时间（含），带偏移 ISO 8601 */
    @NotBlank
    private String from;

    /** 窗口结束时间（不含），带偏移 ISO 8601 */
    @NotBlank
    private String to;

    /** 选填 App 过滤 */
    private String appKey;

    /** 是否返回区间明细 */
    @Builder.Default
    private boolean includeIntervals = false;
}
