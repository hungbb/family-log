import { BellOutlined } from '@ant-design/icons';
import { Badge, Button, Dropdown, Empty, List, Spin } from 'antd';
import dayjs from 'dayjs';
import { type TFunction } from 'i18next';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { type AppNotification } from '../../api/endpoints/notifications';
import { useMarkRead, useNotifications, useUnreadCount } from '../../hooks/useNotifications';

import { NotificationItem, NotificationList, NotificationText, NotificationTime } from './styles';

/**
 * The bell in the top bar (F6.10).
 *
 * It lives in `components/layout` rather than in a feature because the shell renders it and the
 * shell may not import a feature (doc 07 §2) — and the list it shows spans every clan, so there is
 * no one feature it belongs to anyway.
 *
 * The badge is a polled count and the list is fetched on open, because those are two different
 * costs: one number every sixty seconds is cheap enough to run all day, twenty rows is not.
 *
 * Clicking a notification marks it read. There is no "mark all as read": in a family record the
 * things that arrive here are decisions about who somebody is, and a single sweep that clears them
 * unread is how one gets missed.
 */
export function NotificationBell() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const { data: count } = useUnreadCount();
  const { data, isPending } = useNotifications(open);
  const markRead = useMarkRead();

  const unread = count?.unread ?? 0;
  const items = data?.content ?? [];

  const panel = (
    <NotificationList>
      {isPending && open ? (
        <Spin />
      ) : items.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('notifications.empty')} />
      ) : (
        <List
          dataSource={items}
          renderItem={(item) => (
            <NotificationItem
              key={item.id}
              $unread={item.readAt === undefined}
              type="button"
              onClick={() => {
                if (item.readAt === undefined) {
                  markRead.mutate(item.id);
                }
              }}
            >
              <NotificationText>{describe(item, t)}</NotificationText>
              <NotificationTime>
                {dayjs(item.createdAt).format('YYYY-MM-DD HH:mm')}
              </NotificationTime>
            </NotificationItem>
          )}
        />
      )}
    </NotificationList>
  );

  return (
    <Dropdown
      open={open}
      onOpenChange={setOpen}
      trigger={['click']}
      placement="bottomRight"
      popupRender={() => panel}
    >
      <Button type="text" aria-label={t('notifications.title')}>
        <Badge count={unread} size="small">
          <BellOutlined />
        </Badge>
      </Button>
    </Dropdown>
  );
}

/**
 * One notification, as a sentence.
 *
 * The server sends a type and a payload and never a phrase, so the wording is translated here — a
 * Vietnamese reader must not be told in English that their claim was approved.
 *
 * An unrecognised type falls back to a neutral line rather than rendering a key or crashing: doc 05
 * §6 lets the server add enum values inside a major version, and a client that breaks on one breaks
 * on a deploy.
 */
function describe(notification: AppNotification, t: TFunction): string {
  const payload = notification.payload;
  const key =
    notification.type === 'CLAIM_DECIDED'
      ? `notifications.message.CLAIM_DECIDED_${payload['outcome'] ?? 'APPROVED'}`
      : `notifications.message.${notification.type}`;

  const text = t(key, {
    defaultValue: '',
    person: payload['personName'] ?? '',
    name: payload['requesterName'] ?? payload['actorName'] ?? '',
    reason: payload['reason'] ?? '',
  });
  return text === '' ? t('notifications.message.unknown') : text;
}
