export type StorageUsage =
  | 'USER_AVATAR'
  | 'COURSE_COVER'
  | 'FORUM_IMAGE'
  | 'COURSE_PUBLIC_FILE'
  | 'COURSE_PRIVATE_FILE'
  | 'AI_FILE'

export type StorageScopeType = 'USER' | 'COURSE' | 'AI'

export type StorageBucketType = 'MEDIA' | 'COURSE_PUBLIC' | 'COURSE_PRIVATE' | 'AI'

export interface CreateUploadRequest {
  usage: StorageUsage
  scopeType: StorageScopeType
  scopeId?: string | null
  fileName: string
  contentType: string
  sizeBytes: number
  sha256?: string | null
  bucketType?: StorageBucketType | null
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
