import { App as AntApp, Form, Input, InputNumber, Modal, Radio, Select } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';

import { branchSelectOptions } from '@/api/endpoints/branches.ts';
import {
  type Gender,
  type LifeStatus,
  type PartialDate,
  type PersonDetail,
  type PlacementType,
} from '@/api/endpoints/persons.ts';
import { hasCode } from '@/api/errors.ts';
import { toMessage } from '@/lib/apiErrors.ts';
import { validatedValues } from '@/lib/forms.ts';

import { useBranches } from '../../branch';
import { useCreatePerson, useUpdatePerson } from '../hooks/useTree';
import { GENDERS } from '../model/visual';

import { PartialDateInput } from './PartialDateInput';
import { PersonPicker } from './PersonPicker';

/** What the form is doing: adding somebody, or correcting an existing record. */
export type PersonFormMode =
  | { kind: 'create'; placementType: PlacementType; anchorPersonId?: string }
  | { kind: 'edit'; person: PersonDetail };

interface PersonFormModalProps {
  clanId: string;
  open: boolean;
  mode: PersonFormMode;
  onClose: () => void;
  onSaved?: (personId: string) => void;
}

interface PersonFormValues {
  fullName: string;
  familyName?: string;
  givenName?: string;
  alias?: string;
  posthumousName?: string;
  gender?: Gender;
  lifeStatus?: LifeStatus;
  birth?: PartialDate;
  death?: PartialDate;
  burialPlace?: string;
  birthOrder?: number;
  notesHtml?: string;
  anchorPersonId?: string;
  branchId?: string;
}

/**
 * Adding and editing a person (F4.2, F4.3).
 *
 * **One form for both**, because the fields are the same and the differences are two: a create
 * carries a placement, and an edit carries a version. Splitting them would mean maintaining the
 * partial-date wiring twice.
 *
 * **The version conflict is the interesting case** (F4.3). When somebody else has changed the record
 * since it was loaded, the server answers `409 VERSION_CONFLICT` and this offers to reload rather
 * than to retry — retrying is exactly the overwrite the version exists to prevent, and the person in
 * front of the screen is the only one who can decide whether their edit still applies.
 */
export function PersonFormModal({ clanId, open, mode, onClose, onSaved }: PersonFormModalProps) {
  const { t } = useTranslation();
  const { message, modal } = AntApp.useApp();
  const [form] = Form.useForm<PersonFormValues>();

  const createPerson = useCreatePerson(clanId);
  const updatePerson = useUpdatePerson(clanId);
  const { data: branches } = useBranches(clanId);

  const isCreate = mode.kind === 'create';
  const needsAnchor =
    isCreate && (mode.placementType === 'CHILD_OF_PARENT' || mode.placementType === 'SPOUSE_OF');
  const needsBranch = isCreate && mode.placementType === 'BRANCH_ROOT';

  useEffect(() => {
    if (!open) return;
    if (mode.kind === 'edit') {
      form.setFieldsValue({
        fullName: mode.person.fullName,
        ...pick('familyName', mode.person.familyName),
        ...pick('givenName', mode.person.givenName),
        ...pick('alias', mode.person.alias),
        ...pick('posthumousName', mode.person.posthumousName),
        gender: mode.person.gender,
        lifeStatus: mode.person.lifeStatus,
        ...pick('birth', mode.person.birth),
        ...pick('death', mode.person.death),
        ...pick('burialPlace', mode.person.burialPlace),
        ...pick('birthOrder', mode.person.birthOrder),
        ...pick('notesHtml', mode.person.notesHtml),
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        lifeStatus: 'UNKNOWN',
        ...(mode.anchorPersonId === undefined ? {} : { anchorPersonId: mode.anchorPersonId }),
      });
    }
  }, [open, mode, form]);

  const onSubmit = async () => {
    const values = await validatedValues(form);
    if (values === null) return;
    try {
      if (mode.kind === 'create') {
        const created = await createPerson.mutateAsync({
          fullName: values.fullName,
          ...optional(values),
          placement: {
            type: mode.placementType,
            ...(needsAnchor && values.anchorPersonId !== undefined
              ? { personId: values.anchorPersonId }
              : {}),
            ...(needsBranch && values.branchId !== undefined ? { branchId: values.branchId } : {}),
          },
        });
        onSaved?.(created.id);
      } else {
        const updated = await updatePerson.mutateAsync({
          personId: mode.person.id,
          input: { ...optional(values), fullName: values.fullName, version: mode.person.version },
        });
        onSaved?.(updated.id);
      }
      onClose();
    } catch (failure) {
      if (hasCode(failure, 'VERSION_CONFLICT')) {
        // Not an error message: a decision. Somebody else's edit is already saved, and offering a
        // retry here would quietly discard it.
        modal.confirm({
          title: t('tree.conflict.title'),
          content: t('tree.conflict.body'),
          okText: t('tree.conflict.reload'),
          cancelText: t('common.cancel'),
          onOk: () => {
            onClose();
          },
        });
        return;
      }
      void message.error(toMessage(failure, t));
    }
  };

  return (
    <Modal
      open={open}
      title={isCreate ? t('tree.form.createTitle') : t('tree.form.editTitle')}
      okText={t('common.save')}
      cancelText={t('common.cancel')}
      confirmLoading={createPerson.isPending || updatePerson.isPending}
      onOk={() => void onSubmit()}
      onCancel={onClose}
      width={640}
    >
      <Form form={form} layout="vertical" requiredMark={false}>
        {needsAnchor && (
          <Form.Item
            name="anchorPersonId"
            label={
              mode.placementType === 'SPOUSE_OF' ? t('tree.form.spouseOf') : t('tree.form.childOf')
            }
            rules={[{ required: true, message: t('tree.validation.anchorRequired') }]}
          >
            <PersonPicker clanId={clanId} editableOnly />
          </Form.Item>
        )}

        {needsBranch && (
          <Form.Item
            name="branchId"
            label={t('tree.form.branch')}
            rules={[{ required: true, message: t('tree.validation.branchRequired') }]}
          >
            <Select options={branchSelectOptions(branches ?? [])} />
          </Form.Item>
        )}

        <Form.Item
          name="fullName"
          label={t('tree.field.fullName')}
          rules={[{ required: true, message: t('tree.validation.nameRequired') }]}
        >
          <Input maxLength={200} placeholder={t('tree.placeholder.fullName')} />
        </Form.Item>

        <Form.Item name="alias" label={t('tree.field.alias')}>
          <Input maxLength={100} />
        </Form.Item>

        <Form.Item name="posthumousName" label={t('tree.field.posthumousName')}>
          <Input maxLength={100} placeholder={t('tree.placeholder.posthumousName')} />
        </Form.Item>

        <Form.Item
          name="gender"
          label={t('tree.field.gender')}
          rules={[{ required: true, message: t('tree.validation.genderRequired') }]}
        >
          <Radio.Group
            optionType="button"
            options={GENDERS.map((value) => ({ value, label: t(`tree.gender.${value}`) }))}
          />
        </Form.Item>

        <Form.Item name="lifeStatus" label={t('tree.field.lifeStatus')}>
          <Select
            options={(['ALIVE', 'DECEASED', 'UNKNOWN'] as LifeStatus[]).map((value) => ({
              value,
              label: t(`tree.lifeStatus.${value}`),
            }))}
          />
        </Form.Item>

        <Form.Item name="birth" label={t('tree.field.birth')}>
          <PartialDateInput label={t('tree.field.birth')} />
        </Form.Item>

        <Form.Item name="death" label={t('tree.field.death')}>
          <PartialDateInput label={t('tree.field.death')} />
        </Form.Item>

        <Form.Item name="birthOrder" label={t('tree.field.birthOrder')}>
          <InputNumber min={1} max={30} />
        </Form.Item>

        <Form.Item name="burialPlace" label={t('tree.field.burialPlace')}>
          <Input maxLength={255} />
        </Form.Item>

        <Form.Item name="notesHtml" label={t('tree.field.notes')} help={t('tree.field.notesHelp')}>
          <Input.TextArea rows={4} maxLength={4000} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/**
 * Drops the fields the person left blank.
 *
 * `exactOptionalPropertyTypes` is on (doc 09 §4.3), so an explicit `undefined` is not the same as an
 * absent key — and on the wire the difference matters: `PATCH` treats an absent field as "leave it
 * alone", which is what an untouched input means.
 */
function optional(values: PersonFormValues) {
  return {
    ...pick('familyName', values.familyName),
    ...pick('givenName', values.givenName),
    ...pick('alias', values.alias),
    ...pick('posthumousName', values.posthumousName),
    ...pick('gender', values.gender),
    ...pick('lifeStatus', values.lifeStatus),
    ...pick('birth', values.birth),
    ...pick('death', values.death),
    ...pick('burialPlace', values.burialPlace),
    ...pick('birthOrder', values.birthOrder),
    ...pick('notesHtml', values.notesHtml),
  };
}

function pick<K extends string, V>(key: K, value: V | undefined): Partial<Record<K, V>> {
  return value === undefined ? {} : ({ [key]: value } as Record<K, V>);
}
