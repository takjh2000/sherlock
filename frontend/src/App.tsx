import { useEffect, useState } from 'react'
import { authApi, type Me } from './api'
import GameDetailPage from './GameDetailPage'
import GamesPage from './GamesPage'
import LoginPage from './LoginPage'
import SetupPasswordPage from './SetupPasswordPage'
import './App.css'

type View = 'login' | 'setup'

function App() {
  // undefined: 세션 확인 중, null: 비로그인
  const [me, setMe] = useState<Me | null | undefined>(undefined)
  const [view, setView] = useState<View>('login')
  const [studentId, setStudentId] = useState('')
  // 선택한 게임 id. null이면 목록을 보여준다.
  const [gameId, setGameId] = useState<number | null>(null)

  // 새로고침해도 서버 세션(쿠키)으로 로그인 상태를 복원한다.
  useEffect(() => {
    authApi
      .me()
      .then(setMe)
      .catch(() => setMe(null))
  }, [])

  async function handleLogout() {
    await authApi.logout().catch(() => undefined)
    setMe(null)
    setGameId(null)
    setView('login')
  }

  if (me === undefined) return <p>불러오는 중...</p>

  if (me) {
    return (
      <section id="center">
        <h1>셜록 보드게임 동아리</h1>
        <p>
          {me.name}님 환영합니다{me.role === 'ADMIN' ? ' (관리자)' : ''}
        </p>
        <button type="button" onClick={handleLogout}>
          로그아웃
        </button>
        {gameId === null ? (
          <GamesPage onSelect={setGameId} />
        ) : (
          <GameDetailPage id={gameId} onBack={() => setGameId(null)} />
        )}
      </section>
    )
  }

  if (view === 'setup') {
    return (
      <SetupPasswordPage
        initialStudentId={studentId}
        onDone={(id) => {
          setStudentId(id)
          setView('login')
        }}
        onCancel={() => setView('login')}
      />
    )
  }

  return (
    <LoginPage
      initialStudentId={studentId}
      onLoggedIn={setMe}
      onNeedSetup={(id) => {
        setStudentId(id)
        setView('setup')
      }}
    />
  )
}

export default App
