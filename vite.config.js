import { defineConfig } from 'vite';

export default defineConfig({
  base: '/meteox/', // served from https://jrechet.github.io/meteox/
  server: { port: 5180, open: false },
  build: { target: 'es2022', cssMinify: true },
});
