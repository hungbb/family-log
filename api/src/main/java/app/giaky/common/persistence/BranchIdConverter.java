package app.giaky.common.persistence;

import app.giaky.common.types.BranchId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/** {@link BranchId} ↔ the {@code UUID} in a branch-reference column. */
@Converter(autoApply = true)
public class BranchIdConverter implements AttributeConverter<BranchId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(BranchId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public BranchId convertToEntityAttribute(UUID column) {
        return column == null ? null : BranchId.of(column);
    }
}
