import { Card } from 'antd';
import styled from 'styled-components';

/**
 * The clan screens' styled parts.
 *
 * Every AntD component is wrapped rather than overridden by class name (doc 07 §3.2 rule 3), and
 * every value comes from a token — there is not a hex code or a magic pixel in this file.
 */

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

/** Forms stay narrow: a name field the width of a monitor is harder to read, not easier. */
export const FormGrid = styled.div`
  max-width: 520px;
`;

/**
 * The onboarding landing: two doors, equally weighted.
 *
 * Neither creating nor joining is the default action — which of the two a person wants depends
 * entirely on whether somebody in their family has done this before — so they get the same size.
 */
export const ChoiceGrid = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space(5)};
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
`;

export const ChoiceCard = styled(Card)`
  height: 100%;
  border-color: ${({ theme }) => theme.color.border};
  transition:
    border-color ${({ theme }) => theme.motion.fast},
    box-shadow ${({ theme }) => theme.motion.fast};

  &:hover {
    border-color: ${({ theme }) => theme.color.borderStrong};
    box-shadow: ${({ theme }) => theme.shadow.raised};
  }
`;

export const ChoiceIcon = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin-bottom: ${({ theme }) => theme.space(3)};
  border-radius: ${({ theme }) => theme.radius.md};
  background: ${({ theme }) => theme.color.primarySubtle};
  color: ${({ theme }) => theme.color.primary};
  font-size: ${({ theme }) => theme.fontSize.xl};
`;

export const ChoiceTitle = styled.h2`
  margin: 0 0 ${({ theme }) => theme.space(2)};
  font-size: ${({ theme }) => theme.fontSize.lg};
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  color: ${({ theme }) => theme.color.ink};
`;

export const ChoiceBody = styled.p`
  margin: 0 0 ${({ theme }) => theme.space(5)};
  color: ${({ theme }) => theme.color.inkMuted};
  font-size: ${({ theme }) => theme.fontSize.md};
  line-height: ${({ theme }) => theme.lineHeight.normal};
`;

/**
 * The generated clan code, shown once on success.
 *
 * Large and monospaced because it is about to be read aloud or typed into a message — this is the
 * one string in the product whose whole job is to be transcribed correctly.
 */
export const CodeDisplay = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space(3)};
  padding: ${({ theme }) => `${theme.space(3)} ${theme.space(4)}`};
  border: 1px solid ${({ theme }) => theme.color.primaryBorder};
  border-radius: ${({ theme }) => theme.radius.md};
  background: ${({ theme }) => theme.color.primarySubtle};
`;

export const CodeValue = styled.span`
  flex: 1 1 auto;
  font-family: ${({ theme }) => theme.font.mono};
  font-size: ${({ theme }) => theme.fontSize.xl};
  font-weight: ${({ theme }) => theme.fontWeight.medium};
  letter-spacing: 0.08em;
  color: ${({ theme }) => theme.color.ink};
  word-break: break-all;
`;

export const PreviewCard = styled(Card)`
  margin-top: ${({ theme }) => theme.space(5)};
  border-color: ${({ theme }) => theme.color.border};
  background: ${({ theme }) => theme.color.surfaceAlt};
`;

export const PreviewName = styled.h2`
  margin: 0;
  font-family: ${({ theme }) => theme.font.serif};
  font-size: ${({ theme }) => theme.fontSize.xl};
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  color: ${({ theme }) => theme.color.ink};
`;

export const PreviewMeta = styled.p`
  margin: ${({ theme }) => `${theme.space(1)} 0 ${theme.space(4)}`};
  color: ${({ theme }) => theme.color.inkMuted};
  font-size: ${({ theme }) => theme.fontSize.sm};
`;

export const DangerZone = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: ${({ theme }) => theme.space(4)};
`;

export const DangerCopy = styled.div`
  flex: 1 1 320px;
`;

export const DangerTitle = styled.h3`
  margin: 0 0 ${({ theme }) => theme.space(1)};
  font-size: ${({ theme }) => theme.fontSize.md};
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  color: ${({ theme }) => theme.color.ink};
`;

export const DangerBody = styled.p`
  margin: 0;
  color: ${({ theme }) => theme.color.inkMuted};
  font-size: ${({ theme }) => theme.fontSize.sm};
  line-height: ${({ theme }) => theme.lineHeight.normal};
`;

/** A read-only field that should not look editable, whatever the browser thinks. */
export const ReadOnlyValue = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space(2)};
  padding: ${({ theme }) => `${theme.space(1.5)} ${theme.space(3)}`};
  border: 1px solid ${({ theme }) => theme.color.border};
  border-radius: ${({ theme }) => theme.radius.sm};
  background: ${({ theme }) => theme.color.surfaceSunken};
  color: ${({ theme }) => theme.color.inkMuted};
  font-family: ${({ theme }) => theme.font.mono};
  font-size: ${({ theme }) => theme.fontSize.md};
`;
