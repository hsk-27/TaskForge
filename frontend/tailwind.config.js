/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Inter"', '-apple-system', 'BlinkMacSystemFont', '"Segoe UI"', 'Roboto', 'sans-serif'],
        mono: ['"JetBrains Mono"', '"SF Mono"', 'Menlo', 'Consolas', 'monospace'],
      },
      colors: {
        canvas: '#08090a',
        surface: {
          1: '#0f1012',
          2: '#141518',
          3: '#1a1b1f',
          4: '#222329',
        },
        border: {
          subtle: 'rgba(255, 255, 255, 0.05)',
          default: 'rgba(255, 255, 255, 0.08)',
          strong: 'rgba(255, 255, 255, 0.16)',
        },
        brand: {
          DEFAULT: '#4f46e5',
          hover: '#4338ca',
          subtle: 'rgba(99, 102, 241, 0.12)',
        }
      }
    },
  },
  plugins: [],
}
