import {createReadStream} from 'node:fs'
import {stat} from 'node:fs/promises'
import {extname} from 'node:path'
import {Readable} from 'node:stream'

const MIME_TYPES: Record<string, string> = {
  '.css': 'text/css; charset=utf-8',
  '.glb': 'model/gltf-binary',
  '.gltf': 'model/gltf+json',
  '.hdr': 'application/octet-stream',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.jpeg': 'image/jpeg',
  '.jpg': 'image/jpeg',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.m4a': 'audio/mp4',
  '.mp3': 'audio/mpeg',
  '.mp4': 'video/mp4',
  '.ogg': 'audio/ogg',
  '.otf': 'font/otf',
  '.pdf': 'application/pdf',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ttf': 'font/ttf',
  '.wav': 'audio/wav',
  '.wasm': 'application/wasm',
  '.webm': 'video/webm',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
}

interface ByteRange {
  start: number
  end: number
}

export async function createLocalFileResponse(target: string, request: Request) {
  let fileStats
  try {
    fileStats = await stat(target)
  } catch {
    return new Response('Not found', {status: 404})
  }
  if (!fileStats.isFile()) {
    return new Response('Not found', {status: 404})
  }

  const headers = new Headers({
    'Accept-Ranges': 'bytes',
    'Content-Type': contentTypeFor(target),
  })
  const range = parseByteRange(request.headers.get('range'), fileStats.size)
  if (request.headers.has('range') && !range) {
    headers.set('Content-Range', `bytes */${fileStats.size}`)
    return new Response(null, {status: 416, headers})
  }

  const start = range?.start ?? 0
  const end = range?.end ?? fileStats.size - 1
  const contentLength = Math.max(0, end - start + 1)
  headers.set('Content-Length', String(contentLength))
  if (range) {
    headers.set('Content-Range', `bytes ${start}-${end}/${fileStats.size}`)
  }
  if (request.method === 'HEAD') {
    return new Response(null, {status: range ? 206 : 200, headers})
  }

  const body = Readable.toWeb(createReadStream(target, {start, end})) as unknown as BodyInit
  return new Response(body, {status: range ? 206 : 200, headers})
}

export function parseByteRange(value: string | null, size: number): ByteRange | null {
  if (!value || size <= 0) return null

  const match = /^bytes=(\d*)-(\d*)$/.exec(value.trim())
  if (!match || (!match[1] && !match[2])) return null

  const requestedStart = match[1] ? Number(match[1]) : Math.max(size - Number(match[2]), 0)
  const requestedEnd = match[2] && match[1] ? Number(match[2]) : size - 1
  if (!Number.isSafeInteger(requestedStart)
      || !Number.isSafeInteger(requestedEnd)
      || requestedStart < 0
      || requestedStart >= size
      || requestedEnd < requestedStart) {
    return null
  }

  return {start: requestedStart, end: Math.min(requestedEnd, size - 1)}
}

function contentTypeFor(target: string) {
  return MIME_TYPES[extname(target).toLowerCase()] ?? 'application/octet-stream'
}
