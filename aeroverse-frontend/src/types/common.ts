export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

export interface PageResponse<T> {
  records: T[]
  total: number
  page: number
  size: number
}
