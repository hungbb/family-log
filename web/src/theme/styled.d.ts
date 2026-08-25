import type { Tokens } from './tokens';

/**
 * Teaches styled-components what `props.theme` is.
 *
 * Without this augmentation every `props.theme.color.ink` is `any`, and the whole point of putting
 * the tokens in a typed object is lost: a typo becomes a silent `undefined` in the stylesheet
 * instead of a compile error.
 */
declare module 'styled-components' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  export interface DefaultTheme extends Tokens {}
}
