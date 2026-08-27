import { App as AntApp, Button, Form, Input, Space, Tag } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { type ClanCard } from '@/api/endpoints/clans.ts';
import { submitJoinRequest } from '@/api/endpoints/joinRequests.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { useLookupClan } from '../hooks/useClans';

import {
  FormGrid,
  Page,
  PageHeader,
  PageSubtitle,
  PageTitle,
  PreviewCard,
  PreviewMeta,
  PreviewName,
  SectionCard,
} from './styles';

interface JoinFormValues {
  code: string;
  message?: string;
}

/**
 * Joining a clan by code (F2.6).
 *
 * Two steps, and the first one is the point: somebody pasting a code from a chat message needs to
 * see whose family it is before they send a stranger a request. The preview comes from
 * `/clans/lookup`, which answers with a deliberately minimal card — a name, a place and a member
 * count — precisely so that this screen can exist without the code space becoming worth scraping.
 *
 * The same field accepts a clan code and a branch code. They are the same shape, and asking a person
 * to know which kind they were given would be asking them a question they cannot answer.
 */
export function JoinClanPage() {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm<JoinFormValues>();
  const lookup = useLookupClan();
  const [card, setCard] = useState<ClanCard | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onLookup = async () => {
    const code = form.getFieldValue('code') as string | undefined;
    if (code === undefined || code.trim() === '') {
      form.setFields([{ name: 'code', errors: [t('clan.join.validation.codeRequired')] }]);
      return;
    }
    try {
      setCard(await lookup.mutateAsync(code.trim()));
      form.setFields([{ name: 'code', errors: [] }]);
    } catch (failure) {
      setCard(null);
      form.setFields([{ name: 'code', errors: [toMessage(failure, t)] }]);
    }
  };

  const onSubmit = async () => {
    const values = form.getFieldsValue();
    const code = values.code.trim();
    setSubmitting(true);
    try {
      await submitJoinRequest({
        // The preview is what tells the two kinds of code apart: a card carrying a branch means
        // the code named a branch, and sending it as a clan code would find nothing.
        ...(card?.targetBranchId === undefined ? { clanCode: code } : { branchCode: code }),
        ...(values.message !== undefined && values.message !== ''
          ? { message: values.message }
          : {}),
      });
      void message.success(t('clan.join.sent'));
      void navigate('/me');
    } catch (failure) {
      void message.error(toMessage(failure, t));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Page>
      <PageHeader>
        <div>
          <PageTitle>{t('clan.join.title')}</PageTitle>
          <PageSubtitle>{t('clan.join.subtitle')}</PageSubtitle>
        </div>
      </PageHeader>

      <SectionCard>
        <FormGrid>
          <Form form={form} layout="vertical" requiredMark={false}>
            <Form.Item name="code" label={t('clan.join.field.code')}>
              <Input.Search
                size="large"
                placeholder={t('clan.join.placeholder.code')}
                enterButton={t('clan.join.lookUp')}
                loading={lookup.isPending}
                maxLength={32}
                onSearch={() => void onLookup()}
              />
            </Form.Item>

            {card !== null && (
              <PreviewCard>
                <PreviewName>{card.name}</PreviewName>
                <PreviewMeta>
                  {[
                    card.originPlace,
                    t('clan.join.memberCount', { count: card.memberCount }),
                    card.targetBranchName,
                  ]
                    .filter((part) => part !== undefined && part !== '')
                    .join(' · ')}
                </PreviewMeta>

                {card.alreadyMember ? (
                  <Tag color="success">{t('clan.join.alreadyMember')}</Tag>
                ) : card.hasPendingRequest ? (
                  <Tag color="warning">{t('clan.join.alreadyRequested')}</Tag>
                ) : (
                  <>
                    <Form.Item name="message" label={t('clan.join.field.message')}>
                      <Input.TextArea
                        rows={3}
                        maxLength={1000}
                        showCount
                        placeholder={t('clan.join.placeholder.message')}
                      />
                    </Form.Item>
                    <Space size={12}>
                      <Button type="primary" loading={submitting} onClick={() => void onSubmit()}>
                        {t('clan.join.submit')}
                      </Button>
                      <Button onClick={() => void navigate('/')}>{t('common.cancel')}</Button>
                    </Space>
                  </>
                )}
              </PreviewCard>
            )}
          </Form>
        </FormGrid>
      </SectionCard>
    </Page>
  );
}
