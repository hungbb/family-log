import { type FormInstance } from 'antd';
import { describe, expect, it } from 'vitest';

import { validatedValues } from './forms';

interface Values {
  name: string;
}

/** A form whose `validateFields` settles the way the test says. */
function formThat(outcome: () => Promise<Values>): FormInstance<Values> {
  return { validateFields: outcome } as unknown as FormInstance<Values>;
}

describe('validatedValues', () => {
  it('returns the values of a form that passed its rules', async () => {
    const form = formThat(() => Promise.resolve({ name: 'Chi Hai' }));

    await expect(validatedValues(form)).resolves.toEqual({ name: 'Chi Hai' });
  });

  it('returns null, rather than rejecting, when a field refused its value', async () => {
    // The shape AntD rejects with. Not an Error, and not something to report: the form has
    // already shown "Hãy nhập tên" under the field.
    const refusal = {
      values: { name: '' },
      errorFields: [{ name: ['name'], errors: ['Hãy nhập tên chi.'] }],
      outOfDate: false,
    };
    // eslint-disable-next-line @typescript-eslint/prefer-promise-reject-errors -- AntD's own shape
    const form = formThat(() => Promise.reject(refusal));

    await expect(validatedValues(form)).resolves.toBeNull();
  });

  it('still lets a real failure through', async () => {
    const form = formThat(() => Promise.reject(new TypeError('form is not mounted')));

    await expect(validatedValues(form)).rejects.toThrow('form is not mounted');
  });

  it('does not mistake some other rejected object for a validation result', async () => {
    // eslint-disable-next-line @typescript-eslint/prefer-promise-reject-errors -- deliberately odd
    const form = formThat(() => Promise.reject({ errorFields: 'nope' }));

    await expect(validatedValues(form)).rejects.toEqual({ errorFields: 'nope' });
  });
});
