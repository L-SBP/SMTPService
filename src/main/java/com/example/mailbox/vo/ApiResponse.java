package com.example.mailbox.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一响应模型
 *
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private Boolean success;      // 操作是否成功
    private T data;              // 响应数据
    private String message;      // 消息描述
    private List<String> errors; // 错误列表

    /**
     * 创建成功的响应
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message("操作成功")
                .build();
    }

    /**
     * 创建成功的响应（带消息）
     *
     * @param data    响应数据
     * @param message 消息描述
     * @param <T>     数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .build();
    }

    /**
     * 创建失败的响应
     *
     * @param message 错误消息
     * @param errors  错误列表
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> error(String message, List<String> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .data(null)
                .message(message)
                .errors(errors)
                .build();
    }

    /**
     * 创建失败的响应（单个错误）
     *
     * @param message 错误消息
     * @param error   错误信息
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> error(String message, String error) {
        return ApiResponse.<T>builder()
                .success(false)
                .data(null)
                .message(message)
                .errors(List.of(error))
                .build();
    }

    /**
     * 创建失败的响应（无错误列表）
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .data(null)
                .message(message)
                .errors(null)
                .build();
    }
}
