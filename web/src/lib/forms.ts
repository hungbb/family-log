import type { FormInstance } from 'antd';

/**
 * The values of a form that passed its own rules, or null when a field refused them.
 *
 * AntD's `validateFields()` *rejects* when validation fails — by design, and with a plain object
 * (`{ values, errorFields, outOfDate }`) rather than an `Error`. By then the form has already put
 * each message under its field, so there is nothing left for the caller to do but stop. Awaiting it
 * bare inside a handler invoked as `void onSubmit()` let that rejection escape: the dialog looked
 * right and the console logged "Uncaught (in promise)" every time somebody pressed OK on an
 * incomplete form.
 *
 * Only the validation rejection is absorbed. Anything else that goes wrong in there is a bug, and
 * still surfaces as one.
 */
export async function validatedValues<Values>(form: FormInstance<Values>): Promise<Values | null> {
  try {
    return await form.validateFields();
  } catch (failure) {
    if (isValidationFailure(failure)) {
      return null;
    }
    throw failure;
  }
}

/** Whether a rejection is AntD saying "the fields are not valid", as opposed to something breaking. */
function isValidationFailure(failure: unknown): boolean {
  return (
    typeof failure === 'object' &&
    failure !== null &&
    'errorFields' in failure &&
    Array.isArray(failure.errorFields)
  );
}
