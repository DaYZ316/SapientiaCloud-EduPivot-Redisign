export type StorageUsage =
  | 'USER_AVATAR'
  | 'COURSE_COVER'
  | 'COURSE_PUBLIC_FILE'
  | 'COURSE_PRIVATE_FILE'
  | 'AI_FILE'

export type StorageScopeType = 'USER' | 'COURSE' | 'AI'

export interface CreateUploadRequest {
  usage: StorageUsage
  scopeType: StorageScopeType
  scopeId?: string | null
  fileName: string
  contentType: string
  sizeBytes: number
  sha256?: string | null
}

export interface UploadTicket {
  objectId: string
  uploadUrl: string
  method: 'POST'
  formData: Record<string, string>
  headers: Record<string, string>
  expiresAt: string
  maxSizeBytes: number
}

export interface FileAsset {
  id: string
  fileName: string
  contentType: string
  sizeBytes: number
  usage: StorageUsage
  visibility: string
  url: string | null
  uploadedAt: string | null
}

export interface DownloadUrlResponse {
  url: string
  expiresAt: string | null
}
