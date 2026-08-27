package app.giaky.common.persistence;

import app.giaky.common.types.ClanId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/** {@link ClanId} ↔ the {@code UUID} in a {@code clan_id} column. Applied to every such attribute. */
@Converter(autoApply = true)
public class ClanIdConverter implements AttributeConverter<ClanId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(ClanId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ClanId convertToEntityAttribute(UUID column) {
        return column == null ? null : ClanId.of(column);
    }
}
