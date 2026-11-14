import { Card } from 'antd';
import styled from 'styled-components';

/** The administration screens' styled parts. */

export const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.space(6)};
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

/** Keeps the status filter from collapsing to the width of its placeholder. */
export const StatusFilter = styled.div`
  min-width: 200px;

  > * {
    width: 100%;
  }
`;

export const UserName = styled.span`
  color: ${({ theme }) => theme.color.ink};
  font-size: ${({ theme }) => theme.fontSize.md};
  font-weight: ${({ theme }) => theme.fontWeight.medium};
`;

export const UserEmail = styled.span`
  display: block;
  color: ${({ theme }) => theme.color.inkSubtle};
  font-size: ${({ theme }) => theme.fontSize.xs};
`;
