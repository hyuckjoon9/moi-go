package com.mycom.myapp.admin.dto.response;

import java.util.List;

public record AdminPageResponse<T>(
        List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {

    public static <T> AdminPageResponse<T> of(
            List<T> items, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new AdminPageResponse<>(
                items, page, size, totalElements, totalPages, page + 1 < totalPages);
    }
}
