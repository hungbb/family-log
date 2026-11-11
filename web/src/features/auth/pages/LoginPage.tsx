import { App as AntApp, Button, Form, Input } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate } from 'react-router';

import { login } from '../../../api/endpoints/auth';
import { useAuth } from '../../../hooks/useAuth';
import { applyApiError, toMessage, validateWithSchema } from '../../../lib/apiErrors';
import { returnPathOrHome } from '../../../lib/returnPath';
import { AuthLayout } from '../components/AuthLayout';
import { Footer } from '../components/styles';
import { loginSchema, type LoginValues } from '../model/schemas';

/**
 * Sign in (FR-A3).
 *
 * On success the caller goes back where they were headed. `RequireAuth` puts that path in the
 * navigation state; without it, every expired session would dump people on the dashboard and make
 * them find their way back to the page they had open.
 */
export function LoginPage() {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const { startSession } = useAuth();
  const [form] = Form.useForm<LoginValues>();
  const [submitting, setSubmitting] = useState(false);

  const returnPath = returnPathOrHome(location.state);

  const onFinish = async (values: LoginValues) => {
    const parsed = validateWithSchema(loginSchema, values, form, t);
    if (parsed === null) {
      return;
    }
    setSubmitting(true);
    try {
      startSession(await login(parsed));
      void navigate(returnPath, { replace: true });
    } catch (error) {
      // The server answers every failure the same way, on purpose: a message that distinguished
      // "no such account" from "wrong password" would be an account-enumeration oracle.
      if (!applyApiError(error, form)) {
        void message.error(toMessage(error, t));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title={t('auth.login.title')}
      subtitle={t('auth.login.subtitle')}
      footer={
        <Footer>
          <span>{t('auth.login.noAccount')}</span>
          <Link to="/register">{t('auth.login.registerLink')}</Link>
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

        <Form.Item name="password" label={t('auth.field.password')}>
          <Input.Password autoComplete="current-password" size="large" />
        </Form.Item>

        <Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={submitting}>
            {t('auth.login.submit')}
          </Button>
        </Form.Item>

        <Footer>
          <Link to="/forgot-password">{t('auth.login.forgotPassword')}</Link>
        </Footer>
      </Form>
    </AuthLayout>
  );
}
