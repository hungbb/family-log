import { Card } from 'antd';
import styled from 'styled-components';

export const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.space(6)};
`;

export const PageHeader = styled.header`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.space(2)};
`;

export const PageTitle = styled.h1`
  font-size: ${({ theme }) => theme.fontSize.xxl};
`;

export const PageSubtitle = styled.p`
  max-width: 62ch;
  color: ${({ theme }) => theme.color.inkMuted};
`;

export const SectionCard = styled(Card)`
  border-color: ${({ theme }) => theme.color.border};
  box-shadow: ${({ theme }) => theme.shadow.card};
`;

export const FormGrid = styled.div`
  max-width: 460px;
`;

export const SessionRow = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.space(1)};
`;

export const SessionAgent = styled.span`
  color: ${({ theme }) => theme.color.ink};
  font-size: ${({ theme }) => theme.fontSize.sm};
  overflow-wrap: anywhere;
`;

export const SessionMeta = styled.span`
  color: ${({ theme }) => theme.color.inkSubtle};
  font-size: ${({ theme }) => theme.fontSize.xs};
  font-family: ${({ theme }) => theme.font.mono};
`;

export const DangerZone = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: ${({ theme }) => theme.space(4)};
`;

export const DangerCopy = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.space(1)};
  max-width: 52ch;
`;

export const DangerTitle = styled.span`
  color: ${({ theme }) => theme.color.ink};
  font-weight: ${({ theme }) => theme.fontWeight.medium};
`;

export const DangerBody = styled.span`
  color: ${({ theme }) => theme.color.inkMuted};
  font-size: ${({ theme }) => theme.fontSize.sm};
`;
