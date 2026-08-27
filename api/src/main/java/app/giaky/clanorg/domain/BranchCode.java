package app.giaky.clanorg.domain;

/**
 * A branch's shareable join code, {@code CHIHAI-9Q2M}.
 *
 * <p>Same shape as {@link ClanCode} and issued at the same time, though nothing consumes it until
 * Phase 3 — which is the point of issuing it now (phase 2 doc §8). Branch-scoped joining needs a
 * code on every branch; minting them here means Phase 3 is a feature rather than a backfill.
 *
 * <p>What it buys the product: sharing a branch's code lets a relative join straight into the right
 * <i>chi</i>, so the approver's job becomes "yes or no" rather than "yes, and now where do they go".
 */
public record BranchCode(String value) {

    public BranchCode {
        value = CodeFormat.normalize(value, "branch code");
    }

    public static BranchCode of(String raw) {
        return new BranchCode(raw);
    }

    public static boolean isValid(String raw) {
        return CodeFormat.isValid(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
