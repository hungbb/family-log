package app.giaky.clanorg.application;

/**
 * Found a clan (FR-C1).
 *
 * @param name the clan as it is displayed, diacritics and all: <i>Nguyễn Văn</i>
 * @param surname the family name the join code is built from; diacritics are stripped, so
 *     <i>Nguyễn</i> becomes the {@code NGUYEN} in {@code NGUYEN-7F3K}. Optional — the clan name is
 *     used when it is absent, which is right far more often than not
 * @param originPlace <i>quê quán</i>
 */
public record CreateClanCommand(String name, String surname, String originPlace, String description) {

    /** The surname the code should be built from: the explicit one, or the clan name. */
    public String codeSource() {
        return surname == null || surname.isBlank() ? name : surname;
    }
}
