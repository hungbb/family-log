// EXPECT: no-restricted-imports
// The draft forbids Axios outright; all HTTP goes through src/api/client.ts.
import axios from 'axios';

export const client = axios;
