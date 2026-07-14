package com.lifeagent.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一 API 响应")
public class ApiResponse<T> {

    /**
     * 请求是否处理成功
     */
    @Schema(description = "请求是否处理成功")
    private boolean success;

    /**
     * 面向调用方的处理结果说明
     */
    @Schema(description = "面向调用方的处理结果说明")
    private String message;

    /**
     * 经过 DTO 封装的响应数据
     */
    @Schema(description = "经过 DTO 封装的响应数据")
    private T data;

    /**
     * 创建成功响应。
     *
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, Constants.SUCCESS_MESSAGE, data);
    }

    /**
     * 创建失败响应。
     *
     * @param message 失败原因
     * @param <T> 响应数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
