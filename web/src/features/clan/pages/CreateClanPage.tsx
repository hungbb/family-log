import { CheckOutlined, CopyOutlined } from '@ant-design/icons';
import { App as AntApp, Button, Form, Input, Space } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { type Clan } from '@/api/endpoints/clans.ts';
import { applyApiError, toMessage } from '@/lib/apiErrors.ts';

import { useCreateClan } from '../hooks/useClans';

import {
  CodeDisplay,
  CodeValue,
  FormGrid,
  Page,
  PageHeader,
  PageSubtitle,
  PageTitle,
  SectionCard,
} from './styles';

interface CreateClanFormValues {
  name: string;
  surname?: string;
  originPlace?: string;
  description?: string;
}

/**
 * Founding a clan (F2.2).
 *
 * The screen has two states rather than one. Submitting does not navigate away, because the thing
 * that just happened — a join code was generated — is the only chance the founder has to copy it
 * while they are thinking about it. They leave when they choose to.
 */
export function CreateClanPage() {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm<CreateClanFormValues>();
  const createClan = useCreateClan();
  const [created, setCreated] = useState<Clan | null>(null);
  const [copied, setCopied] = useState(false);

  const onFinish = async (values: CreateClanFormValues) => {
    try {
      setCreated(await createClan.mutateAsync(values));
    } catch (failure) {
      if (!applyApiError(failure, form)) {
        void message.error(toMessage(failure, t));
      }
    }
  };

  const onCopy = async () => {
    if (created === null) return;
    try {
      await navigator.clipboard.writeText(created.clanCode);
      setCopied(true);
      void message.success(t('clan.create.codeCopied'));
    } catch {
      // Clipboard access is denied in some browsers and over plain HTTP. The code is on screen
      // and selectable either way, so this is a missing convenience rather than a failure.
      void message.warning(t('clan.create.copyUnavailable'));
    }
  };

  if (created !== null) {
    return (
      <Page>
        <PageHeader>
          <div>
            <PageTitle>{created.name}</PageTitle>
            <PageSubtitle>{t('clan.create.doneSubtitle')}</PageSubtitle>
          </div>
        </PageHeader>

        <SectionCard title={t('clan.create.codeTitle')}>
          <PageSubtitle>{t('clan.create.codeBody')}</PageSubtitle>
          <CodeDisplay>
            <CodeValue>{created.clanCode}</CodeValue>
            <Button
              icon={copied ? <CheckOutlined /> : <CopyOutlined />}
              onClick={() => void onCopy()}
            >
              {copied ? t('clan.create.copied') : t('common.copy')}
            </Button>
          </CodeDisplay>

          <Space size={12}>
            <Button type="primary" onClick={() => void navigate(`/clans/${created.id}/branches`)}>
              {t('clan.create.addBranches')}
            </Button>
            <Button onClick={() => void navigate(`/clans/${created.id}/members`)}>
              {t('clan.create.openClan')}
            </Button>
          </Space>
        </SectionCard>
      </Page>
    );
  }

  return (
    <Page>
      <PageHeader>
        <div>
          <PageTitle>{t('clan.create.title')}</PageTitle>
          <PageSubtitle>{t('clan.create.subtitle')}</PageSubtitle>
        </div>
      </PageHeader>

      <SectionCard>
        <FormGrid>
          <Form
            form={form}
            layout="vertical"
            requiredMark={false}
            onFinish={(values) => void onFinish(values)}
          >
            <Form.Item
              name="name"
              label={t('clan.field.name')}
              rules={[{ required: true, message: t('clan.validation.nameRequired') }]}
            >
              <Input size="large" placeholder={t('clan.placeholder.name')} maxLength={200} />
            </Form.Item>

            <Form.Item
              name="surname"
              label={t('clan.field.surname')}
              extra={t('clan.field.surnameHint')}
            >
              <Input size="large" placeholder={t('clan.placeholder.surname')} maxLength={100} />
            </Form.Item>

            <Form.Item name="originPlace" label={t('clan.field.originPlace')}>
              <Input size="large" placeholder={t('clan.placeholder.originPlace')} maxLength={255} />
            </Form.Item>

            <Form.Item name="description" label={t('clan.field.description')}>
              <Input.TextArea rows={4} maxLength={4000} showCount />
            </Form.Item>

            <Form.Item>
              <Space size={12}>
                <Button type="primary" htmlType="submit" loading={createClan.isPending}>
                  {t('clan.create.submit')}
                </Button>
                <Button onClick={() => void navigate(-1)}>{t('common.cancel')}</Button>
              </Space>
            </Form.Item>
          </Form>
        </FormGrid>
      </SectionCard>
    </Page>
  );
}
