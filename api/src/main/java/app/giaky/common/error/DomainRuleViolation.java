package app.giaky.common.error;

import java.util.List;

/**
 * 422 — syntactically valid, but it would break an invariant: a cycle in the lineage, a death before
 * a birth, a relationship the domain forbids.
 */
public non-sealed class DomainRuleViolation extends ApiException {

    public DomainRuleViolation(ErrorCode code, String detail) {
        super(code, detail);
    }

    public DomainRuleViolation(ErrorCode code, String detail, List<FieldViolation> errors) {
        super(code, detail, errors);
    }
}
