import { describe, expect, it } from 'vitest';
import { type ZodType } from 'zod';

import { validateWithSchema } from '@/lib/apiErrors.ts';
import { recordingForm } from '@/test/recordingForm.ts';
import { translator } from '@/test/translator.ts';

import {
  PASSWORD_MIN_LENGTH,
  forgotPasswordSchema,
  loginSchema,
  registerSchema,
  resetPasswordSchema,
} from './schemas';

const t = translator('vi');

/** The message keys a schema reports, by field. */
function messages(schema: ZodType, values: unknown): Record<string, string[]> {
  const result = schema.safeParse(values);
  if (result.success) return {};
  const byField: Record<string, string[]> = {};
  for (const issue of result.error.issues) {
    const field = String(issue.path[0] ?? 'form');
    (byField[field] ??= []).push(issue.message);
  }
  return byField;
}

/** Whether a string is a key the Vietnamese locale actually defines. */
function isTranslated(key: string): boolean {
  return t(key, { count: 1, defaultValue: '\u0000' }) !== '\u0000';
}

describe('the auth schemas on an untouched form', () => {
  // What AntD hands over when the button is pressed with nothing typed: no keys at all.
  const untouched = {};

  it('ask for each field by name instead of reporting a type error', () => {
    expect(messages(registerSchema, untouched)).toEqual({
      email: ['auth.validation.emailRequired'],
      password: ['auth.validation.passwordRequired'],
      displayName: ['auth.validation.displayNameRequired'],
    });
    expect(messages(loginSchema, untouched)).toEqual({
      email: ['auth.validation.emailRequired'],
      password: ['auth.validation.passwordRequired'],
    });
    expect(messages(forgotPasswordSchema, untouched)).toEqual({
      email: ['auth.validation.emailRequired'],
    });
    expect(messages(resetPasswordSchema, untouched)).toEqual({
      password: ['auth.validation.passwordRequired'],
      confirmPassword: ['auth.validation.passwordsDoNotMatch'],
    });
  });

  it('reach the form as sentences in the interface language', () => {
    const { form, placed } = recordingForm();

    validateWithSchema(registerSchema, untouched, form, t);

    expect(placed).toEqual({
      email: ['Hãy nhập email.'],
      password: ['Hãy nhập mật khẩu.'],
      displayName: ['Hãy nhập tên hiển thị.'],
    });
  });

  it('never produce a message the locale does not define', () => {
    const awkward: unknown[] = [
      {},
      { email: null, password: null, displayName: null, confirmPassword: null },
      { email: 42, password: 42, displayName: 42, confirmPassword: 42 },
      { email: '   ', password: '', displayName: '   ', confirmPassword: '' },
      { email: 'not-an-address', password: 'short', displayName: 'x'.repeat(200) },
    ];

    for (const schema of [registerSchema, loginSchema, forgotPasswordSchema, resetPasswordSchema]) {
      for (const values of awkward) {
        for (const reported of Object.values(messages(schema, values)).flat()) {
          expect(isTranslated(reported), `"${reported}" is not a message key`).toBe(true);
        }
      }
    }
  });
});

describe('the auth schemas on a filled form', () => {
  it('treat whitespace as nothing typed', () => {
    expect(messages(forgotPasswordSchema, { email: '   ' })).toEqual({
      email: ['auth.validation.emailRequired'],
    });
  });

  it('accept a plausible registration, trimmed', () => {
    const parsed = registerSchema.safeParse({
      email: '  be@giaky.test ',
      password: 'x'.repeat(PASSWORD_MIN_LENGTH),
      displayName: ' Nguyễn Thị Bé ',
    });

    expect(parsed.success).toBe(true);
    expect(parsed.data).toMatchObject({ email: 'be@giaky.test', displayName: 'Nguyễn Thị Bé' });
  });

  it('do not hold an existing password to the current policy at sign-in', () => {
    expect(messages(loginSchema, { email: 'be@giaky.test', password: 'old' })).toEqual({});
  });

  it('refuse a confirmation that differs, on the confirmation field', () => {
    expect(
      messages(resetPasswordSchema, {
        password: 'x'.repeat(PASSWORD_MIN_LENGTH),
        confirmPassword: 'y'.repeat(PASSWORD_MIN_LENGTH),
      }),
    ).toEqual({ confirmPassword: ['auth.validation.passwordsDoNotMatch'] });
  });
});
