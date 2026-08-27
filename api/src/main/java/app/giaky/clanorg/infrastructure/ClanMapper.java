package app.giaky.clanorg.infrastructure;

import app.giaky.clanorg.domain.Clan;
import app.giaky.clanorg.domain.ClanCode;
import app.giaky.common.types.ClanId;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link Clan} ↔ {@link ClanJpaEntity}.
 *
 * <p>MapStruct copies the fields; the {@code default} methods teach it the value objects. Because
 * the build sets {@code unmappedTargetPolicy=ERROR}, adding a column and forgetting to map it fails
 * compilation rather than silently writing a null.
 */
@Mapper
public interface ClanMapper {

    ClanJpaEntity toEntity(Clan clan);

    /**
     * The {@code ignore} is not an unmapped column.
     *
     * <p>MapStruct reads any one-argument method returning its own declaring type as a fluent
     * setter, and {@code Clan.withRootBranch(BranchId)} has exactly that shape. It is behaviour, not
     * state — the record's components are all mapped through its canonical constructor — so naming
     * it here keeps the strict policy switched on for everything that really is state.
     */
    @Mapping(target = "withRootBranch", ignore = true)
    Clan toDomain(ClanJpaEntity entity);

    default UUID fromClanId(ClanId value) {
        return value == null ? null : value.value();
    }

    default ClanId toClanId(UUID value) {
        return value == null ? null : ClanId.of(value);
    }

    default String fromClanCode(ClanCode value) {
        return value == null ? null : value.value();
    }

    default ClanCode toClanCode(String value) {
        return value == null ? null : ClanCode.of(value);
    }
}
