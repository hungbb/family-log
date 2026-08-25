import type { ThemeConfig } from 'antd';

import { tokens } from './tokens';

/**
 * The tokens, expressed in Ant Design's vocabulary.
 *
 * Everything here is a translation — no value is invented. If a colour looks wrong in an AntD
 * component, the fix belongs in `tokens.ts`, not here.
 *
 * The component-level overrides at the bottom are the ones that separate this from stock Ant
 * Design: flatter controls, hairline borders, and no drop shadow on anything that is not floating.
 */
export const antdTheme: ThemeConfig = {
  token: {
    colorPrimary: tokens.color.primary,
    colorPrimaryHover: tokens.color.primaryHover,
    colorPrimaryActive: tokens.color.primaryActive,
    colorPrimaryBg: tokens.color.primarySubtle,
    colorPrimaryBorder: tokens.color.primaryBorder,

    colorSuccess: tokens.color.success,
    colorWarning: tokens.color.warning,
    colorError: tokens.color.danger,
    colorInfo: tokens.color.info,

    colorText: tokens.color.ink,
    colorTextSecondary: tokens.color.inkMuted,
    colorTextTertiary: tokens.color.inkSubtle,
    colorTextQuaternary: tokens.color.inkSubtle,

    colorBgBase: tokens.color.surface,
    colorBgContainer: tokens.color.surface,
    colorBgElevated: tokens.color.surface,
    colorBgLayout: tokens.color.surfaceAlt,
    colorBgSpotlight: tokens.color.surfaceInverse,

    colorBorder: tokens.color.borderStrong,
    colorBorderSecondary: tokens.color.border,

    fontFamily: tokens.font.body,
    fontFamilyCode: tokens.font.mono,
    fontSize: 14,
    fontSizeSM: 13,
    fontSizeLG: 16,
    fontSizeHeading1: 28,
    fontSizeHeading2: 22,
    fontSizeHeading3: 18,
    fontSizeHeading4: 16,
    fontSizeHeading5: 14,
    lineHeight: tokens.lineHeight.normal,

    borderRadius: 10,
    borderRadiusSM: 6,
    borderRadiusLG: 16,

    controlHeight: 36,
    controlHeightSM: 28,
    controlHeightLG: 44,

    // Minimalism is mostly restraint about shadows. Only genuinely floating surfaces get one.
    boxShadow: tokens.shadow.card,
    boxShadowSecondary: tokens.shadow.float,
    boxShadowTertiary: tokens.shadow.card,

    motionDurationMid: '0.2s',
    wireframe: false,
  },

  components: {
    Button: {
      // Flat by default; a shadow on a button is a 2014 affordance.
      primaryShadow: 'none',
      defaultShadow: 'none',
      dangerShadow: 'none',
      fontWeight: tokens.fontWeight.medium,
      paddingInline: 16,
    },
    Card: {
      colorBorderSecondary: tokens.color.border,
      paddingLG: 20,
    },
    Layout: {
      bodyBg: tokens.color.surfaceAlt,
      headerBg: tokens.color.surface,
      headerHeight: 56,
      headerPadding: '0 20px',
      siderBg: tokens.color.surface,
      triggerBg: tokens.color.surfaceSunken,
      triggerColor: tokens.color.inkMuted,
    },
    Menu: {
      itemBg: 'transparent',
      subMenuItemBg: 'transparent',
      itemSelectedBg: tokens.color.primarySubtle,
      itemSelectedColor: tokens.color.primary,
      itemHoverBg: tokens.color.surfaceSunken,
      itemColor: tokens.color.inkMuted,
      itemHeight: 38,
      itemMarginInline: 8,
      itemBorderRadius: 8,
      activeBarWidth: 0,
      activeBarBorderWidth: 0,
    },
    Table: {
      headerBg: tokens.color.surfaceAlt,
      headerColor: tokens.color.inkMuted,
      headerSplitColor: 'transparent',
      rowHoverBg: tokens.color.surfaceAlt,
      borderColor: tokens.color.border,
      cellPaddingBlock: 12,
    },
    Input: {
      activeShadow: tokens.shadow.focus,
      hoverBorderColor: tokens.color.borderStrong,
    },
    Select: {
      optionSelectedBg: tokens.color.primarySubtle,
    },
    Tag: {
      defaultBg: tokens.color.surfaceSunken,
      defaultColor: tokens.color.inkMuted,
      borderRadiusSM: 999,
    },
    Tooltip: {
      colorBgSpotlight: tokens.color.surfaceInverse,
      borderRadius: 8,
    },
    Modal: {
      borderRadiusLG: 16,
    },
    Drawer: {
      paddingLG: 20,
    },
  },
};
