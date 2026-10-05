import { useEffect, useState } from 'react'
import { gameApi, type GameDetail } from './api'

type Props = {
  id: number
  onBack: () => void
}

export default function GameDetailPage({ id, onBack }: Props) {
  const [game, setGame] = useState<GameDetail | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    gameApi
      .detail(id)
      .then(setGame)
      .catch((err) => setError(err instanceof Error ? err.message : '불러오지 못했습니다.'))
  }, [id])

  return (
    <section>
      <button type="button" onClick={onBack}>
        목록으로
      </button>
      {error && <p role="alert">{error}</p>}
      {game && (
        <>
          <h2>{game.name}</h2>
          <p>분류: {game.category === 'BOARD_GAME' ? '보드게임' : '크라임씬'}</p>
          <p>소유자: {game.owner ?? '-'}</p>
          <p>
            대여 가능 {game.availableQuantity}/{game.totalQuantity}
          </p>
          <h3>특이사항</h3>
          {game.notes.length === 0 && <p>기록된 특이사항이 없습니다.</p>}
          <ul>
            {game.notes.map((note, i) => (
              <li key={i}>
                {note.content}{' '}
                <small>
                  {note.authorName} · {note.createdAt.slice(0, 10)}
                </small>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}
