import { useEffect, useState } from 'react'
import { gameApi, rentalApi, type GameCategory, type GameSummary, type Page } from './api'

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
  const [message, setMessage] = useState('')
  // 대여 후 목록(가능 수량)을 다시 불러오기 위한 값
  const [reloadKey, setReloadKey] = useState(0)

  async function handleRent(game: GameSummary) {
    setError('')
    setMessage('')
    try {
      const rental = await rentalApi.rent(game.id)
      setMessage(`${game.name}을(를) 대여했습니다. 반납 예정일은 ${rental.dueDate}입니다.`)
    } catch (err) {
      setError(err instanceof Error ? err.message : '대여에 실패했습니다.')
    }
    setReloadKey((k) => k + 1)
  }

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
  }, [keyword, category, availableOnly, page, reloadKey])

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
      {message && <p role="status">{message}</p>}
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
            <button
              type="button"
              disabled={game.availableQuantity === 0}
              onClick={() => handleRent(game)}
            >
              대여
            </button>
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
