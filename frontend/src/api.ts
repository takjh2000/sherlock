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

export type GameCategory = 'BOARD_GAME' | 'CRIME_SCENE'

export type GameNote = {
  content: string
  authorName: string
  createdAt: string
}

export type GameSummary = {
  id: number
  name: string
  category: GameCategory
  owner: string | null
  totalQuantity: number
  availableQuantity: number
  latestNote: GameNote | null
}

export type GameDetail = Omit<GameSummary, 'latestNote'> & {
  notes: GameNote[]
}

export type Page<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type GameSearch = {
  keyword: string
  category: GameCategory
  availableOnly: boolean
  page: number
}

export const gameApi = {
  list: ({ keyword, category, availableOnly, page }: GameSearch) => {
    const params = new URLSearchParams({
      category,
      availableOnly: String(availableOnly),
      page: String(page),
      size: '20',
    })
    if (keyword.trim()) params.set('keyword', keyword.trim())
    return request<Page<GameSummary>>(`/api/games?${params}`)
  },
  detail: (id: number) => request<GameDetail>(`/api/games/${id}`),
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
