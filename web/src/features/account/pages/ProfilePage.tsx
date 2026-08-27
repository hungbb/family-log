import { App as AntApp, Button, Form, Input, List, Select, Skeleton, Tag } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { LocaleTag } from '@/api/endpoints/auth.ts';
import { logoutAll } from '@/api/endpoints/auth.ts';
import { MyJoinRequests } from '@/features/members';
import { useAuth } from '@/hooks/useAuth.ts';
import { applyApiError, toMessage } from '@/lib/apiErrors.ts';

import { useProfile, useSessions, useUpdateProfile } from '../hooks/useProfile';

import {
  DangerBody,
  DangerCopy,
  DangerTitle,
  DangerZone,
  FormGrid,
  Page,
  PageHeader,
  PageSubtitle,
  PageTitle,
  SectionCard,
  SessionAgent,
  SessionMeta,
  SessionRow,
} from './styles';

interface ProfileFormValues {
  displayName: string;
  locale: LocaleTag;
}

/**
 * `/me` — the account's own page (task F1.6).
 *
 * Three things, in the order they matter: what the account is, where it is signed in, and how to
 * end all of that. The locale control changes the interface immediately as well as saving, because
 * a language setting that only takes effect on the next reload is a setting people try twice.
 */
export function ProfilePage() {
  const { t, i18n } = useTranslation();
  const { message, modal } = AntApp.useApp();
  const { endSession } = useAuth();
  const { data: profile, isPending, error } = useProfile();
  const { data: sessions, isPending: sessionsPending } = useSessions();
  const updateProfile = useUpdateProfile();
  const [form] = Form.useForm<ProfileFormValues>();
  const [revoking, setRevoking] = useState(false);

  useEffect(() => {
    if (profile) {
      form.setFieldsValue({ displayName: profile.displayName, locale: profile.locale });
    }
  }, [profile, form]);

  const onFinish = async (values: ProfileFormValues) => {
    try {
      await updateProfile.mutateAsync(values);
      await i18n.changeLanguage(values.locale.startsWith('en') ? 'en' : 'vi');
      void message.success(t('account.saved'));
    } catch (failure) {
      if (!applyApiError(failure, form)) {
        void message.error(toMessage(failure, t));
      }
    }
  };

  const onLogoutEverywhere = () => {
    modal.confirm({
      title: t('account.logoutAll.confirmTitle'),
      content: t('account.logoutAll.confirmBody'),
      okText: t('account.logoutAll.confirmOk'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        setRevoking(true);
        try {
          await logoutAll();
          // This browser's own session went with the others. Clearing locally sends us to the
          // login screen rather than leaving a shell whose every request now fails.
          await endSession();
        } catch (failure) {
          void message.error(toMessage(failure, t));
        } finally {
          setRevoking(false);
        }
      },
    });
  };

  if (error) {
    return (
      <Page>
        <PageHeader>
          <PageTitle>{t('account.title')}</PageTitle>
          <PageSubtitle>{toMessage(error, t)}</PageSubtitle>
        </PageHeader>
      </Page>
    );
  }

  return (
    <Page>
      <PageHeader>
        <PageTitle>{t('account.title')}</PageTitle>
        <PageSubtitle>{t('account.subtitle')}</PageSubtitle>
      </PageHeader>

      <SectionCard title={t('account.details')}>
        <Skeleton loading={isPending} active>
          <FormGrid>
            <Form
              form={form}
              layout="vertical"
              onFinish={(values) => void onFinish(values)}
              requiredMark={false}
            >
              <Form.Item label={t('auth.field.email')}>
                <Input value={profile?.email ?? ''} disabled />
              </Form.Item>

              <Form.Item label={t('account.verification')}>
                {profile?.emailVerified === true ? (
                  <Tag color="success">{t('account.verified')}</Tag>
                ) : (
                  <Tag color="warning">{t('account.unverified')}</Tag>
                )}
              </Form.Item>

              <Form.Item name="displayName" label={t('auth.field.displayName')}>
                <Input size="large" />
              </Form.Item>

              <Form.Item name="locale" label={t('account.language')}>
                <Select
                  size="large"
                  options={[
                    { value: 'vi-VN', label: t('language.vi') },
                    { value: 'en-US', label: t('language.en') },
                  ]}
                />
              </Form.Item>

              <Form.Item>
                <Button type="primary" htmlType="submit" loading={updateProfile.isPending}>
                  {t('common.save')}
                </Button>
              </Form.Item>
            </Form>
          </FormGrid>
        </Skeleton>
      </SectionCard>

      <SectionCard title={t('members.myRequests.title')}>
        <PageSubtitle>{t('members.myRequests.subtitle')}</PageSubtitle>
        <MyJoinRequests />
      </SectionCard>

      <SectionCard title={t('account.sessions.title')}>
        <PageSubtitle>{t('account.sessions.subtitle')}</PageSubtitle>
        <List
          loading={sessionsPending}
          dataSource={sessions ?? []}
          locale={{ emptyText: t('account.sessions.empty') }}
          renderItem={(session) => (
            <List.Item key={session.id}>
              <SessionRow>
                <SessionAgent>
                  {session.userAgent ?? t('account.sessions.unknownClient')}
                </SessionAgent>
                <SessionMeta>
                  {[
                    session.ipAddress ?? t('account.sessions.unknownAddress'),
                    dayjs(session.issuedAt).format('YYYY-MM-DD HH:mm'),
                  ].join(' · ')}
                </SessionMeta>
              </SessionRow>
            </List.Item>
          )}
        />
      </SectionCard>

      <SectionCard title={t('account.logoutAll.title')}>
        <DangerZone>
          <DangerCopy>
            <DangerTitle>{t('account.logoutAll.heading')}</DangerTitle>
            <DangerBody>{t('account.logoutAll.body')}</DangerBody>
          </DangerCopy>
          <Button danger loading={revoking} onClick={onLogoutEverywhere}>
            {t('account.logoutAll.action')}
          </Button>
        </DangerZone>
      </SectionCard>
    </Page>
  );
}
