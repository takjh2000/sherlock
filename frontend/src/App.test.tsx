import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('앱이 렌더링되고 시작 제목이 보인다', () => {
    render(<App />)
    expect(
      screen.getByRole('heading', { name: 'Get started' }),
    ).toBeInTheDocument()
  })
})
