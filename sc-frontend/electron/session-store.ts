import {existsSync} from 'node:fs'
import {mkdir, readFile, rm, writeFile} from 'node:fs/promises'
import {dirname} from 'node:path'

export interface SecretProtector {
  isEncryptionAvailable(): boolean
  encryptString(value: string): Buffer
  decryptString(value: Buffer): string
}

export async function writeEncryptedRefreshToken(
  target: string,
  refreshToken: string,
  protector: SecretProtector,
) {
  if (!protector.isEncryptionAvailable()) {
    throw new Error('Windows 安全存储不可用，无法保存登录状态。')
  }
  if (!refreshToken || refreshToken.length > 8192) {
    throw new Error('刷新令牌无效。')
  }

  await mkdir(dirname(target), {recursive: true})
  await writeFile(target, protector.encryptString(refreshToken))
}

export async function readEncryptedRefreshToken(target: string, protector: SecretProtector) {
  if (!protector.isEncryptionAvailable() || !existsSync(target)) {
    return ''
  }

  try {
    return protector.decryptString(await readFile(target))
  } catch {
    await rm(target, {force: true})
    return ''
  }
}
