import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

function mockFetch(handler: (path: string) => { status: number; body?: unknown }) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (path: string) => {
      const { status, body } = handler(path)
      return {
        ok: status >= 200 && status < 300,
        status,
        json: async () => body ?? {},
      }
    }),
  )
}

describe('App', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('세션이 없으면 로그인 페이지가 보인다', async () => {
    mockFetch(() => ({ status: 401, body: { code: 'UNAUTHENTICATED' } }))
    render(<App />)
    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument()
  })

  it('세션이 있으면 로그인 상태가 유지된다', async () => {
    mockFetch((path) =>
      path.startsWith('/api/games')
        ? { status: 200, body: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }
        : { status: 200, body: { studentId: '20240001', name: '홍길동', role: 'MEMBER' } },
    )
    render(<App />)
    expect(await screen.findByText(/홍길동님 환영합니다/)).toBeInTheDocument()
  })

  it('비밀번호 미설정 회원은 첫 비밀번호 설정 페이지로 이동한다', async () => {
    mockFetch((path) =>
      path === '/api/auth/login'
        ? { status: 401, body: { code: 'PASSWORD_NOT_SET', message: '설정 필요' } }
        : { status: 401 },
    )
    render(<App />)
    await userEvent.type(await screen.findByLabelText('학번'), '20240002')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'whatever123')
    await userEvent.click(screen.getByRole('button', { name: '로그인' }))
    expect(
      await screen.findByRole('heading', { name: '첫 비밀번호 설정' }),
    ).toBeInTheDocument()
  })

  it('로그인 실패 시 오류 메시지를 보여준다', async () => {
    mockFetch((path) =>
      path === '/api/auth/login'
        ? { status: 401, body: { code: 'BAD_CREDENTIALS', message: '학번 또는 비밀번호가 올바르지 않습니다.' } }
        : { status: 401 },
    )
    render(<App />)
    await userEvent.type(await screen.findByLabelText('학번'), '20240001')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: '로그인' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('올바르지 않습니다')
  })
})
