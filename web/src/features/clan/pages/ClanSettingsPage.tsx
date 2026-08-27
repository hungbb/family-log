import { InfoCircleOutlined } from '@ant-design/icons';
import { App as AntApp, Alert, Button, Form, Input, Skeleton, Space, Tooltip } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router';

import { applyApiError, toMessage } from '@/lib/apiErrors.ts';

import { useClan, useDeleteClan, useUpdateClan } from '../hooks/useClans';

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
  ReadOnlyValue,
  SectionCard,
} from './styles';

interface SettingsFormValues {
  name: string;
  originPlace?: string;
  description?: string;
}

/**
 * Clan settings (F2.4).
 *
 * Editable to the founder; read-only to everybody else until the permission model lands. The page
 * is not hidden from a member who cannot use it — knowing that settings exist, and that somebody
 * else owns them, is more useful than a menu item that vanishes.
 *
 * The clan code is displayed and cannot be edited (I-C1). The tooltip explains why, because a field
 * that refuses to be changed without saying so reads as a bug.
 */
export function ClanSettingsPage() {
  const { t } = useTranslation();
  const { message, modal } = AntApp.useApp();
  const navigate = useNavigate();
  const { clanId = '' } = useParams();
  const { data: clan, isPending, error } = useClan(clanId);
  const updateClan = useUpdateClan(clanId);
  const deleteClan = useDeleteClan(clanId);
  const [form] = Form.useForm<SettingsFormValues>();

  const canAdminister = clan?.myPermissions.includes('CLAN_ADMIN') ?? false;

  useEffect(() => {
    if (clan) {
      form.setFieldsValue({
        name: clan.name,
        ...(clan.originPlace !== undefined ? { originPlace: clan.originPlace } : {}),
        ...(clan.description !== undefined ? { description: clan.description } : {}),
      });
    }
  }, [clan, form]);

  const onFinish = async (values: SettingsFormValues) => {
    if (clan === undefined) return;
    try {
      await updateClan.mutateAsync({ ...values, version: clan.version });
      void message.success(t('clan.settings.saved'));
    } catch (failure) {
      if (!applyApiError(failure, form)) {
        void message.error(toMessage(failure, t));
      }
    }
  };

  const onDelete = () => {
    modal.confirm({
      title: t('clan.settings.delete.confirmTitle'),
      content: t('clan.settings.delete.confirmBody'),
      okText: t('clan.settings.delete.confirmOk'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteClan.mutateAsync();
          void message.success(t('clan.settings.delete.done'));
          void navigate('/');
        } catch (failure) {
          void message.error(toMessage(failure, t));
        }
      },
    });
  };

  if (error) {
    return (
      <Page>
        <PageHeader>
          <div>
            <PageTitle>{t('clan.settings.title')}</PageTitle>
            <PageSubtitle>{toMessage(error, t)}</PageSubtitle>
          </div>
        </PageHeader>
      </Page>
    );
  }

  return (
    <Page>
      <PageHeader>
        <div>
          <PageTitle>{t('clan.settings.title')}</PageTitle>
          <PageSubtitle>{t('clan.settings.subtitle')}</PageSubtitle>
        </div>
      </PageHeader>

      {!isPending && !canAdminister && (
        <Alert type="info" showIcon message={t('clan.settings.readOnly')} />
      )}

      <SectionCard title={t('clan.settings.details')}>
        <Skeleton loading={isPending} active>
          <FormGrid>
            <Form
              form={form}
              layout="vertical"
              requiredMark={false}
              disabled={!canAdminister}
              onFinish={(values) => void onFinish(values)}
            >
              <Form.Item label={t('clan.field.code')}>
                <ReadOnlyValue>
                  {clan?.clanCode ?? ''}
                  <Tooltip title={t('clan.field.codeTooltip')}>
                    <InfoCircleOutlined aria-label={t('clan.field.codeTooltip')} />
                  </Tooltip>
                </ReadOnlyValue>
              </Form.Item>

              <Form.Item
                name="name"
                label={t('clan.field.name')}
                rules={[{ required: true, message: t('clan.validation.nameRequired') }]}
              >
                <Input size="large" maxLength={200} />
              </Form.Item>

              <Form.Item name="originPlace" label={t('clan.field.originPlace')}>
                <Input size="large" maxLength={255} />
              </Form.Item>

              <Form.Item name="description" label={t('clan.field.description')}>
                <Input.TextArea rows={4} maxLength={4000} showCount />
              </Form.Item>

              {canAdminister && (
                <Form.Item>
                  <Space size={12}>
                    <Button type="primary" htmlType="submit" loading={updateClan.isPending}>
                      {t('common.save')}
                    </Button>
                  </Space>
                </Form.Item>
              )}
            </Form>
          </FormGrid>
        </Skeleton>
      </SectionCard>

      {canAdminister && (
        <SectionCard title={t('clan.settings.delete.title')}>
          <DangerZone>
            <DangerCopy>
              <DangerTitle>{t('clan.settings.delete.heading')}</DangerTitle>
              <DangerBody>{t('clan.settings.delete.body')}</DangerBody>
            </DangerCopy>
            <Button danger loading={deleteClan.isPending} onClick={onDelete}>
              {t('clan.settings.delete.action')}
            </Button>
          </DangerZone>
        </SectionCard>
      )}
    </Page>
  );
}
