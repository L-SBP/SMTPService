package com.example.mailbox.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private Boolean success;      // 操作是否成功
    private T data;              // 响应数据
    private String message;      // 消息描述
    private List<String> errors; // 错误列表
}