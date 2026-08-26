import { App as AntApp, Button, Form, Input, Result } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { forgotPassword } from '../../../api/endpoints/auth';
import { toMessage, validateWithSchema } from '../../../lib/apiErrors';
import { AuthLayout } from '../components/AuthLayout';
import { Footer } from '../components/styles';
import { forgotPasswordSchema, type ForgotPasswordValues } from '../model/schemas';

/**
 * Request a password reset (FR-A6).
 *
 * The confirmation is the same whatever was typed — an address with an account, one without, one
 * that is not an address at all. The server answers `202` to all three for the same reason, and a
 * screen that said "we could not find that account" would undo it entirely.
 */
export function ForgotPasswordPage() {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const [form] = Form.useForm<ForgotPasswordValues>();
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);

  const onFinish = async (values: ForgotPasswordValues) => {
    const parsed = validateWithSchema(forgotPasswordSchema, values, form, t);
    if (parsed === null) {
      return;
    }
    setSubmitting(true);
    try {
      await forgotPassword(parsed.email);
      setSent(true);
    } catch (error) {
      // Only a transport failure or a rate limit can land here; the endpoint itself never refuses.
      void message.error(toMessage(error, t));
    } finally {
      setSubmitting(false);
    }
  };

  if (sent) {
    return (
      <AuthLayout title={t('auth.forgot.sentTitle')}>
        <Result
          status="success"
          title={t('auth.forgot.sentHeading')}
          subTitle={t('auth.forgot.sentBody')}
          extra={
            <Link to="/login">
              <Button type="primary">{t('auth.forgot.backToLogin')}</Button>
            </Link>
          }
        />
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title={t('auth.forgot.title')}
      subtitle={t('auth.forgot.subtitle')}
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
        <Form.Item name="email" label={t('auth.field.email')}>
          <Input
            type="email"
            autoComplete="email"
            size="large"
            placeholder={t('auth.placeholder.email')}
          />
        </Form.Item>

        <Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={submitting}>
            {t('auth.forgot.submit')}
          </Button>
        </Form.Item>
      </Form>
    </AuthLayout>
  );
}
