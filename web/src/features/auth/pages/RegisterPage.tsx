import { App as AntApp, Button, Form, Input } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';

import { login, register, type LocaleTag } from '../../../api/endpoints/auth';
import { useAuth } from '../../../hooks/useAuth';
import { applyApiError, toMessage, validateWithSchema } from '../../../lib/apiErrors';
import { AuthLayout } from '../components/AuthLayout';
import { Footer, Hint } from '../components/styles';
import { PASSWORD_MIN_LENGTH, registerSchema, type RegisterValues } from '../model/schemas';

/**
 * Create an account (FR-A1).
 *
 * Registration does not sign anyone in — the server deliberately returns no tokens — so this signs
 * in straight afterwards with the credentials just entered. That keeps one code path for becoming
 * signed in, and means a registration that half-succeeded cannot leave a session behind.
 *
 * The new account is `PENDING_VERIFICATION` and may use the product immediately; the banner in the
 * shell is what asks for the address to be confirmed (phase doc §8).
 */
export function RegisterPage() {
  const { t, i18n } = useTranslation();
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const { startSession } = useAuth();
  const [form] = Form.useForm<RegisterValues>();
  const [submitting, setSubmitting] = useState(false);

  const onFinish = async (values: RegisterValues) => {
    const parsed = validateWithSchema(registerSchema, values, form, t);
    if (parsed === null) {
      return;
    }
    setSubmitting(true);
    try {
      await register({ ...parsed, locale: currentLocaleTag(i18n.language) });
      startSession(await login({ email: parsed.email, password: parsed.password }));
      void message.success(t('auth.register.created'));
      void navigate('/', { replace: true });
    } catch (error) {
      // A 422 from the password policy lands on the password field; a 409 becomes a message.
      if (!applyApiError(error, form)) {
        void message.error(toMessage(error, t));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title={t('auth.register.title')}
      subtitle={t('auth.register.subtitle')}
      footer={
        <Footer>
          <span>{t('auth.register.haveAccount')}</span>
          <Link to="/login">{t('auth.register.loginLink')}</Link>
        </Footer>
      }
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={(values) => void onFinish(values)}
        requiredMark={false}
      >
        <Form.Item name="displayName" label={t('auth.field.displayName')}>
          <Input autoComplete="name" size="large" placeholder={t('auth.placeholder.displayName')} />
        </Form.Item>

        <Form.Item name="email" label={t('auth.field.email')}>
          <Input
            type="email"
            autoComplete="email"
            size="large"
            placeholder={t('auth.placeholder.email')}
          />
        </Form.Item>

        <Form.Item name="password" label={t('auth.field.password')}>
          <Input.Password autoComplete="new-password" size="large" />
        </Form.Item>
        <Hint>{t('auth.register.passwordHint', { count: PASSWORD_MIN_LENGTH })}</Hint>

        <Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={submitting}>
            {t('auth.register.submit')}
          </Button>
        </Form.Item>
      </Form>
    </AuthLayout>
  );
}

/** The interface's current language, as the tag the API stores. */
function currentLocaleTag(language: string): LocaleTag {
  return language.startsWith('en') ? 'en-US' : 'vi-VN';
}
