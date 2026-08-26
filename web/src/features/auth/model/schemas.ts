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
 */

/** Matches `PasswordPolicy.MIN_LENGTH` on the backend. */
export const PASSWORD_MIN_LENGTH = 10;

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
  .string()
  .trim()
  .min(1, { message: 'auth.validation.emailRequired' })
  .max(320, { message: 'auth.validation.emailTooLong' })
  .regex(EMAIL_SHAPE, { message: 'auth.validation.emailInvalid' });

const password = z
  .string()
  .min(PASSWORD_MIN_LENGTH, { message: 'auth.validation.passwordTooShort' })
  .max(PASSWORD_MAX_LENGTH, { message: 'auth.validation.passwordTooLong' });

const displayName = z
  .string()
  .trim()
  .min(1, { message: 'auth.validation.displayNameRequired' })
  .max(150, { message: 'auth.validation.displayNameTooLong' });

export const loginSchema = z.object({
  email,
  // Not `password`: an existing account may predate a policy change, and refusing to *submit* a
  // password the server would accept is a way to lock people out of their own accounts.
  password: z.string().min(1, { message: 'auth.validation.passwordRequired' }),
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
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: 'auth.validation.passwordsDoNotMatch',
    path: ['confirmPassword'],
  });

export type LoginValues = z.infer<typeof loginSchema>;
export type RegisterValues = z.infer<typeof registerSchema>;
export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>;
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;
