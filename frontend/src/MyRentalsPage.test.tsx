import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MyRentalsPage from './MyRentalsPage'

const rentals = [
  {
    id: 1,
    gameId: 1,
    gameName: '카탄',
    rentedDate: '2026-10-01',
    dueDate: '2026-10-08',
    returnedDate: null,
    status: 'RENTED',
    daysLeft: 3,
    overdueDays: 0,
  },
  {
    id: 2,
    gameId: 2,
    gameName: '스플렌더',
    rentedDate: '2026-09-20',
    dueDate: '2026-09-27',
    returnedDate: null,
    status: 'RENTED',
    daysLeft: 0,
    overdueDays: 8,
  },
]

function mockFetch() {
  const fn = vi.fn(async (_path: string, _init?: RequestInit) => ({
    ok: true,
    status: 200,
    json: async () => rentals,
  }))
  vi.stubGlobal('fetch', fn)
  return fn
}

describe('MyRentalsPage', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('반납 예정일, D-n, 연체 일수를 보여준다', async () => {
    mockFetch()
    render(<MyRentalsPage />)
    expect(await screen.findByText(/D-3/)).toBeInTheDocument()
    expect(screen.getByText(/연체 8일/)).toBeInTheDocument()
    expect(screen.getByText(/반납 예정일 2026-10-08/)).toBeInTheDocument()
  })

  it('반납 시 신고 내용을 함께 전송한다', async () => {
    const fetchFn = mockFetch()
    render(<MyRentalsPage />)
    await userEvent.click((await screen.findAllByRole('button', { name: '반납' }))[0])
    await userEvent.type(screen.getByLabelText(/분실\/파손 신고/), '카드 분실')
    await userEvent.click(screen.getByRole('button', { name: '반납 확인' }))

    const call = fetchFn.mock.calls.find(([path]) => path === '/api/rentals/1/return')
    expect(call).toBeDefined()
    expect(JSON.parse(call![1]!.body as string)).toEqual({ report: '카드 분실' })
  })

  it('신고 입력창은 1000자까지만 입력할 수 있다', async () => {
    mockFetch()
    render(<MyRentalsPage />)
    await userEvent.click((await screen.findAllByRole('button', { name: '반납' }))[0])
    expect(screen.getByLabelText(/분실\/파손 신고/)).toHaveAttribute('maxlength', '1000')
  })
})
