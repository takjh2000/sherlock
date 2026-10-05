import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import GameDetailPage from './GameDetailPage'
import GamesPage from './GamesPage'

const note = { content: '주사위 1개 분실', authorName: '홍길동', createdAt: '2026-10-01T10:00:00' }

const catan = {
  id: 1,
  name: '카탄',
  category: 'BOARD_GAME',
  owner: '김철수',
  totalQuantity: 2,
  availableQuantity: 1,
  latestNote: note,
}

const onePage = { content: [catan], page: 0, size: 20, totalElements: 1, totalPages: 1 }
const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }

function mockFetch(body: unknown) {
  const fn = vi.fn(async (_path: string) => ({ ok: true, status: 200, json: async () => body }))
  vi.stubGlobal('fetch', fn)
  return fn
}

describe('GamesPage', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('목록에 가능 수량과 최근 특이사항을 보여준다', async () => {
    mockFetch(onePage)
    render(<GamesPage onSelect={() => undefined} />)
    expect(await screen.findByText('카탄')).toBeInTheDocument()
    expect(screen.getByText('대여 가능 1/2')).toBeInTheDocument()
    expect(screen.getByText(/주사위 1개 분실/)).toBeInTheDocument()
  })

  it('검색어, 탭, 대여 가능 필터가 요청 파라미터에 반영된다', async () => {
    const fetchFn = mockFetch(emptyPage)
    render(<GamesPage onSelect={() => undefined} />)
    await screen.findByText('조건에 맞는 게임이 없습니다.')

    await userEvent.type(screen.getByLabelText('게임 이름 검색'), '카탄')
    await userEvent.click(screen.getByRole('button', { name: '검색' }))
    await userEvent.click(screen.getByRole('tab', { name: '크라임씬' }))
    await userEvent.click(screen.getByLabelText('대여 가능만 보기'))

    const last = fetchFn.mock.calls[fetchFn.mock.calls.length - 1][0]
    const params = new URLSearchParams(last.split('?')[1])
    expect(params.get('keyword')).toBe('카탄')
    expect(params.get('category')).toBe('CRIME_SCENE')
    expect(params.get('availableOnly')).toBe('true')
  })

  it('게임을 누르면 선택 콜백이 호출된다', async () => {
    mockFetch(onePage)
    const onSelect = vi.fn()
    render(<GamesPage onSelect={onSelect} />)
    await userEvent.click(await screen.findByRole('button', { name: '카탄' }))
    expect(onSelect).toHaveBeenCalledWith(1)
  })
})

describe('GameDetailPage', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('특이사항 이력 전체를 보여준다', async () => {
    mockFetch({
      id: 1,
      name: '카탄',
      category: 'BOARD_GAME',
      owner: '김철수',
      totalQuantity: 2,
      availableQuantity: 1,
      notes: [{ ...note, content: '주사위 교체함' }, note],
    })
    render(<GameDetailPage id={1} onBack={() => undefined} />)
    expect(await screen.findByRole('heading', { name: '카탄' })).toBeInTheDocument()
    expect(screen.getByText(/주사위 교체함/)).toBeInTheDocument()
    expect(screen.getByText(/주사위 1개 분실/)).toBeInTheDocument()
  })
})
