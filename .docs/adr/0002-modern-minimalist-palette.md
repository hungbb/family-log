# ADR 0002 — A modern minimalist palette, not the lacquer-red heritage palette

- **Status:** Accepted
- **Date:** 2026-08-25
- **Deciders:** hungbb
- **Affects:** [07-frontend-architecture.md](../07-frontend-architecture.md) §3.1, §3.3; [phases/phase-0-foundation.md](../phases/phase-0-foundation.md) F0.3

## Context

Two documents disagree about how the product should look.

`07-frontend-architecture.md` §3.1 specifies a heritage palette — lacquer red `#8B1E1E`, warm
paper-toned surfaces `#FFFDF9` / `#F5EFE6` — and §3.3 describes the intent as deliberately avoiding
"a generic SaaS look".

The Phase 0 issue ([#2](https://github.com/hungbb/family-log/issues/2)) says something different, and
says it twice: task F0.3 asks for "the modern minimalist palette from doc 07 §3", and the exit
criteria read "the app has a modern, minimalist look, not stock Ant Design". The reference to doc 07
§3 is a pointer to the section, but the adjective it uses is not the one that section carries.

The issue is the later statement of intent and is internally consistent, so it wins on the question
of register. Doc 07 §3 remains right about everything else in that section: the token *structure*
(one `tokens.ts`, both AntD and styled-components reading from it, no hard-coded values in
components) is unchanged, and so is the underlying goal — the UI must not look like stock Ant Design.

## Decision

`web/src/theme/tokens.ts` uses a modern minimalist palette: near-white surfaces, a narrow neutral
ramp, hairline borders, restrained shadows, and a single accent used sparingly.

The accent is a muted clay `#A6503F` rather than a saturated lacquer red. It keeps the heritage
warmth doc 07 §3.3 asks for, in a lower, more contemporary register — and it is nobody's idea of Ant
Design blue, which is the constraint both documents share.

The token keys are exactly those of doc 07 §3.1 (`color`, `space`, `radius`, `font`, `shadow`, `z`),
extended with `fontSize`, `lineHeight`, `fontWeight`, `motion` and `layout`. Every value is a role,
never a literal, so a second token set can be swapped through `ThemeProvider` for dark mode later.

## Alternatives Considered

| Option | Pros | Cons | Why not |
|--------|------|------|---------|
| Implement doc 07 §3.1 verbatim | Matches the written palette exactly | Contradicts the issue's explicit "modern minimalist" twice over | The issue is the more recent and more specific instruction |
| Pure neutral minimalism, near-black accent | Cleanest "modern" reading; very current | Black primary buttons make links black too; drops the heritage warmth §3.3 argues for | Loses something the product actually wants, for a look that is itself a trend |
| Ask before building | No ambiguity | Blocks a whole phase on a colour | The token structure is what matters; the palette is one file and one ADR to change |

## Consequences

**Positive** — the theme satisfies the issue's exit criterion and keeps §3.3's intent. Because
nothing outside `tokens.ts` and `antdTheme.ts` names a colour, revisiting this is a single-file edit
plus a screenshot review.

**Negative** — `07-frontend-architecture.md` §3.1 now shows hex values the code does not use. Anyone
reading that section for the palette will be wrong until it is updated.

**Follow-up**

- Update `07-frontend-architecture.md` §3.1 and §3.3 to the shipped palette, or supersede this ADR if
  the heritage direction is reaffirmed.
- The tree visualizer (Phase 5) is the real test of the palette: gender rails, the muted treatment
  for deceased persons and the generation rails all need to read clearly against near-white surfaces.
  Revisit `color.male`, `color.female` and `color.deceased` with a real tree on screen.
