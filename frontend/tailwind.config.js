// Design tokens (UX-socle §5.1) : alias de la palette Tailwind, aucune couleur écrite en dur.
// Identité visuelle 2 : indigo (marque), sarcelle et orange (accents), fonds lavande — contrastes AA conservés.
const colors = require('tailwindcss/colors');
const defaultTheme = require('tailwindcss/defaultTheme');

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Inter Variable"', ...defaultTheme.fontFamily.sans],
      },
      colors: {
        primary: { DEFAULT: colors.indigo[600], hover: colors.indigo[700], soft: colors.indigo[50], strong: colors.indigo[900] },
        brand: { DEFAULT: colors.teal[600], soft: colors.teal[50] },
        accent: { DEFAULT: colors.orange[600], soft: colors.orange[50] },
        success: { DEFAULT: colors.emerald[700], soft: colors.emerald[50] },
        warning: { DEFAULT: colors.amber[800], soft: colors.amber[50] },
        danger: { DEFAULT: colors.rose[700], soft: colors.rose[50] },
        info: { DEFAULT: colors.sky[800], soft: colors.sky[50] },
        surface: { DEFAULT: colors.white, alt: colors.slate[50] },
        lavender: { DEFAULT: colors.violet[100], soft: colors.violet[50] },
        sidebar: { DEFAULT: colors.indigo[950], hover: colors.indigo[900], muted: colors.indigo[300] },
        border: colors.slate[200],
        muted: colors.slate[500],
        ink: colors.slate[900],
        focus: colors.indigo[500],
      },
      boxShadow: {
        card: '0 1px 2px rgb(15 23 42 / 0.04), 0 4px 16px -4px rgb(15 23 42 / 0.08)',
        lift: '0 12px 32px -8px rgb(49 46 129 / 0.25)',
        cover: '4px 6px 14px -4px rgb(15 23 42 / 0.35)',
      },
      borderRadius: {
        xl: '0.875rem',
        '2xl': '1.25rem',
      },
    },
  },
  plugins: [],
};
