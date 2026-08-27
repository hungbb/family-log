package app.giaky.clanorg.application;

/**
 * Ask to join, by code (FR-J1).
 *
 * <p>Exactly one of the two codes is given. A clan code asks to join the family; a branch code asks
 * to join a particular <i>chi</i>, which is the case worth supporting — it turns the approver's job
 * from "where do they belong?" into "yes or no".
 */
public record SubmitJoinRequestCommand(String clanCode, String branchCode, String message) {

    /** Whether the caller supplied exactly one code, which is the only shape that means anything. */
    public boolean hasExactlyOneCode() {
        return blank(clanCode) != blank(branchCode);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
