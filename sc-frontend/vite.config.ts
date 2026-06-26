import {fileURLToPath, URL} from 'node:url'

import vue from '@vitejs/plugin-vue'
import {defineConfig, loadEnv} from 'vite'

export default defineConfig(({mode}) => {
    const env = loadEnv(mode, process.cwd(), '')
    const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:39080'

    return {
        plugins: [vue()],
        resolve: {
            alias: {
                '@': fileURLToPath(new URL('./src', import.meta.url)),
                'vue-demi': 'vue-demi/lib/v3/index.mjs',
            },
        },
        server: {
            port: 5173,
            proxy: {
                '/api': {
                    target: apiProxyTarget,
                    changeOrigin: true,
                    ws: true,
                },
            },
        },
        build: {
            chunkSizeWarningLimit: 3000,
            rolldownOptions: {
                output: {
                    codeSplitting: true,
                },
            },
        },
    }
})
