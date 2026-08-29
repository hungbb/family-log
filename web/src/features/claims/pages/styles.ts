import { Card } from 'antd';
import styled from 'styled-components';

/** The claim screens' styled parts. */

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

export const FilterBar = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space(3)};
  margin-bottom: ${({ theme }) => theme.space(4)};
`;

/**
 * The family around a candidate — the line that actually identifies somebody.
 *
 * Indented behind a rule rather than run into the name, because a person scanning twenty
 * same-surnamed rows reads down the left edge and stops at the father they recognise.
 */
export const TreeContext = styled.p`
  margin: ${({ theme }) => `${theme.space(2)} 0 0`};
  padding-left: ${({ theme }) => theme.space(3)};
  border-left: 2px solid ${({ theme }) => theme.color.border};
  color: ${({ theme }) => theme.color.inkMuted};
  font-size: ${({ theme }) => theme.fontSize.sm};
  line-height: ${({ theme }) => theme.lineHeight.normal};
`;

export const Evidence = styled.p`
  margin: ${({ theme }) => `${theme.space(2)} 0 0`};
  padding-left: ${({ theme }) => theme.space(3)};
  border-left: 2px solid ${({ theme }) => theme.color.primaryBorder};
  color: ${({ theme }) => theme.color.ink};
  font-size: ${({ theme }) => theme.fontSize.sm};
  line-height: ${({ theme }) => theme.lineHeight.normal};
  white-space: pre-wrap;
`;

export const Meta = styled.span`
  color: ${({ theme }) => theme.color.inkSubtle};
  font-size: ${({ theme }) => theme.fontSize.xs};
`;

export const InlineActions = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space(2)};
  margin-top: ${({ theme }) => theme.space(3)};
`;

export const FieldRow = styled.div`
  width: 100%;

  > * {
    width: 100%;
  }
`;

export const FieldLabel = styled.label`
  display: block;
  margin-bottom: ${({ theme }) => theme.space(1)};
  color: ${({ theme }) => theme.color.inkMuted};
  font-size: ${({ theme }) => theme.fontSize.sm};
`;

/** The stack of controls inside a dialog. */
export const DialogBody = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.space(4)};
`;

/**
 * One opt-in toggle: the switch, then what turning it on actually does.
 *
 * The explanation sits next to the control rather than under the section, because these two
 * switches copy an account's photograph and name onto a genealogical record — a person should read
 * the consequence in the same glance as the switch, not below the fold.
 */
export const ToggleRow = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${({ theme }) => theme.space(3)};
`;

export const ToggleText = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.space(1)};
`;
