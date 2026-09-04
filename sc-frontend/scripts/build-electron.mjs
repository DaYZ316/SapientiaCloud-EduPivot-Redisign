import {rm} from 'node:fs/promises'
import {dirname, resolve} from 'node:path'
import {fileURLToPath} from 'node:url'

import {build} from 'esbuild'

const scriptsDirectory = dirname(fileURLToPath(import.meta.url))
const projectDirectory = resolve(scriptsDirectory, '..')
const outputDirectory = resolve(projectDirectory, 'dist-electron')
const profilePublicKey = process.env.EDUPIVOT_DESKTOP_PROFILE_PUBLIC_KEY ?? ''

await rm(outputDirectory, {recursive: true, force: true})

await Promise.all([
  build({
    entryPoints: [resolve(projectDirectory, 'electron/main.ts')],
    outfile: resolve(outputDirectory, 'main.cjs'),
    bundle: true,
    format: 'cjs',
    platform: 'node',
    target: 'node22',
    external: ['electron'],
    define: {
      __EDUPIVOT_PROFILE_PUBLIC_KEY__: JSON.stringify(profilePublicKey),
    },
  }),
  build({
    entryPoints: [resolve(projectDirectory, 'electron/preload.ts')],
    outfile: resolve(outputDirectory, 'preload.cjs'),
    bundle: true,
    format: 'cjs',
    platform: 'node',
    target: 'node22',
    external: ['electron'],
  }),
])
