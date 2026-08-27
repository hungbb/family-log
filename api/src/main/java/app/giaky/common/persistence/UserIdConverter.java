package app.giaky.common.persistence;

import app.giaky.common.types.UserId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/**
 * {@link UserId} ↔ the {@code UUID} in a user-reference column.
 *
 * <p>Identity's own tables predate this and keep their raw {@code UUID} attributes; the converter
 * applies to the columns in <i>other</i> contexts that point at an account, which is where the
 * distinction between a user id and any other id actually gets confused.
 */
@Converter(autoApply = true)
public class UserIdConverter implements AttributeConverter<UserId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(UserId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public UserId convertToEntityAttribute(UUID column) {
        return column == null ? null : UserId.of(column);
    }
}
