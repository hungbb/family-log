package app.giaky.lineage.application;

import app.giaky.common.types.PartialDate;
import app.giaky.lineage.domain.Parentage;
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.Union;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Before/after JSON for the audit trail (B4.14, doc 03 §3.13).
 *
 * <p>An audit entry that says "somebody updated a person" answers nothing a family actually asks.
 * What they ask is what the record used to say, which is why every lineage mutation carries both
 * states. Storing them as {@code JSONB} rather than as a column-by-column diff keeps the entry
 * readable years later, when the columns have moved on.
 *
 * <p><b>Hand-built maps, not a serialization of the aggregate.</b> Two reasons, and both bite
 * eventually: a reflective dump would silently start recording any field added later — including
 * {@code linkedUserId}, which ties a person to an account and does not belong in a log an
 * administrator reads — and it would change shape whenever the record did, so entries written a year
 * apart would not compare. This list is the contract.
 *
 * <p>Failures are swallowed and logged. An audit entry is worth having and is never worth failing a
 * family's edit over; the entry still gets written, with a null body.
 */
@Component
public class LineageSnapshots {

    private static final Logger log = LoggerFactory.getLogger(LineageSnapshots.class);

    private final ObjectMapper objectMapper;

    public LineageSnapshots(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** A person's recorded state, as the audit view shows it. Null for "there was none". */
    public String of(Person person) {
        if (person == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", person.id().value());
        snapshot.put("branchId", person.branchId() == null ? null : person.branchId().value());
        snapshot.put("fullName", person.fullName());
        snapshot.put("familyName", person.name().familyName());
        snapshot.put("givenName", person.name().givenName());
        snapshot.put("alias", person.name().alias());
        snapshot.put("posthumousName", person.name().posthumousName());
        snapshot.put("gender", person.gender().name());
        snapshot.put("lifeStatus", person.lifeStatus().name());
        snapshot.put("birth", date(person.birth()));
        snapshot.put("death", date(person.death()));
        snapshot.put("burialPlace", person.burialPlace());
        snapshot.put("generation", person.generation());
        snapshot.put("birthOrder", person.birthOrder());
        snapshot.put("foundingAncestor", person.foundingAncestor());
        // The plain projection, not the HTML: an audit reader wants to see what changed, and markup
        // noise in a diff hides it.
        snapshot.put("notes", person.notesText());
        snapshot.put("deleted", person.isDeleted());
        snapshot.put("version", person.version());
        return write(snapshot);
    }

    /** A parent-child edge. */
    public String of(Parentage parentage) {
        if (parentage == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", parentage.id());
        snapshot.put("parentPersonId", parentage.parentPersonId().value());
        snapshot.put("childPersonId", parentage.childPersonId().value());
        snapshot.put("parentRole", parentage.parentRole().name());
        snapshot.put("nature", parentage.nature().name());
        snapshot.put("unionId", parentage.unionId());
        snapshot.put("removed", parentage.isDeleted());
        return write(snapshot);
    }

    /** A marriage. */
    public String of(Union union) {
        if (union == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", union.id());
        snapshot.put("partnerAId", union.partnerAId().value());
        snapshot.put("partnerBId", union.partnerBId().value());
        snapshot.put("unionType", union.unionType().name());
        snapshot.put("status", union.status().name());
        snapshot.put("startDate", date(union.startDate()));
        snapshot.put("endDate", date(union.endDate()));
        snapshot.put("notes", union.notes());
        snapshot.put("removed", union.isDeleted());
        return write(snapshot);
    }

    /** A partial date, flattened the way the columns store it. Null when nothing is known. */
    private static Map<String, Object> date(PartialDate value) {
        if (value == null || !value.isKnown()) {
            return null;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("year", value.year());
        fields.put("month", value.month());
        fields.put("day", value.day());
        fields.put("precision", value.precision().name());
        fields.put("circa", value.circa());
        fields.put("rangeEndYear", value.rangeEndYear());
        fields.put("lunarText", value.lunarText());
        return fields;
    }

    private String write(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException failed) {
            log.warn("Could not serialize an audit snapshot; the entry will carry no body", failed);
            return null;
        }
    }
}
