package com.example.mailbox.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Page<T> {
    private List<T> content;      // 当前页数据
    private Long totalElements;   // 总元素数
    private Integer totalPages;   // 总页数
    private Integer size;         // 每页大小
    private Integer number;       // 当前页码(从0开始)
    private Boolean first;        // 是否是第一页
    private Boolean last;         // 是否是最后一页
}
