import { z } from 'zod';

/**
 * Client-side validation for the auth forms.
 *
 * **These are a courtesy, not a rule.** The server validates everything again and its answer is the
 * one that counts — this exists so a person learns their password is too short before a round trip,
 * not so the API can trust the client. Where the two disagree, the server wins and the form shows
 * what it said (see `applyApiError`).
 *
 * The messages are i18n keys rather than text: `zod` produces the key, the form renders it through
 * `t()`. A literal here would be a hard-coded user-facing string, which doc 09 §3.2 forbids and
 * ESLint flags.
 *
 * **Every `z.string()` names its own "required" key.** An AntD field nobody has typed in is
 * `undefined`, not `''`, and Zod refuses `undefined` as the wrong *type* before `.min(1)` is ever
 * consulted. Without a key on the type check, the message for the commonest mistake there is —
 * pressing the button on an empty form — was Zod's own "Invalid input: expected string, received
 * undefined", in English, under every field.
 */

/** Matches `PasswordPolicy.MIN_LENGTH` on the backend, which is FR-A2's floor. */
export const PASSWORD_MIN_LENGTH = 8;

/** Matches `PasswordPolicy.MAX_LENGTH` — BCrypt ignores anything past 72 bytes. */
export const PASSWORD_MAX_LENGTH = 72;

/**
 * Deliberately loose, and the same shape the backend's `EmailAddress` accepts.
 *
 * Strict email validation is a well-known way to reject addresses that work. The only address that
 * has really been verified is one that received a message, which is what the verification token is
 * for; this rejects what is certainly wrong and leaves the rest to delivery.
 */
const EMAIL_SHAPE = /^[^@\s]+@[^@\s.]+(\.[^@\s.]+)+$/;

const email = z
  .string({ error: 'auth.validation.emailRequired' })
  .trim()
  // `abort`: an empty address is missing, not also malformed — one message, not two.
  .min(1, { message: 'auth.validation.emailRequired', abort: true })
  .max(320, { message: 'auth.validation.emailTooLong' })
  .regex(EMAIL_SHAPE, { message: 'auth.validation.emailInvalid' });

const password = z
  .string({ error: 'auth.validation.passwordRequired' })
  .min(PASSWORD_MIN_LENGTH, { message: 'auth.validation.passwordTooShort' })
  .max(PASSWORD_MAX_LENGTH, { message: 'auth.validation.passwordTooLong' });

const displayName = z
  .string({ error: 'auth.validation.displayNameRequired' })
  .trim()
  .min(1, { message: 'auth.validation.displayNameRequired' })
  .max(150, { message: 'auth.validation.displayNameTooLong' });

export const loginSchema = z.object({
  email,
  // Not `password`: an existing account may predate a policy change, and refusing to *submit* a
  // password the server would accept is a way to lock people out of their own accounts.
  password: z
    .string({ error: 'auth.validation.passwordRequired' })
    .min(1, { message: 'auth.validation.passwordRequired' }),
});

export const registerSchema = z.object({
  email,
  password,
  displayName,
});

export const forgotPasswordSchema = z.object({ email });

export const resetPasswordSchema = z
  .object({
    password,
    // An empty confirmation is a confirmation that does not match, and says so.
    confirmPassword: z.string({ error: 'auth.validation.passwordsDoNotMatch' }),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: 'auth.validation.passwordsDoNotMatch',
    path: ['confirmPassword'],
  });

export type LoginValues = z.infer<typeof loginSchema>;
export type RegisterValues = z.infer<typeof registerSchema>;
export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>;
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;
