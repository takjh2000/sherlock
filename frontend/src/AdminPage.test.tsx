import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import AdminPage from './AdminPage'

type Call = { path: string; init?: RequestInit }

// 가짜 데이터만 사용한다.
const page = <T,>(content: T[]) => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
})

const members = [
  { id: 1, studentId: '20250001', name: '가짜일', role: 'MEMBER', active: true, passwordSet: true },
]

const rental = {
  id: 7,
  gameId: 1,
  gameName: '카탄',
  studentId: '20250001',
  memberName: '가짜일',
  rentedDate: '2026-09-01',
  dueDate: '2026-09-08',
  returnedDate: null,
  status: 'RENTED',
  overdueDays: 10,
}

function mockFetch(handler: (path: string, init?: RequestInit) => unknown = () => undefined) {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (path: string, init?: RequestInit) => {
      calls.push({ path, init })
      const custom = handler(path, init)
      let body: unknown = custom
      if (body === undefined) {
        if (path.startsWith('/api/admin/members')) body = page(members)
        else if (path === '/api/admin/rentals/overdue') body = [rental]
        else if (path === '/api/admin/rentals/active') body = [rental]
        else if (path === '/api/admin/rentals/overdue-stats')
          body = [{ studentId: '20250001', name: '가짜일', overdueCount: 2, overdueDays: 15, currentlyOverdue: 1 }]
        else if (path.startsWith('/api/admin/games'))
          body = page([
            { id: 1, name: '카탄', category: 'BOARD_GAME', owner: null, totalQuantity: 2, availableQuantity: 2, latestNote: null, deleted: false },
            { id: 2, name: '옛날게임', category: 'BOARD_GAME', owner: null, totalQuantity: 1, availableQuantity: 1, latestNote: null, deleted: true },
          ])
        else body = {}
      }
      return { ok: true, status: 200, json: async () => body }
    }),
  )
  return calls
}

describe('AdminPage', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('삭제된 게임은 삭제됨으로 표시되고 수정/삭제 버튼이 없다', async () => {
    mockFetch()
    render(<AdminPage />)
    const deleted = (await screen.findByText('옛날게임')).closest('li')!
    expect(within(deleted).getByText(/삭제됨/)).toBeInTheDocument()
    expect(within(deleted).queryByRole('button')).toBeNull()
  })

  it('삭제된 게임 포함 체크박스를 켜면 includeDeleted=true로 조회한다', async () => {
    const calls = mockFetch()
    render(<AdminPage />)
    await screen.findByText('카탄')
    await userEvent.click(screen.getByLabelText('삭제된 게임 포함'))
    await vi.waitFor(() =>
      expect(calls.some((c) => c.path.includes('includeDeleted=true'))).toBe(true),
    )
  })

  it('게임 삭제가 거절되면 서버 메시지를 보여준다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_path: string, init?: RequestInit) => {
        if (init?.method === 'DELETE') {
          return {
            ok: false,
            status: 409,
            json: async () => ({ code: 'GAME_RENTED', message: '대여 중인 게임은 삭제할 수 없습니다.' }),
          }
        }
        return {
          ok: true,
          status: 200,
          json: async () =>
            page([{ id: 1, name: '카탄', category: 'BOARD_GAME', owner: null, totalQuantity: 2, availableQuantity: 1, latestNote: null, deleted: false }]),
        }
      }),
    )
    render(<AdminPage />)
    await userEvent.click(await screen.findByRole('button', { name: '삭제' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('대여 중인 게임은 삭제할 수 없습니다.')
  })

  it('회원 비밀번호 초기화를 확인 후 요청한다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const calls = mockFetch()
    render(<AdminPage />)
    await userEvent.click(screen.getByRole('button', { name: '회원 관리' }))
    await userEvent.click(await screen.findByRole('button', { name: '비밀번호 초기화' }))
    await vi.waitFor(() =>
      expect(
        calls.some((c) => c.path === '/api/admin/members/1/reset-password' && c.init?.method === 'POST'),
      ).toBe(true),
    )
  })

  it('엑셀 업로드는 multipart(FormData)로 보내고 건너뛴 행을 보여준다', async () => {
    const calls = mockFetch((path) =>
      path === '/api/admin/members/import'
        ? { created: 1, skipped: [{ row: 3, studentId: '20250001', reason: '이미 등록된 학번입니다.' }] }
        : undefined,
    )
    render(<AdminPage />)
    await userEvent.click(screen.getByRole('button', { name: '회원 관리' }))
    const file = new File(['fake'], 'members.xlsx')
    await userEvent.upload(await screen.findByLabelText(/명단 엑셀/), file)

    expect(await screen.findByText(/3행 20250001: 이미 등록된 학번입니다./)).toBeInTheDocument()
    const upload = calls.find((c) => c.path === '/api/admin/members/import')!
    expect(upload.init?.body).toBeInstanceOf(FormData)
    // Content-Type을 직접 지정하면 multipart 경계가 빠지므로 지정하지 않아야 한다.
    expect(upload.init?.headers).toEqual({})
  })

  it('게임 엑셀 가져오기는 multipart로 보내고 신규/갱신/실패 요약과 실패 사유를 보여준다', async () => {
    const calls = mockFetch((path) =>
      path === '/api/admin/games/import'
        ? {
            created: 3,
            updated: 2,
            failed: [{ sheet: '보드게임_현황', row: 9, name: '가짜게임', reason: '전체 수량은 1 이상의 정수여야 합니다.' }],
          }
        : undefined,
    )
    render(<AdminPage />)
    const file = new File(['fake'], 'games.xlsx')
    await userEvent.upload(await screen.findByLabelText(/게임 목록 엑셀/), file)

    expect(await screen.findByText('신규 3건, 갱신 2건, 실패 1건')).toBeInTheDocument()
    expect(
      screen.getByText(/보드게임_현황 9행 가짜게임: 전체 수량은 1 이상의 정수여야 합니다./),
    ).toBeInTheDocument()
    const upload = calls.find((c) => c.path === '/api/admin/games/import')!
    expect(upload.init?.body).toBeInstanceOf(FormData)
  })

  it('대여 현황에서 연체 목록, 회원별 누적을 보여주고 강제 반납한다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const calls = mockFetch()
    render(<AdminPage />)
    await userEvent.click(screen.getByRole('button', { name: '대여 현황' }))

    const overdue = await screen.findByRole('list', { name: '연체 목록' })
    expect(within(overdue).getByText(/연체 10일/)).toBeInTheDocument()
    expect(screen.getByRole('list', { name: '회원별 연체 누적' })).toHaveTextContent('누적 2회 / 15일')

    await userEvent.click(within(overdue).getByRole('button', { name: '강제 반납' }))
    await vi.waitFor(() =>
      expect(calls.some((c) => c.path === '/api/admin/rentals/7/force-return')).toBe(true),
    )
  })
})

describe('App 관리자 메뉴', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  function mockMe(role: 'ADMIN' | 'MEMBER') {
    mockFetch((path) => {
      if (path === '/api/auth/me') return { studentId: '20200001', name: '가짜임원', role }
      if (path.startsWith('/api/games')) return page([])
      return undefined
    })
  }

  it('ADMIN에게만 관리자 메뉴가 보인다', async () => {
    mockMe('ADMIN')
    render(<App />)
    expect(await screen.findByRole('button', { name: '관리자' })).toBeInTheDocument()
  })

  it('MEMBER에게는 관리자 메뉴가 보이지 않는다', async () => {
    mockMe('MEMBER')
    render(<App />)
    await screen.findByText(/환영합니다/)
    expect(screen.queryByRole('button', { name: '관리자' })).toBeNull()
  })
})
