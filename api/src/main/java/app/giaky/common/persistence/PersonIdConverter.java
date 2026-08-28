package app.giaky.common.persistence;

import app.giaky.common.types.PersonId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/** {@link PersonId} ↔ the {@code UUID} in a person-reference column. */
@Converter(autoApply = true)
public class PersonIdConverter implements AttributeConverter<PersonId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(PersonId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public PersonId convertToEntityAttribute(UUID column) {
        return column == null ? null : PersonId.of(column);
    }
}
