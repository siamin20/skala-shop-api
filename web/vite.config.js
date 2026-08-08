import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * 프론트엔드 빌드 설정.
 *
 * 개발 서버(5173)와 API 서버(8080)가 포트가 달라 브라우저는 이를 다른 출처로 본다.
 * 그대로 두면 모든 요청이 CORS에 막힌다.
 *
 * 서버에 CORS 허용을 넣는 방법도 있지만 프록시를 골랐다. 두 가지 이유다.
 *   1. 서버가 개발 환경 사정을 알 필요가 없다. 운영에서는 같은 출처로 서비스하므로
 *      CORS 설정은 개발용으로만 존재하게 되고, 그런 설정은 운영에 남아 위험해지기 쉽다
 *   2. 리프레시 토큰 쿠키가 SameSite=Strict다. 출처가 다르면 브라우저가 쿠키를
 *      아예 보내지 않아 토큰 갱신이 동작하지 않는다. 프록시를 쓰면 같은 출처가 된다
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
