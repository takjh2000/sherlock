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
  // FormData(파일 업로드)는 브라우저가 multipart 경계를 포함한 Content-Type을 직접 지정한다.
  const isForm = init?.body instanceof FormData
  const res = await fetch(path, {
    credentials: 'same-origin',
    headers: isForm ? {} : { 'Content-Type': 'application/json' },
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
  id: number
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

export type Rental = {
  id: number
  gameId: number
  gameName: string
  rentedDate: string
  dueDate: string
  returnedDate: string | null
  status: 'RENTED' | 'RETURNED'
  // 반납 예정일까지 D-n (반납 완료면 null)
  daysLeft: number | null
  overdueDays: number
}

// 서버(ReturnRequest)의 분실/파손 신고 최대 길이
export const MAX_REPORT_LENGTH = 1000

export const rentalApi = {
  rent: (gameId: number) =>
    request<Rental>('/api/rentals', {
      method: 'POST',
      body: JSON.stringify({ gameId }),
    }),
  mine: () => request<Rental[]>('/api/rentals/me'),
  returnRental: (id: number, report: string) =>
    request<Rental>(`/api/rentals/${id}/return`, {
      method: 'POST',
      body: JSON.stringify({ report: report.trim() || null }),
    }),
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

// ---- 관리자 API (/api/admin/**, ADMIN 전용) ----

export type AdminGame = GameSummary & { deleted: boolean }

export type AdminGameDetail = GameDetail & { deleted: boolean }

export type GameInput = {
  name: string
  category: GameCategory
  owner: string
  totalQuantity: number
}

export type AdminMember = {
  id: number
  studentId: string
  name: string
  role: 'MEMBER' | 'ADMIN'
  active: boolean
  // false면 아직 첫 로그인(비밀번호 설정) 전
  passwordSet: boolean
}

export type MemberImportResult = {
  created: number
  skipped: { row: number; studentId: string; reason: string }[]
}

export type AdminRental = {
  id: number
  gameId: number
  gameName: string
  studentId: string
  memberName: string
  rentedDate: string
  dueDate: string
  returnedDate: string | null
  status: 'RENTED' | 'RETURNED'
  overdueDays: number
}

export type MemberOverdueStat = {
  studentId: string
  name: string
  overdueCount: number
  overdueDays: number
  currentlyOverdue: number
}

const json = (method: string, body?: unknown): RequestInit => ({
  method,
  body: body === undefined ? undefined : JSON.stringify(body),
})

export const adminApi = {
  games: (keyword: string, includeDeleted: boolean, page: number) => {
    const params = new URLSearchParams({
      includeDeleted: String(includeDeleted),
      page: String(page),
      size: '20',
    })
    if (keyword.trim()) params.set('keyword', keyword.trim())
    return request<Page<AdminGame>>(`/api/admin/games?${params}`)
  },
  game: (id: number) => request<AdminGameDetail>(`/api/admin/games/${id}`),
  createGame: (input: GameInput) =>
    request<AdminGameDetail>('/api/admin/games', json('POST', input)),
  updateGame: (id: number, input: GameInput) =>
    request<AdminGameDetail>(`/api/admin/games/${id}`, json('PUT', input)),
  deleteGame: (id: number) => request<void>(`/api/admin/games/${id}`, json('DELETE')),
  addNote: (gameId: number, content: string) =>
    request<GameNote>(`/api/admin/games/${gameId}/notes`, json('POST', { content })),
  updateNote: (noteId: number, content: string) =>
    request<GameNote>(`/api/admin/notes/${noteId}`, json('PUT', { content })),
  deleteNote: (noteId: number) => request<void>(`/api/admin/notes/${noteId}`, json('DELETE')),

  members: (keyword: string, page: number) => {
    const params = new URLSearchParams({ page: String(page), size: '20' })
    if (keyword.trim()) params.set('keyword', keyword.trim())
    return request<Page<AdminMember>>(`/api/admin/members?${params}`)
  },
  createMember: (studentId: string, name: string) =>
    request<AdminMember>('/api/admin/members', json('POST', { studentId, name })),
  importMembers: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<MemberImportResult>('/api/admin/members/import', {
      method: 'POST',
      body: form,
    })
  },
  deactivateMember: (id: number) =>
    request<AdminMember>(`/api/admin/members/${id}/deactivate`, json('POST')),
  resetPassword: (id: number) =>
    request<AdminMember>(`/api/admin/members/${id}/reset-password`, json('POST')),

  activeRentals: () => request<AdminRental[]>('/api/admin/rentals/active'),
  overdueRentals: () => request<AdminRental[]>('/api/admin/rentals/overdue'),
  overdueStats: () => request<MemberOverdueStat[]>('/api/admin/rentals/overdue-stats'),
  forceReturn: (id: number) =>
    request<AdminRental>(`/api/admin/rentals/${id}/force-return`, json('POST')),
}
