import { Card } from 'antd';
import styled from 'styled-components';

/** The member and join-request screens' styled parts. */

export const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.space(6)};
`;

export const PageHeader = styled.header`
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: ${({ theme }) => theme.space(3)};
`;

export const PageTitle = styled.h1`
  margin: 0;
  font-family: ${({ theme }) => theme.font.serif};
  font-size: ${({ theme }) => theme.fontSize.xxl};
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  letter-spacing: -0.01em;
  color: ${({ theme }) => theme.color.ink};
`;

export const PageSubtitle = styled.p`
  margin: ${({ theme }) => `${theme.space(1)} 0 0`};
  max-width: 60ch;
  color: ${({ theme }) => theme.color.inkMuted};
  font-size: ${({ theme }) => theme.fontSize.md};
  line-height: ${({ theme }) => theme.lineHeight.normal};
`;

export const SectionCard = styled(Card)`
  border-color: ${({ theme }) => theme.color.border};
  box-shadow: ${({ theme }) => theme.shadow.card};
`;

/** Search and branch filter, side by side and wrapping on a narrow screen. */
export const FilterBar = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space(3)};
  margin-bottom: ${({ theme }) => theme.space(4)};
`;

export const MemberName = styled.span`
  color: ${({ theme }) => theme.color.ink};
  font-size: ${({ theme }) => theme.fontSize.md};
  font-weight: ${({ theme }) => theme.fontWeight.medium};
`;

export const MemberEmail = styled.span`
  display: block;
  color: ${({ theme }) => theme.color.inkSubtle};
  font-size: ${({ theme }) => theme.fontSize.xs};
`;

/** One pending request in the inbox. The message is the only thing an approver has to go on. */
export const RequestMessage = styled.p`
  margin: ${({ theme }) => `${theme.space(2)} 0 0`};
  padding-left: ${({ theme }) => theme.space(3)};
  border-left: 2px solid ${({ theme }) => theme.color.border};
  color: ${({ theme }) => theme.color.inkMuted};
  font-size: ${({ theme }) => theme.fontSize.sm};
  line-height: ${({ theme }) => theme.lineHeight.normal};
  white-space: pre-wrap;
`;

export const RequestMeta = styled.span`
  color: ${({ theme }) => theme.color.inkSubtle};
  font-size: ${({ theme }) => theme.fontSize.xs};
`;

export const InlineActions = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space(2)};
  margin-top: ${({ theme }) => theme.space(3)};
`;

/**
 * A full-width form control.
 *
 * The width goes on a wrapper rather than on the control itself: an inline `style` is forbidden
 * (doc 07 §3.2), and reaching for `.ant-select` by class name would break on an AntD upgrade. The
 * direct-child selector says what it means and survives both.
 */
export const FieldRow = styled.div`
  width: 100%;

  > * {
    width: 100%;
  }
`;
