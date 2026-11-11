import { type FormInstance } from 'antd';

/**
 * An AntD form reduced to the one method the validation helpers call, recording what it was given.
 *
 * `validateWithSchema` and `applyApiError` only ever report failures through `setFields`, so a test
 * of what a person would see under each field needs nothing else — and certainly not a rendered
 * form.
 */
export function recordingForm(): { form: FormInstance; placed: Record<string, string[]> } {
  const placed: Record<string, string[]> = {};
  const form = {
    setFields(fields: { name: string; errors: string[] }[]) {
      for (const field of fields) placed[field.name] = field.errors;
    },
  } as unknown as FormInstance;
  return { form, placed };
}
