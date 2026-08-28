package app.giaky.common.types;

import java.util.List;

/**
 * A slice of a longer list, as the application layer speaks it.
 *
 * <p>Not {@code common.web.PageResponse} and not Spring Data's {@code Page}: an application service
 * is driven by jobs and listeners as well as by HTTP, and neither of those should have to know what
 * a response envelope looks like. The controller maps this onto the envelope of doc 05 §3.2.
 *
 * @param page zero-based
 */
public record Paged<T>(List<T> content, int page, int size, long totalElements) {

    public Paged {
        content = List.copyOf(content);
    }

    /** Pages a list already held in memory. */
    public static <T> Paged<T> of(List<T> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new Paged<>(all.subList(from, to), page, size, all.size());
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
