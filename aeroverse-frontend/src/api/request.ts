import axios, { AxiosError } from 'axios'
import type { AxiosRequestConfig } from 'axios'

import { i18n } from '@/i18n'
import type { ApiResponse } from '@/types/common'

const SUCCESS_CODE = 0

export class ApiError extends Error {
  readonly code?: number

  constructor(message: string, code?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 12000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('aeroverse.accessToken')

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await http.request<ApiResponse<T>>(config)
    const payload = response.data

    if (payload.code !== SUCCESS_CODE) {
      throw new ApiError(payload.message || i18n.global.t('common.request.failed'), payload.code)
    }

    return payload.data
  } catch (error) {
    if (error instanceof ApiError) {
      throw error
    }

    const axiosError = error as AxiosError<ApiResponse<unknown>>
    const message =
      axiosError.response?.data?.message ||
      axiosError.message ||
      i18n.global.t('common.request.networkError')

    throw new ApiError(message, axiosError.response?.data?.code)
  }
}
