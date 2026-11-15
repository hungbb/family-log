import {
  App as AntApp,
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Select,
  Skeleton,
  Switch,
} from 'antd';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { MailSecurity, MailSettings, MailSettingsInput } from '@/api/endpoints/admin.ts';
import { isApiError } from '@/api/errors.ts';
import { applyApiError, toMessage } from '@/lib/apiErrors.ts';

import { useMailSettings, useSendTestMail, useUpdateMailSettings } from '../hooks/useAdmin';
import { FormActions, FormColumn, Hint, TestRow } from '../pages/styles';

interface FormValues {
  enabled: boolean;
  host?: string;
  port?: number | null;
  username?: string;
  password?: string;
  removePassword: boolean;
  security: MailSecurity;
  fromAddress?: string;
  fromName?: string;
}

/** The port each mode conventionally uses, offered when the mode changes and the port is empty. */
const DEFAULT_PORT: Record<MailSecurity, number> = { NONE: 25, STARTTLS: 587, SSL: 465 };

function toFormValues(settings: MailSettings): FormValues {
  return {
    enabled: settings.enabled,
    host: settings.host ?? '',
    port: settings.port ?? null,
    username: settings.username ?? '',
    password: '',
    removePassword: false,
    security: settings.security,
    fromAddress: settings.fromAddress ?? '',
    fromName: settings.fromName ?? '',
  };
}

function blankToNull(value: string | undefined): string | null {
  const trimmed = value?.trim() ?? '';
  return trimmed === '' ? null : trimmed;
}

/**
 * The outgoing mail transport.
 *
 * The password field is write-only. It starts empty whatever is stored, and leaving it empty keeps
 * the stored password. This is how the form can be saved without asking for the password again,
 * and why the server never has to send it back.
 */
export function MailSettingsPanel() {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const [form] = Form.useForm<FormValues>();
  const [testTo, setTestTo] = useState('');

  const { data: settings, isPending, error } = useMailSettings();
  const update = useUpdateMailSettings();
  const sendTest = useSendTestMail();

  useEffect(() => {
    if (settings !== undefined) {
      form.setFieldsValue(toFormValues(settings));
    }
  }, [settings, form]);

  if (isPending) {
    return <Skeleton active />;
  }
  if (error !== null) {
    return <Alert type="error" showIcon message={toMessage(error, t)} />;
  }

  const onSave = async (values: FormValues) => {
    const password = values.password ?? '';
    const input: MailSettingsInput = {
      enabled: values.enabled,
      host: blankToNull(values.host),
      port: values.port ?? null,
      username: blankToNull(values.username),
      security: values.security,
      fromAddress: blankToNull(values.fromAddress),
      fromName: blankToNull(values.fromName),
      // Absent keeps the stored password; '' removes it; anything else replaces it.
      ...(values.removePassword ? { password: '' } : password !== '' ? { password } : {}),
    };
    try {
      await update.mutateAsync(input);
      void message.success(t('admin.mail.saved'));
    } catch (failure) {
      if (!applyApiError(failure, form)) {
        void message.error(toMessage(failure, t));
      }
    }
  };

  const onTest = async () => {
    try {
      await sendTest.mutateAsync(testTo.trim());
      void message.success(t('admin.mail.test.sent', { to: testTo.trim() }));
    } catch (failure) {
      // The one place the server's own words are shown: "535 Authentication failed" is exactly
      // what a super admin needs to fix their settings, and no translation could say it better.
      if (isApiError(failure) && failure.code === 'MAIL_DELIVERY_FAILED') {
        void message.error(t('admin.mail.test.failed', { detail: failure.detail ?? '' }), 8);
      } else {
        void message.error(toMessage(failure, t));
      }
    }
  };

  return (
    <FormColumn>
      {!settings.enabled && (
        <Alert type="warning" showIcon message={t('admin.mail.disabledWarning')} />
      )}

      <Form<FormValues>
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={toFormValues(settings)}
        onFinish={(values) => {
          void onSave(values);
        }}
        onValuesChange={(changed: Partial<FormValues>) => {
          if (changed.security !== undefined && !form.getFieldValue('port')) {
            form.setFieldValue('port', DEFAULT_PORT[changed.security]);
          }
        }}
      >
        <Form.Item name="enabled" label={t('admin.mail.enabled')} valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="host" label={t('admin.mail.host')}>
          <Input placeholder="smtp.example.com" autoComplete="off" />
        </Form.Item>
        <Form.Item name="security" label={t('admin.mail.security')}>
          <Select<MailSecurity>
            options={(['STARTTLS', 'SSL', 'NONE'] as const).map((value) => ({
              value,
              label: t(`admin.mail.securityOption.${value}`),
            }))}
          />
        </Form.Item>
        <Form.Item name="port" label={t('admin.mail.port')}>
          <InputNumber min={1} max={65535} precision={0} />
        </Form.Item>
        <Form.Item name="username" label={t('admin.mail.username')}>
          <Input autoComplete="off" />
        </Form.Item>
        <Form.Item
          name="password"
          label={t('admin.mail.password')}
          extra={
            settings.loginStored ? t('admin.mail.passwordStored') : t('admin.mail.passwordNone')
          }
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        {settings.loginStored && (
          <Form.Item name="removePassword" valuePropName="checked">
            <Switch
              checkedChildren={t('admin.mail.removePassword')}
              unCheckedChildren={t('admin.mail.keepPassword')}
            />
          </Form.Item>
        )}
        <Form.Item name="fromAddress" label={t('admin.mail.fromAddress')}>
          <Input placeholder="noreply@example.com" />
        </Form.Item>
        <Form.Item name="fromName" label={t('admin.mail.fromName')}>
          <Input placeholder="Gia Ký" />
        </Form.Item>

        <FormActions>
          <Button type="primary" htmlType="submit" loading={update.isPending}>
            {t('common.save')}
          </Button>
        </FormActions>
        <Hint>
          {t('admin.mail.updatedAt', {
            when: dayjs(settings.updatedAt).format('YYYY-MM-DD HH:mm'),
          })}
        </Hint>
      </Form>

      <TestRow>
        <Input
          type="email"
          value={testTo}
          placeholder={t('admin.mail.test.placeholder')}
          onChange={(event) => {
            setTestTo(event.target.value);
          }}
        />
        <Button
          onClick={() => {
            void onTest();
          }}
          loading={sendTest.isPending}
          disabled={testTo.trim() === ''}
        >
          {t('admin.mail.test.action')}
        </Button>
      </TestRow>
      <Hint>{t('admin.mail.test.hint')}</Hint>
    </FormColumn>
  );
}
