import type { Config } from 'tailwindcss';

// Tokens del prototipo aprobado (alcanfores-diseno/js/tw-config.js y css/alcanfores.css :root). Mismos hex.
// Responsive: md = 768 (tablet) · lg = 1024 (escritorio) · xl = 1280 (amplio), igual que los cortes del prototipo.
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  corePlugins: { container: false },
  theme: {
    extend: {
      colors: {
        noche: { DEFAULT: '#091A2A', 2: '#12283D', 3: '#1D3A55' },
        verde: { DEFAULT: '#27811F', hover: '#1F6C18', grass: '#37A232', profundo: '#0B5D33', suave: '#B9DCC6', menta: '#E8F3EC' },
        grass: '#37A232',
        profundo: '#0B5D33',
        suave: '#B9DCC6',
        menta: '#E8F3EC',
        amarillo: { DEFAULT: '#F2B705', tinte: '#FDF1C4', ocre: '#8A6A00' },
        gris: { DEFAULT: '#5B6B63', claro: '#ECEFED' },
        rojo: { DEFAULT: '#C62828', hover: '#A82020', tinte: '#FDECEC', texto: '#A31F1F' },
        borde: { DEFAULT: '#DDE7E1', campo: '#7A8A82', panel: '#E3EAE6' },
        texto: { DEFAULT: '#091A2A', suave: '#5B6B63', 3: '#6B7A73' },
        superficie: { DEFAULT: '#FFFFFF', suave: '#F4F8F5', admin: '#F5F7F6' },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
        titular: ['"Barlow Semi Condensed"', '"Arial Narrow"', 'system-ui', 'sans-serif'],
      },
      borderRadius: { 8: '8px', 10: '10px', 12: '12px', 14: '14px', 16: '16px', 24: '24px', pill: '999px' },
      boxShadow: {
        1: '0 1px 2px rgba(9,26,42,.06)',
        2: '0 1px 2px rgba(9,26,42,.04), 0 6px 16px -6px rgba(9,26,42,.10)',
        flota: '0 14px 32px -12px rgba(9,26,42,.28)',
        modal: '0 28px 64px -18px rgba(9,26,42,.40)',
        barra: '0 -8px 24px -12px rgba(9,26,42,.18)',
      },
      maxWidth: { contenido: '1200px' },
      keyframes: {
        entrar: { from: { opacity: '0', transform: 'translateY(6px)' }, to: { opacity: '1', transform: 'none' } },
        voltear: { from: { transform: 'rotateY(0)' }, to: { transform: 'rotateY(180deg)' } },
      },
      animation: { entrar: 'entrar .18s ease-out' },
    },
  },
  plugins: [],
} satisfies Config;
