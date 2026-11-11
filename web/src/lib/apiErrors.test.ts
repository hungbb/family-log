import { describe, expect, it } from 'vitest';
import { z } from 'zod';

import { recordingForm } from '@/test/recordingForm.ts';
import { translator } from '@/test/translator.ts';

import { validateWithSchema } from './apiErrors';

const t = translator('vi');

/** A schema shaped like the application's: every rule produces an i18n key. */
const keyed = z.object({
  email: z
    .string({ error: 'auth.validation.emailRequired' })
    .min(1, { message: 'auth.validation.emailRequired' }),
  password: z
    .string({ error: 'auth.validation.passwordRequired' })
    .min(8, { message: 'auth.validation.passwordTooShort' }),
});

describe('validateWithSchema', () => {
  it('translates each key and puts it on the field it belongs to', () => {
    const { form, placed } = recordingForm();

    const parsed = validateWithSchema(keyed, {}, form, t);

    expect(parsed).toBeNull();
    expect(placed).toEqual({
      email: ['Hãy nhập email.'],
      password: ['Hãy nhập mật khẩu.'],
    });
  });

  it('fills the bound of a length rule from the schema', () => {
    const { form, placed } = recordingForm();

    validateWithSchema(keyed, { email: 'be@giaky.test', password: 'ngắn' }, form, t);

    expect(placed).toEqual({ password: ['Mật khẩu phải có ít nhất 8 ký tự.'] });
  });

  it("falls back to a translated sentence when a rule has no key, never to Zod's own text", () => {
    const { form, placed } = recordingForm();
    const unkeyed = z.object({ code: z.string() });

    validateWithSchema(unkeyed, {}, form, t);

    expect(placed).toEqual({ code: [t('errors.validationFailed')] });
  });

  it('returns the parsed values and touches nothing when the form is valid', () => {
    const { form, placed } = recordingForm();

    const parsed = validateWithSchema(
      keyed,
      { email: 'be@giaky.test', password: 'mật-khẩu-đủ-dài' },
      form,
      t,
    );

    expect(parsed).toEqual({ email: 'be@giaky.test', password: 'mật-khẩu-đủ-dài' });
    expect(placed).toEqual({});
  });
});
