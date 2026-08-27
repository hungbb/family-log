import { Card, Tree } from 'antd';
import styled from 'styled-components';

/** The branch page's styled parts. */

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

/**
 * The branch tree.
 *
 * Rows are given room to breathe — a *gia phả*'s branch names are long, and AntD's default row
 * height crowds them. The drag cursor is set on the whole tree rather than per node so it does not
 * flicker between rows during a drag.
 */
export const BranchTree = styled(Tree)`
  .ant-tree-treenode {
    width: 100%;
    padding-block: ${({ theme }) => theme.space(1)};
  }

  .ant-tree-node-content-wrapper {
    width: 100%;
  }

  .ant-tree-node-selected {
    background: ${({ theme }) => theme.color.primarySubtle} !important;
  }
`;

/** One row: the name on the left, the counts and actions on the right. */
export const BranchRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space(3)};
  width: 100%;
`;

export const BranchName = styled.span<{ $archived: boolean }>`
  color: ${({ theme, $archived }) => ($archived ? theme.color.inkSubtle : theme.color.ink)};
  font-size: ${({ theme }) => theme.fontSize.md};
  font-weight: ${({ theme }) => theme.fontWeight.medium};
  text-decoration: ${({ $archived }) => ($archived ? 'line-through' : 'none')};
`;

export const BranchMeta = styled.span`
  color: ${({ theme }) => theme.color.inkSubtle};
  font-size: ${({ theme }) => theme.fontSize.xs};
`;

/** The branch code, monospaced because it is meant to be copied and read aloud. */
export const BranchCodeTag = styled.span`
  padding: ${({ theme }) => `0 ${theme.space(1.5)}`};
  border: 1px solid ${({ theme }) => theme.color.border};
  border-radius: ${({ theme }) => theme.radius.sm};
  background: ${({ theme }) => theme.color.surfaceAlt};
  color: ${({ theme }) => theme.color.inkMuted};
  font-family: ${({ theme }) => theme.font.mono};
  font-size: ${({ theme }) => theme.fontSize.xs};
`;

export const RowActions = styled.span`
  display: inline-flex;
  gap: ${({ theme }) => theme.space(1)};
  margin-left: auto;
`;
