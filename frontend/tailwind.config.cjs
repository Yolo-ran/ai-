/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#DEDBC8',
      },
      fontFamily: {
        serif: ['"Instrument Serif"', 'serif'],
      },
      boxShadow: {
        glow: '0 30px 80px rgba(222, 219, 200, 0.12)',
      },
    },
  },
  plugins: [],
};
