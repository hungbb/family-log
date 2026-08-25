import { Card } from 'antd';
import styled, { css, keyframes } from 'styled-components';

type BadgeTone = 'up' | 'down' | 'pending';

const pulse = keyframes`
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
`;

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

export const StatusCard = styled(Card)`
  border-color: ${({ theme }) => theme.color.border};
  box-shadow: ${({ theme }) => theme.shadow.card};
`;

export const StatusRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${({ theme }) => theme.space(4)};
`;

const toneStyles = {
  up: css`
    background: ${({ theme }) => theme.color.successSubtle};
    color: ${({ theme }) => theme.color.success};
  `,
  down: css`
    background: ${({ theme }) => theme.color.dangerSubtle};
    color: ${({ theme }) => theme.color.danger};
  `,
  pending: css`
    background: ${({ theme }) => theme.color.surfaceSunken};
    color: ${({ theme }) => theme.color.inkMuted};
  `,
} as const;

export const Badge = styled.span<{ $tone: BadgeTone }>`
  display: inline-flex;
  align-items: center;
  gap: ${({ theme }) => theme.space(2)};
  padding: ${({ theme }) => `${theme.space(1.5)} ${theme.space(3)}`};
  border-radius: ${({ theme }) => theme.radius.pill};
  font-size: ${({ theme }) => theme.fontSize.sm};
  font-weight: ${({ theme }) => theme.fontWeight.medium};
  ${({ $tone }) => toneStyles[$tone]};
`;

export const Dot = styled.span<{ $tone: BadgeTone }>`
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  ${({ $tone }) =>
    $tone === 'pending' &&
    css`
      animation: ${pulse} 1.2s ease-in-out infinite;
    `};
`;

export const DetailList = styled.dl`
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: ${({ theme }) => `${theme.space(2)} ${theme.space(5)}`};
  margin: 0;
  font-size: ${({ theme }) => theme.fontSize.sm};

  dt {
    color: ${({ theme }) => theme.color.inkSubtle};
  }

  dd {
    margin: 0;
    color: ${({ theme }) => theme.color.ink};
    font-family: ${({ theme }) => theme.font.mono};
    font-size: ${({ theme }) => theme.fontSize.xs};
    overflow-wrap: anywhere;
  }
`;

export const Hint = styled.p`
  color: ${({ theme }) => theme.color.inkMuted};
  font-size: ${({ theme }) => theme.fontSize.sm};
`;

export const Details = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.space(4)};
`;
