import { useCallback, useEffect, useState } from 'react'
import {
  adminApi,
  type AdminGame,
  type AdminGameDetail,
  type GameCategory,
  type GameImportResult,
  type GameInput,
  type Page,
} from './api'

const CATEGORY_LABEL: Record<GameCategory, string> = {
  BOARD_GAME: '보드게임',
  CRIME_SCENE: '크라임씬',
}

const EMPTY: GameInput = { name: '', category: 'BOARD_GAME', owner: '', totalQuantity: 1 }

function message(err: unknown, fallback: string) {
  return err instanceof Error ? err.message : fallback
}

export default function AdminGamesPanel() {
  const [keyword, setKeyword] = useState('')
  const [includeDeleted, setIncludeDeleted] = useState(false)
  const [page, setPage] = useState(0)
  const [games, setGames] = useState<Page<AdminGame> | null>(null)
  const [error, setError] = useState('')
  // null: 편집 없음, 'new': 새 게임, 객체: 기존 게임 편집
  const [editing, setEditing] = useState<AdminGameDetail | 'new' | null>(null)
  const [importResult, setImportResult] = useState<GameImportResult | null>(null)

  const load = useCallback(() => {
    return adminApi
      .games(keyword, includeDeleted, page)
      .then((data) => {
        setError('')
        setGames(data)
      })
      .catch((err) => setError(message(err, '불러오지 못했습니다.')))
  }, [keyword, includeDeleted, page])

  useEffect(() => {
    void load()
  }, [load])

  async function edit(id: number) {
    try {
      setEditing(await adminApi.game(id))
    } catch (err) {
      setError(message(err, '불러오지 못했습니다.'))
    }
  }

  async function handleImport(file: File | undefined) {
    if (!file) return
    setImportResult(null)
    try {
      setImportResult(await adminApi.importGames(file))
      setError('')
      await load()
    } catch (err) {
      setError(message(err, '엑셀 가져오기에 실패했습니다.'))
    }
  }

  async function remove(game: AdminGame) {
    if (!window.confirm(`'${game.name}'을(를) 삭제할까요?`)) return
    try {
      await adminApi.deleteGame(game.id)
      await load()
    } catch (err) {
      setError(message(err, '삭제에 실패했습니다.'))
    }
  }

  return (
    <div>
      <h3>게임 관리</h3>
      {error && <p role="alert">{error}</p>}
      <div>
        <label>
          게임 검색
          <input
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setPage(0)
            }}
          />
        </label>{' '}
        <label>
          <input
            type="checkbox"
            checked={includeDeleted}
            onChange={(e) => {
              setIncludeDeleted(e.target.checked)
              setPage(0)
            }}
          />
          삭제된 게임 포함
        </label>{' '}
        <button type="button" onClick={() => setEditing('new')}>
          게임 추가
        </button>
      </div>

      <div>
        <label>
          게임 목록 엑셀(xlsx) 가져오기
          <input
            type="file"
            accept=".xlsx"
            onChange={(e) => {
              void handleImport(e.target.files?.[0])
              e.target.value = ''
            }}
          />
        </label>
        <small>
          {' '}
          시트 &quot;보드게임_현황&quot;, &quot;크라임씬_현황&quot;만 읽습니다 (최대 5MB). 같은 분류·이름은 수량/소유자를
          갱신합니다.
        </small>
      </div>
      {importResult && (
        <div role="status">
          <p>
            신규 {importResult.created}건, 갱신 {importResult.updated}건, 실패 {importResult.failed.length}건
          </p>
          <ul aria-label="가져오기 실패 목록">
            {importResult.failed.map((f) => (
              <li key={`${f.sheet}-${f.row}`}>
                {f.sheet} {f.row}행 {f.name}: {f.reason}
              </li>
            ))}
          </ul>
        </div>
      )}

      {editing && (
        <GameEditor
          key={editing === 'new' ? 'new' : editing.id}
          game={editing === 'new' ? null : editing}
          onSaved={(saved) => {
            setEditing(saved)
            void load()
          }}
          onClose={() => setEditing(null)}
        />
      )}

      {games && games.content.length === 0 && <p>게임이 없습니다.</p>}
      <ul>
        {games?.content.map((game) => (
          <li key={game.id}>
            <strong>{game.name}</strong> · {CATEGORY_LABEL[game.category]} · {game.availableQuantity}/
            {game.totalQuantity}
            {game.deleted ? (
              <em> (삭제됨)</em>
            ) : (
              <>
                {' '}
                <button type="button" onClick={() => edit(game.id)}>
                  수정
                </button>{' '}
                <button type="button" onClick={() => remove(game)}>
                  삭제
                </button>
              </>
            )}
          </li>
        ))}
      </ul>
      {games && games.totalPages > 1 && (
        <div>
          <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>
            이전
          </button>{' '}
          {page + 1} / {games.totalPages}{' '}
          <button
            type="button"
            disabled={page + 1 >= games.totalPages}
            onClick={() => setPage(page + 1)}
          >
            다음
          </button>
        </div>
      )}
    </div>
  )
}

function GameEditor({
  game,
  onSaved,
  onClose,
}: {
  game: AdminGameDetail | null
  onSaved: (saved: AdminGameDetail) => void
  onClose: () => void
}) {
  const [form, setForm] = useState<GameInput>(
    game
      ? {
          name: game.name,
          category: game.category,
          owner: game.owner ?? '',
          totalQuantity: game.totalQuantity,
        }
      : EMPTY,
  )
  const [error, setError] = useState('')

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    try {
      const saved = game
        ? await adminApi.updateGame(game.id, form)
        : await adminApi.createGame(form)
      setError('')
      onSaved(saved)
    } catch (err) {
      setError(message(err, '저장에 실패했습니다.'))
    }
  }

  return (
    <>
    <form onSubmit={submit} aria-label={game ? '게임 수정' : '게임 추가'}>
      <h4>{game ? '게임 수정' : '게임 추가'}</h4>
      {error && <p role="alert">{error}</p>}
      <label>
        이름
        <input
          required
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
        />
      </label>{' '}
      <label>
        분류
        <select
          value={form.category}
          onChange={(e) => setForm({ ...form, category: e.target.value as GameCategory })}
        >
          <option value="BOARD_GAME">보드게임</option>
          <option value="CRIME_SCENE">크라임씬</option>
        </select>
      </label>{' '}
      <label>
        소유자
        <input value={form.owner} onChange={(e) => setForm({ ...form, owner: e.target.value })} />
      </label>{' '}
      <label>
        전체 수량
        <input
          type="number"
          min={1}
          required
          value={form.totalQuantity}
          onChange={(e) => setForm({ ...form, totalQuantity: Number(e.target.value) })}
        />
      </label>{' '}
      <button type="submit">저장</button>{' '}
      <button type="button" onClick={onClose}>
        닫기
      </button>
    </form>
    {/* 특이사항 입력에서 Enter를 눌러도 게임 저장 폼이 제출되지 않도록 폼 밖에 둔다. */}
    {game && <NotesEditor gameId={game.id} initial={game.notes} />}
    </>
  )
}

function NotesEditor({ gameId, initial }: { gameId: number; initial: AdminGameDetail['notes'] }) {
  const [notes, setNotes] = useState(initial)
  const [draft, setDraft] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editText, setEditText] = useState('')
  const [error, setError] = useState('')

  async function reload() {
    setNotes((await adminApi.game(gameId)).notes)
  }

  async function run(action: () => Promise<unknown>, fallback: string) {
    try {
      await action()
      await reload()
      setError('')
    } catch (err) {
      setError(message(err, fallback))
    }
  }

  return (
    <fieldset>
      <legend>특이사항</legend>
      {error && <p role="alert">{error}</p>}
      <ul>
        {notes.map((note) => (
          <li key={note.id}>
            {editingId === note.id ? (
              <>
                <input
                  aria-label="특이사항 수정"
                  value={editText}
                  maxLength={1000}
                  onChange={(e) => setEditText(e.target.value)}
                />{' '}
                <button
                  type="button"
                  onClick={() =>
                    run(async () => {
                      await adminApi.updateNote(note.id, editText)
                      setEditingId(null)
                    }, '수정에 실패했습니다.')
                  }
                >
                  저장
                </button>{' '}
                <button type="button" onClick={() => setEditingId(null)}>
                  취소
                </button>
              </>
            ) : (
              <>
                {note.content} <small>({note.authorName})</small>{' '}
                <button
                  type="button"
                  onClick={() => {
                    setEditingId(note.id)
                    setEditText(note.content)
                  }}
                >
                  특이사항 수정
                </button>{' '}
                <button
                  type="button"
                  onClick={() => {
                    if (window.confirm('특이사항을 삭제할까요?')) {
                      void run(() => adminApi.deleteNote(note.id), '삭제에 실패했습니다.')
                    }
                  }}
                >
                  특이사항 삭제
                </button>
              </>
            )}
          </li>
        ))}
      </ul>
      <input
        aria-label="새 특이사항"
        value={draft}
        maxLength={1000}
        onChange={(e) => setDraft(e.target.value)}
      />{' '}
      <button
        type="button"
        disabled={!draft.trim()}
        onClick={() =>
          run(async () => {
            await adminApi.addNote(gameId, draft)
            setDraft('')
          }, '작성에 실패했습니다.')
        }
      >
        특이사항 추가
      </button>
    </fieldset>
  )
}
