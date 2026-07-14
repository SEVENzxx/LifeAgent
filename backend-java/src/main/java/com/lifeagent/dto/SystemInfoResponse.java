package com.lifeagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统运行信息")
public class SystemInfoResponse {

    /**
     * 当前应用名称
     */
    @Schema(description = "当前应用名称")
    private String application;

    /**
     * 当前启用的 Spring Profile
     */
    @Schema(description = "当前启用的 Spring Profile")
    private String profile;
}
