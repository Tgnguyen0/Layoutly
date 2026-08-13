/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        graphite: {
          950: '#0A0C11',
          900: '#0F131A',
          800: '#151A23',
          700: '#1D2330',
          600: '#2A3140',
          500: '#3A4356',
        },
        ink: {
          100: '#EDEFF3',
          300: '#B7BECC',
          500: '#7C879C',
        },
        blueprint: {
          DEFAULT: '#E8A33D',
          soft: '#F0C078',
          dim: '#8A6B37',
        },
        cyan: {
          accent: '#4FD1C5',
        },
      },
      fontFamily: {
        display: ['"Sora"', 'sans-serif'],
        body: ['"Inter"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
      backgroundImage: {
        'grid-pattern':
          'linear-gradient(to right, rgba(255,255,255,0.035) 1px, transparent 1px), linear-gradient(to bottom, rgba(255,255,255,0.035) 1px, transparent 1px)',
      },
      backgroundSize: {
        grid: '28px 28px',
      },
    },
  },
  plugins: [],
}
