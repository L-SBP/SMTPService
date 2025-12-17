package com.example.mailbox.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应模型
 *
 * @param <T> 分页数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Page<T> {
    private List<T> content;      // 当前页数据
    private Long totalElements;   // 总元素数
    private Integer totalPages;   // 总页数
    private Integer size;         // 每页大小
    private Integer number;       // 当前页码(从0开始)
    private Boolean first;        // 是否是第一页
    private Boolean last;         // 是否是最后一页

    /**
     * 创建分页响应
     *
     * @param content       当前页数据
     * @param totalElements 总元素数
     * @param totalPages    总页数
     * @param size          每页大小
     * @param number        当前页码
     * @param <T>           数据类型
     * @return 分页响应
     */
    public static <T> Page<T> of(List<T> content, Long totalElements, Integer totalPages, Integer size, Integer number) {
        return Page.<T>builder()
                .content(content)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .size(size)
                .number(number)
                .first(number == 0)
                .last(number.equals(totalPages - 1))
                .build();
    }

    /**
     * 创建空分页响应
     *
     * @param <T> 数据类型
     * @return 空分页响应
     */
    public static <T> Page<T> empty() {
        return Page.<T>builder()
                .content(List.of())
                .totalElements(0L)
                .totalPages(0)
                .size(0)
                .number(0)
                .first(true)
                .last(true)
                .build();
    }
}
