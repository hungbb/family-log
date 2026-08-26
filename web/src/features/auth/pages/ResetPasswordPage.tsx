import { App as AntApp, Button, Form, Input, Result } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router';

import { resetPassword } from '../../../api/endpoints/auth';
import { applyApiError, toMessage, validateWithSchema } from '../../../lib/apiErrors';
import { AuthLayout } from '../components/AuthLayout';
import { Footer, Hint } from '../components/styles';
import {
  PASSWORD_MIN_LENGTH,
  resetPasswordSchema,
  type ResetPasswordValues,
} from '../model/schemas';

/**
 * Set a new password from a reset link (FR-A6).
 *
 * The token comes from the query string, which is where the emailed link puts it. Success does not
 * sign anyone in: the reset revoked every session for the account, this browser's included, so the
 * only honest next step is the login form.
 */
export function ResetPasswordPage() {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm<ResetPasswordValues>();
  const [submitting, setSubmitting] = useState(false);

  const token = searchParams.get('token');

  const onFinish = async (values: ResetPasswordValues) => {
    if (token === null) {
      return;
    }
    const parsed = validateWithSchema(resetPasswordSchema, values, form, t);
    if (parsed === null) {
      return;
    }
    setSubmitting(true);
    try {
      await resetPassword({ token, newPassword: parsed.password });
      void message.success(t('auth.reset.done'));
      void navigate('/login', { replace: true });
    } catch (error) {
      if (!applyApiError(error, form)) {
        void message.error(toMessage(error, t));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (token === null) {
    return (
      <AuthLayout title={t('auth.reset.title')}>
        <Result
          status="warning"
          title={t('auth.reset.missingTokenTitle')}
          subTitle={t('auth.reset.missingTokenBody')}
          extra={
            <Link to="/forgot-password">
              <Button type="primary">{t('auth.reset.requestNew')}</Button>
            </Link>
          }
        />
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title={t('auth.reset.title')}
      subtitle={t('auth.reset.subtitle')}
      footer={
        <Footer>
          <Link to="/login">{t('auth.forgot.backToLogin')}</Link>
        </Footer>
      }
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={(values) => void onFinish(values)}
        requiredMark={false}
      >
        <Form.Item name="password" label={t('auth.field.newPassword')}>
          <Input.Password autoComplete="new-password" size="large" />
        </Form.Item>
        <Hint>{t('auth.register.passwordHint', { count: PASSWORD_MIN_LENGTH })}</Hint>

        <Form.Item name="confirmPassword" label={t('auth.field.confirmPassword')}>
          <Input.Password autoComplete="new-password" size="large" />
        </Form.Item>

        <Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={submitting}>
            {t('auth.reset.submit')}
          </Button>
        </Form.Item>

        <Hint>{t('auth.reset.signsOutEverywhere')}</Hint>
      </Form>
    </AuthLayout>
  );
}
