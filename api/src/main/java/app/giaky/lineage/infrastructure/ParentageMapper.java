package app.giaky.lineage.infrastructure;

import app.giaky.lineage.domain.Parentage;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.ParentageNature;
import org.springframework.stereotype.Component;

/**
 * {@link Parentage} ↔ {@link ParentageJpaEntity}.
 *
 * <p>Hand-written for the same reason {@link PersonMapper} is: the lineage tables trade enums as
 * strings and ids as wrappers, and one readable method per direction beats a generated one whose
 * omissions are invisible.
 */
@Component
public class ParentageMapper {

    ParentageJpaEntity toEntity(Parentage parentage) {
        ParentageJpaEntity entity = new ParentageJpaEntity();
        entity.setId(parentage.id());
        entity.setClanId(parentage.clanId());
        entity.setParentPersonId(parentage.parentPersonId());
        entity.setChildPersonId(parentage.childPersonId());
        entity.setParentRole(parentage.parentRole().name());
        entity.setNature(parentage.nature().name());
        entity.setUnionId(parentage.unionId());
        entity.setCreatedBy(parentage.createdBy());
        entity.setUpdatedBy(parentage.updatedBy());
        entity.setCreatedAt(parentage.createdAt());
        entity.setUpdatedAt(parentage.updatedAt());
        entity.setDeletedAt(parentage.deletedAt());
        return entity;
    }

    Parentage toDomain(ParentageJpaEntity entity) {
        return new Parentage(
                entity.getId(),
                entity.getClanId(),
                entity.getParentPersonId(),
                entity.getChildPersonId(),
                ParentRole.valueOf(entity.getParentRole()),
                ParentageNature.valueOf(entity.getNature()),
                entity.getUnionId(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }
}
