// EXPECT: no-restricted-imports
// No third-party HTTP client of any kind, not just Axios.
import ky from 'ky';

export const client = ky;
