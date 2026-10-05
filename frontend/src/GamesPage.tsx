import { useEffect, useState } from 'react'
import { gameApi, type GameCategory, type GameSummary, type Page } from './api'

type Props = {
  onSelect: (id: number) => void
}

const TABS: { category: GameCategory; label: string }[] = [
  { category: 'BOARD_GAME', label: '보드게임' },
  { category: 'CRIME_SCENE', label: '크라임씬' },
]

export default function GamesPage({ onSelect }: Props) {
  const [input, setInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState<GameCategory>('BOARD_GAME')
  const [availableOnly, setAvailableOnly] = useState(false)
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<Page<GameSummary> | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    gameApi
      .list({ keyword, category, availableOnly, page })
      .then((data) => {
        if (cancelled) return
        setError('')
        setResult(data)
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : '목록을 불러오지 못했습니다.')
      })
    return () => {
      cancelled = true
    }
  }, [keyword, category, availableOnly, page])

  return (
    <section>
      <h2>게임 목록</h2>
      <form
        role="search"
        onSubmit={(e) => {
          e.preventDefault()
          setKeyword(input)
          setPage(0)
        }}
      >
        <input
          type="search"
          aria-label="게임 이름 검색"
          placeholder="게임 이름"
          value={input}
          onChange={(e) => setInput(e.target.value)}
        />
        <button type="submit">검색</button>
      </form>
      <div role="tablist">
        {TABS.map((tab) => (
          <button
            key={tab.category}
            type="button"
            role="tab"
            aria-selected={category === tab.category}
            onClick={() => {
              setCategory(tab.category)
              setPage(0)
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <label>
        <input
          type="checkbox"
          checked={availableOnly}
          onChange={(e) => {
            setAvailableOnly(e.target.checked)
            setPage(0)
          }}
        />
        대여 가능만 보기
      </label>

      {error && <p role="alert">{error}</p>}
      {result && result.content.length === 0 && <p>조건에 맞는 게임이 없습니다.</p>}
      <ul>
        {result?.content.map((game) => (
          <li key={game.id}>
            <button type="button" onClick={() => onSelect(game.id)}>
              {game.name}
            </button>{' '}
            <span>
              대여 가능 {game.availableQuantity}/{game.totalQuantity}
            </span>
            {game.owner && <span> · 소유자 {game.owner}</span>}
            {game.latestNote && <p>특이사항: {game.latestNote.content}</p>}
          </li>
        ))}
      </ul>
      {result && result.totalPages > 1 && (
        <nav aria-label="페이지">
          <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>
            이전
          </button>{' '}
          <span>
            {result.page + 1} / {result.totalPages}
          </span>{' '}
          <button
            type="button"
            disabled={page + 1 >= result.totalPages}
            onClick={() => setPage(page + 1)}
          >
            다음
          </button>
        </nav>
      )}
    </section>
  )
}
