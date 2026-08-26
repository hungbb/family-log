// EXPECT: no-restricted-syntax
// styled-components owns the prop name `theme`. Ant Design's `Layout.Sider` and `Menu` take a
// `theme` prop of their own, and passing one to a styled wrapper replaces the design tokens with
// the string "light" — every `theme.color.*` in the component then throws at render.
import { Layout } from 'antd';
import styled from 'styled-components';

const Sider = styled(Layout.Sider)`
  border-right: 1px solid ${({ theme }) => theme.color.border};
`;

export function Navigation() {
  return <Sider theme="light">Broken tokens</Sider>;
}
