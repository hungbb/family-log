// EXPECT: no-restricted-imports
// "NEVER use Tailwind CSS" — draft §3.2.
import tailwindcss from 'tailwindcss';

export const config = tailwindcss;
