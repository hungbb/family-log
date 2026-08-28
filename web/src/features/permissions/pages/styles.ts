import { Card } from 'antd';
import styled from 'styled-components';

/** The permissions screen's styled parts. */

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

export const HolderName = styled.span`
  color: ${({ theme }) => theme.color.ink};
  font-size: ${({ theme }) => theme.fontSize.md};
  font-weight: ${({ theme }) => theme.fontWeight.medium};
`;

export const HolderEmail = styled.span`
  display: block;
  color: ${({ theme }) => theme.color.inkSubtle};
  font-size: ${({ theme }) => theme.fontSize.xs};
`;

export const Meta = styled.span`
  color: ${({ theme }) => theme.color.inkSubtle};
  font-size: ${({ theme }) => theme.fontSize.xs};
`;

/**
 * The plain-language summary of what somebody may do (F3.5).
 *
 * Set in the serif face the rest of the product uses for prose rather than for chrome: this is a
 * sentence a family reads and argues about, not a field label.
 */
export const ScopeSummary = styled.p`
  margin: ${({ theme }) => `${theme.space(2)} 0 0`};
  padding-left: ${({ theme }) => theme.space(3)};
  border-left: 2px solid ${({ theme }) => theme.color.border};
  color: ${({ theme }) => theme.color.inkMuted};
  font-size: ${({ theme }) => theme.fontSize.sm};
  line-height: ${({ theme }) => theme.lineHeight.normal};
`;

/** A full-width form control; see the note on `FieldRow` in the members styles. */
export const FieldRow = styled.div`
  width: 100%;

  > * {
    width: 100%;
  }
`;

/** One member's grants inside the drawer, stacked with room to breathe. */
export const DrawerSection = styled.section`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.space(3)};
`;

export const DrawerSectionTitle = styled.h3`
  margin: 0;
  color: ${({ theme }) => theme.color.ink};
  font-size: ${({ theme }) => theme.fontSize.md};
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
`;
