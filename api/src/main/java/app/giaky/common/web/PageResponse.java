package app.giaky.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The paged-response envelope from {@code .docs/05-api-specification.md} §3.2.
 *
 * <p>Controllers return this rather than Spring Data's {@code Page}, whose serialized shape is not
 * part of our contract and changes between Spring versions.
 */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
