package app.giaky.lineage.infrastructure;

import app.giaky.lineage.domain.Union;
import app.giaky.lineage.domain.UnionStatus;
import app.giaky.lineage.domain.UnionType;
import org.springframework.stereotype.Component;

/** {@link Union} ↔ {@link UnionJpaEntity}. Hand-written; see {@link PersonMapper}. */
@Component
public class UnionMapper {

    UnionJpaEntity toEntity(Union union) {
        UnionJpaEntity entity = new UnionJpaEntity();
        entity.setId(union.id());
        entity.setClanId(union.clanId());
        entity.setPartnerAId(union.partnerAId());
        entity.setPartnerBId(union.partnerBId());
        entity.setUnionType(union.unionType().name());
        entity.setStatus(union.status().name());
        entity.setStartDate(PartialDateEmbeddable.of(union.startDate()));
        entity.setEndDate(PartialDateEmbeddable.of(union.endDate()));
        entity.setNotes(union.notes());
        entity.setCreatedBy(union.createdBy());
        entity.setUpdatedBy(union.updatedBy());
        entity.setCreatedAt(union.createdAt());
        entity.setUpdatedAt(union.updatedAt());
        entity.setDeletedAt(union.deletedAt());
        return entity;
    }

    Union toDomain(UnionJpaEntity entity) {
        return new Union(
                entity.getId(),
                entity.getClanId(),
                entity.getPartnerAId(),
                entity.getPartnerBId(),
                UnionType.valueOf(entity.getUnionType()),
                UnionStatus.valueOf(entity.getStatus()),
                entity.getStartDate() == null ? null : entity.getStartDate().toDomain(),
                entity.getEndDate() == null ? null : entity.getEndDate().toDomain(),
                entity.getNotes(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }
}
