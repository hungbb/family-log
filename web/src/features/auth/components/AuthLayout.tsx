import { Segmented } from 'antd';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { SUPPORTED_LOCALES } from '../../../i18n';

import { Brand, BrandMark, BrandName, FormCard, Page, Panel, Subtitle, Title } from './styles';

interface AuthLayoutProps {
  title: string;
  subtitle?: string;
  children: ReactNode;
  /** Rendered under the card: the "no account yet?" line and its link. */
  footer?: ReactNode;
}

/**
 * The frame shared by every signed-out screen.
 *
 * The language switch lives here rather than only in the app shell on purpose: someone who cannot
 * read the login form cannot reach the shell to change the language, which would make the second
 * locale unreachable for exactly the people who need it (NFR-8).
 */
export function AuthLayout({ title, subtitle, children, footer }: AuthLayoutProps) {
  const { t, i18n } = useTranslation();
  const currentLocale = SUPPORTED_LOCALES.find((locale) => i18n.language.startsWith(locale));

  return (
    <Page>
      <Panel>
        <Brand>
          <BrandMark aria-hidden="true">G</BrandMark>
          <BrandName>{t('app.name')}</BrandName>
        </Brand>

        <FormCard>
          <Title>{title}</Title>
          {subtitle !== undefined && <Subtitle>{subtitle}</Subtitle>}
          {children}
        </FormCard>

        {footer}

        <Brand>
          <Segmented
            size="small"
            aria-label={t('language.label')}
            value={currentLocale ?? SUPPORTED_LOCALES[0]}
            onChange={(value) => {
              void i18n.changeLanguage(value);
            }}
            options={SUPPORTED_LOCALES.map((locale) => ({
              value: locale,
              label: t(`language.${locale}`),
            }))}
          />
        </Brand>
      </Panel>
    </Page>
  );
}
