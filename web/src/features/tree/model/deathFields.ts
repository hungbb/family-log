import { type LifeStatus, type PartialDate } from '@/api/endpoints/persons.ts';

/** What the person form holds for the fields that only a death has. */
export interface DeathFieldValues {
  lifeStatus?: LifeStatus | undefined;
  death?: PartialDate | undefined;
  burialPlace?: string | undefined;
  posthumousName?: string | undefined;
}

/** The part of a create or update body those fields contribute. */
export interface DeathFieldsInput {
  death?: PartialDate;
  burialPlace?: string;
  posthumousName?: string;
}

/**
 * What to send for the date of death, burial place and posthumous name (F4.2, F4.3).
 *
 * The form shows those fields only while the status is "deceased", and keeps what was typed into
 * them while they are hidden, so a mis-click on the status loses nothing. What gets sent follows the
 * status, not the form's store:
 * - **deceased** — whatever the fields hold.
 * - **living** — explicitly cleared. The server refuses a living person with a date of death (I-P3),
 *   and a `PATCH` that merely omitted it would leave the old one in place and be refused.
 * - **unknown** — omitted, so an edit leaves whatever the record already holds alone. Hiding a
 *   field is not a reason to erase it.
 */
export function deathFields(values: DeathFieldValues): DeathFieldsInput {
  if (values.lifeStatus === 'DECEASED') {
    return {
      ...(values.death === undefined ? {} : { death: values.death }),
      ...(values.burialPlace === undefined ? {} : { burialPlace: values.burialPlace }),
      ...(values.posthumousName === undefined ? {} : { posthumousName: values.posthumousName }),
    };
  }
  if (values.lifeStatus === 'ALIVE') {
    // An `UNKNOWN` date with nothing else in it is how the API spells "no date" — the server stores
    // it as absent. Empty strings are trimmed to null the same way.
    return { death: { precision: 'UNKNOWN', circa: false }, burialPlace: '', posthumousName: '' };
  }
  return {};
}
