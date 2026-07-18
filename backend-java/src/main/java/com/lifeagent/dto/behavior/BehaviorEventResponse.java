package com.lifeagent.dto.behavior;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行为事件上传响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorEventResponse {

    /** 客户端提交的 eventId */
    private String eventId;

    /** ACCEPTED / DUPLICATE */
    private String result;
}
