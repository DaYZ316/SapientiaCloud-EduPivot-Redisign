import axios from 'axios'

import {request} from '@/shared/api/request'
import type {CreateUploadRequest, DownloadUrlResponse, FileAsset, UploadTicket} from '@/features/storage/types/storage'

export function createUploadTicket(data: CreateUploadRequest) {
  return request<UploadTicket>({
    method: 'POST',
    url: '/api/storage/uploads',
    data,
  })
}

export function completeUpload(objectId: string) {
  return request<FileAsset>({
    method: 'POST',
    url: `/api/storage/uploads/${objectId}/complete`,
  })
}

export function getDownloadUrl(fileId: string) {
  return request<DownloadUrlResponse>({
    method: 'GET',
    url: `/api/storage/files/${fileId}/download-url`,
  })
}

export function convertFile(fileId: string) {
  return request<DownloadUrlResponse>({
    method: 'POST',
    url: `/api/storage/files/${fileId}/convert`,
  })
}

export function deleteStorageFile(fileId: string) {
  return request<void>({
    method: 'DELETE',
    url: `/api/storage/files/${fileId}`,
  })
}

export async function uploadToMinio(
  ticket: UploadTicket,
  file: File,
  onProgress?: (progress: number) => void,
) {
  const formData = new FormData()
  Object.entries(ticket.formData).forEach(([key, value]) => {
    formData.append(key, value)
  })
  formData.append('file', file)

  await axios.post(ticket.uploadUrl, formData, {
    headers: ticket.headers,
    onUploadProgress: (event) => {
      if (!event.total || !onProgress) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
}
