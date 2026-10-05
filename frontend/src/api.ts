export type Me = {
  studentId: string
  name: string
  role: 'MEMBER' | 'ADMIN'
}

export class ApiError extends Error {
  status: number
  code?: string

  constructor(status: number, code: string | undefined, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new ApiError(
      res.status,
      body.code,
      body.message ?? '요청에 실패했습니다.',
    )
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

export const authApi = {
  me: () => request<Me>('/api/auth/me'),
  login: (studentId: string, password: string) =>
    request<Me>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ studentId, password }),
    }),
  logout: () => request<void>('/api/auth/logout', { method: 'POST' }),
  setupPassword: (studentId: string, name: string, password: string) =>
    request<void>('/api/auth/setup-password', {
      method: 'POST',
      body: JSON.stringify({ studentId, name, password }),
    }),
}
