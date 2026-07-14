package com.lifeagent.controller;

import com.lifeagent.common.ApiResponse;
import com.lifeagent.common.Constants;
import com.lifeagent.dto.SystemInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@Tag(name = "系统信息", description = "查询 LifeAgent Java 服务运行信息")
@Profile({Constants.API_PROFILE, Constants.ALL_IN_ONE_PROFILE})
@RestController
@RequestMapping(Constants.SYSTEM_API_PATH)
public class SystemController {

    private final Environment environment;

    @Operation(summary = "查询系统信息", description = "返回应用名称和当前运行 Profile")
    @GetMapping
    public ApiResponse<SystemInfoResponse> getSystemInfo() {
        log.debug("系统信息查询请求进入");
        SystemInfoResponse response = new SystemInfoResponse(
                Constants.APPLICATION_NAME,
                resolveProfile()
        );
        log.info("系统信息查询完成, profile={}", response.getProfile());
        return ApiResponse.ok(response);
    }

    private String resolveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return String.join(Constants.PROFILE_SEPARATOR, activeProfiles);
        }

        String[] defaultProfiles = environment.getDefaultProfiles();
        if (defaultProfiles.length > 0) {
            return String.join(Constants.PROFILE_SEPARATOR, defaultProfiles);
        }
        return Constants.DEFAULT_PROFILE;
    }
}
