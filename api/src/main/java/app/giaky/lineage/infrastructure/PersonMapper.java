package app.giaky.lineage.infrastructure;

import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.LifeStatus;
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.PersonName;
import org.springframework.stereotype.Component;

/**
 * {@link Person} ↔ {@link PersonJpaEntity}.
 *
 * <p>Hand-written rather than MapStruct, which every other context here uses. The reason is the
 * partial dates: two value objects flatten onto fourteen columns and come back again, and expressing
 * that as {@code @Mapping} expressions produces a mapper nobody can read and a generated class that
 * silently drops a field the day one is added. The domain constructors do the validating on the way
 * back, so a hand-written mapper is not a hand-written invariant — it is only the plumbing.
 *
 * <p>Enums cross as strings, matching the {@code CHECK} constraints rather than a native PostgreSQL
 * enum (doc 03 §1).
 */
@Component
public class PersonMapper {

    PersonJpaEntity toEntity(Person person) {
        PersonJpaEntity entity = new PersonJpaEntity();
        entity.setId(person.id().value());
        entity.setClanId(person.clanId());
        entity.setBranchId(person.branchId());
        entity.setFullName(person.name().fullName());
        entity.setFamilyName(person.name().familyName());
        entity.setGivenName(person.name().givenName());
        entity.setAlias(person.name().alias());
        entity.setPosthumousName(person.name().posthumousName());
        entity.setGender(person.gender().name());
        entity.setLifeStatus(person.lifeStatus().name());
        entity.setBirth(PartialDateEmbeddable.of(person.birth()));
        entity.setDeath(PartialDateEmbeddable.of(person.death()));
        entity.setBurialPlace(person.burialPlace());
        entity.setGeneration(shortOf(person.generation()));
        entity.setBirthOrder(shortOf(person.birthOrder()));
        entity.setAvatarMediaId(person.avatarMediaId());
        entity.setNotesHtml(person.notesHtml());
        entity.setNotesText(person.notesText());
        entity.setLinkedUserId(person.linkedUserId());
        entity.setFoundingAncestor(person.foundingAncestor());
        entity.setVersion(person.version());
        entity.setCreatedBy(person.createdBy());
        entity.setUpdatedBy(person.updatedBy());
        entity.setCreatedAt(person.createdAt());
        entity.setUpdatedAt(person.updatedAt());
        entity.setDeletedAt(person.deletedAt());
        return entity;
    }

    Person toDomain(PersonJpaEntity entity) {
        return new Person(
                PersonId.of(entity.getId()),
                entity.getClanId(),
                entity.getBranchId(),
                new PersonName(
                        entity.getFullName(),
                        entity.getFamilyName(),
                        entity.getGivenName(),
                        entity.getAlias(),
                        entity.getPosthumousName()),
                Gender.valueOf(entity.getGender()),
                LifeStatus.valueOf(entity.getLifeStatus()),
                entity.getBirth() == null ? null : entity.getBirth().toDomain(),
                entity.getDeath() == null ? null : entity.getDeath().toDomain(),
                entity.getBurialPlace(),
                intOf(entity.getGeneration()),
                intOf(entity.getBirthOrder()),
                entity.getAvatarMediaId(),
                entity.getNotesHtml(),
                entity.getNotesText(),
                entity.getLinkedUserId(),
                entity.isFoundingAncestor(),
                entity.getVersion(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    private static Short shortOf(Integer value) {
        return value == null ? null : value.shortValue();
    }

    private static Integer intOf(Short value) {
        return value == null ? null : value.intValue();
    }
}
