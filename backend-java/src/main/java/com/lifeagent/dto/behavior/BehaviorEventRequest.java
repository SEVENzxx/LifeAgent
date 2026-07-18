package com.lifeagent.dto.behavior;

import com.lifeagent.common.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行为事件上传请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorEventRequest {

    /** 客户端生成 UUID，重试必须复用 */
    @NotBlank
    private String eventId;

    /** 稳定 App 标识，仅允许小写字母、数字、点、下划线和短横 */
    @NotBlank
    @Pattern(regexp = "[a-z0-9._-]{1,100}")
    private String appKey;

    /** 显示名，1～100 字符 */
    @NotBlank
    @Pattern(regexp = ".{1,100}")
    private String appName;

    /** OPEN 或 CLOSE */
    @NotBlank
    @Pattern(regexp = "OPEN|CLOSE")
    private String eventType;

    /** 带偏移 ISO 8601，如 2026-07-17T22:58:12+08:00 */
    @NotBlank
    private String eventTime;

    /** 客户端版本，1～50 字符 */
    @NotBlank
    @Pattern(regexp = ".{1,50}")
    private String clientVersion;
}
