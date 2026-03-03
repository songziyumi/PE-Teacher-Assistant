package com.pe.assistant.dto;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
public class PageDto<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;

    public static <T> PageDto<T> of(Page<T> page) {
        PageDto<T> dto = new PageDto<>();
        dto.content = page.getContent();
        dto.totalElements = page.getTotalElements();
        dto.totalPages = page.getTotalPages();
        dto.page = page.getNumber();
        dto.size = page.getSize();
        return dto;
    }
}
