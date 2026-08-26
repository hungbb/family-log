/**
 * The auth feature's public surface.
 *
 * Everything else in `features/auth` is internal: another feature that reaches past this file has
 * quietly coupled itself to how these screens are built rather than to what they are (doc 07 §2).
 */
export { ForgotPasswordPage } from './pages/ForgotPasswordPage';
export { LoginPage } from './pages/LoginPage';
export { RegisterPage } from './pages/RegisterPage';
export { ResetPasswordPage } from './pages/ResetPasswordPage';
export { VerifyEmailPage } from './pages/VerifyEmailPage';
