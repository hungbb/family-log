import { Button, Layout } from 'antd';
import styled from 'styled-components';

/**
 * The shell's styled parts.
 *
 * Every AntD component here is wrapped rather than overridden by class name (doc 07 §3.2 rule 3):
 * a wrapper survives an AntD upgrade, a `.ant-layout-sider` selector does not.
 */

export const Shell = styled(Layout)`
  min-height: 100vh;
  background: ${({ theme }) => theme.color.surfaceAlt};
`;

/**
 * **Never pass Ant Design's `theme` prop to this component.**
 *
 * `Layout.Sider` accepts `theme="light" | "dark"`, and styled-components claims the name `theme`
 * for itself: a `theme` prop on a styled component *replaces* the `ThemeProvider` context for every
 * interpolation below. Passing the string would make `theme.color` undefined here and crash the
 * whole shell, which is exactly what it did until this comment was written.
 *
 * The light appearance is ours instead — the two declarations below, plus `Layout.siderBg` in
 * `antdTheme.ts` — which is what doc 07 §3.2 rule 3 asks for anyway: wrap the component, do not
 * drive it by its own theming.
 */
export const Sider = styled(Layout.Sider)`
  border-right: 1px solid ${({ theme }) => theme.color.border};
  background: ${({ theme }) => theme.color.surface};
  color: ${({ theme }) => theme.color.ink};

  /* The sider is sticky rather than scrolling with the page: navigation should not disappear
     while panning a tree that is several screens tall. */
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
`;

export const Brand = styled.div<{ $collapsed: boolean }>`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space(2.5)};
  height: ${({ theme }) => theme.layout.topBarHeight};
  padding-inline: ${({ theme, $collapsed }) => ($collapsed ? theme.space(4) : theme.space(5))};
  border-bottom: 1px solid ${({ theme }) => theme.color.border};
  overflow: hidden;
  white-space: nowrap;
`;

export const BrandMark = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 26px;
  height: 26px;
  border-radius: ${({ theme }) => theme.radius.sm};
  background: ${({ theme }) => theme.color.primary};
  color: ${({ theme }) => theme.color.inkInverse};
  font-family: ${({ theme }) => theme.font.serif};
  font-size: ${({ theme }) => theme.fontSize.md};
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  line-height: 1;
`;

export const BrandName = styled.span`
  font-family: ${({ theme }) => theme.font.serif};
  font-size: ${({ theme }) => theme.fontSize.lg};
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  letter-spacing: -0.01em;
  color: ${({ theme }) => theme.color.ink};
`;

export const SiderFooter = styled.div`
  padding: ${({ theme }) => `${theme.space(4)} ${theme.space(5)}`};
  color: ${({ theme }) => theme.color.inkSubtle};
  font-size: ${({ theme }) => theme.fontSize.xs};
`;

export const TopBar = styled(Layout.Header)`
  position: sticky;
  top: 0;
  z-index: ${({ theme }) => theme.z.sticky};
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space(3)};
  height: ${({ theme }) => theme.layout.topBarHeight};
  padding-inline: ${({ theme }) => theme.space(5)};
  border-bottom: 1px solid ${({ theme }) => theme.color.border};
  background: ${({ theme }) => theme.color.surface};
`;

export const TopBarSpacer = styled.div`
  flex: 1 1 auto;
`;

export const Placeholder = styled.span`
  color: ${({ theme }) => theme.color.inkSubtle};
  font-size: ${({ theme }) => theme.fontSize.sm};
`;

export const Content = styled(Layout.Content)`
  width: 100%;
  max-width: ${({ theme }) => theme.layout.contentMaxWidth};
  margin-inline: auto;
  padding: ${({ theme }) => `${theme.space(7)} ${theme.space(5)} ${theme.space(10)}`};
`;

/**
 * The account menu's trigger.
 *
 * A `Button` rather than a bare `div` so it is focusable and operable from the keyboard without
 * anything being reimplemented — the menu is the only way to sign out, and a sign-out that needs a
 * mouse is not one (NFR-7).
 */
export const AccountButton = styled(Button)`
  height: 36px;
  padding-inline: ${({ theme }) => theme.space(2)};
`;

export const UserName = styled.span`
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: ${({ theme }) => theme.color.ink};
  font-size: ${({ theme }) => theme.fontSize.sm};

  /* The name is decoration next to the avatar on a narrow screen; the avatar carries the meaning. */
  @media (max-width: 640px) {
    display: none;
  }
`;

/**
 * The banner shown until an address is confirmed.
 *
 * A strip rather than a modal: an unverified account can use the product (phase doc §8), so this is
 * a reminder, not a gate.
 */
export const VerificationBanner = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${({ theme }) => theme.space(3)};
  padding: ${({ theme }) => `${theme.space(2)} ${theme.space(5)}`};
  border-bottom: 1px solid ${({ theme }) => theme.color.border};
  background: ${({ theme }) => theme.color.warningSubtle};
  color: ${({ theme }) => theme.color.warning};
  font-size: ${({ theme }) => theme.fontSize.sm};
`;
