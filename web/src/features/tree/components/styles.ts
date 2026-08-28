import { Card, Table } from 'antd';
import styled from 'styled-components';

/** The lineage screens' styled parts (doc 07 §3.2 — all styling lives in styled-components). */

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
 * The list view of the tree (F4.6).
 *
 * The permanent accessible alternative to the canvas Phase 5 brings, not a placeholder: NFR-7 wants
 * a keyboard- and screen-reader-navigable route to every node, and a table is that. It scrolls
 * horizontally on a narrow screen rather than crushing the columns.
 */
export const TreeTable = styled(Table)`
  .ant-table-thead > tr > th {
    background: ${({ theme }) => theme.color.surfaceAlt};
    color: ${({ theme }) => theme.color.inkMuted};
    font-weight: ${({ theme }) => theme.fontWeight.medium};
  }
` as typeof Table;

/** A person's name in a row, dimmed when the caller cannot edit them (F4.8). */
export const PersonName = styled.span<{ $readOnly?: boolean }>`
  color: ${({ theme, $readOnly }) => ($readOnly ? theme.color.inkMuted : theme.color.ink)};
  font-size: ${({ theme }) => theme.fontSize.md};
  font-weight: ${({ theme }) => theme.fontWeight.medium};
`;

export const PersonMeta = styled.span`
  display: block;
  color: ${({ theme }) => theme.color.inkSubtle};
  font-size: ${({ theme }) => theme.fontSize.xs};
`;

export const RowActions = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.space(1)};
  justify-content: flex-end;
`;

/** The year/month/day inputs of `PartialDateInput`, wrapping on a narrow screen. */
export const DateFieldRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${({ theme }) => theme.space(2)};
`;

export const DateHint = styled.span`
  color: ${({ theme }) => theme.color.inkSubtle};
  font-size: ${({ theme }) => theme.fontSize.xs};
`;

/** A block of related people in the detail drawer — parents, spouses, children, siblings. */
export const RelationGroup = styled.section`
  margin-top: ${({ theme }) => theme.space(5)};
`;

export const RelationTitle = styled.h3`
  margin: ${({ theme }) => `0 0 ${theme.space(2)}`};
  color: ${({ theme }) => theme.color.inkMuted};
  font-size: ${({ theme }) => theme.fontSize.sm};
  font-weight: ${({ theme }) => theme.fontWeight.medium};
  text-transform: uppercase;
  letter-spacing: 0.06em;
`;

export const RelationRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${({ theme }) => theme.space(2)};
  padding: ${({ theme }) => `${theme.space(2)} 0`};
  border-bottom: 1px solid ${({ theme }) => theme.color.border};

  &:last-child {
    border-bottom: none;
  }
`;

export const RelationPerson = styled.button`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: ${({ theme }) => theme.space(0.5)};
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  text-align: left;

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.color.primary};
    outline-offset: 2px;
  }
`;

/** The note body in the drawer. Sanitized server-side; rendered as the family wrote it. */
export const NoteBody = styled.div`
  color: ${({ theme }) => theme.color.ink};
  font-size: ${({ theme }) => theme.fontSize.md};
  line-height: ${({ theme }) => theme.lineHeight.normal};

  p {
    margin: ${({ theme }) => `0 0 ${theme.space(2)}`};
  }

  ul,
  ol {
    margin: ${({ theme }) => `0 0 ${theme.space(2)}`};
    padding-left: ${({ theme }) => theme.space(5)};
  }
`;

export const StatGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: ${({ theme }) => theme.space(3)};
`;
