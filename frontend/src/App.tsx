import { useEffect, useState } from 'react'
import { authApi, type Me } from './api'
import AdminPage from './AdminPage'
import GameDetailPage from './GameDetailPage'
import GamesPage from './GamesPage'
import MyRentalsPage from './MyRentalsPage'
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
  // 내 대여 페이지 표시 여부
  const [showRentals, setShowRentals] = useState(false)
  // 관리자 페이지 표시 여부 (ADMIN만)
  const [showAdmin, setShowAdmin] = useState(false)

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
    setShowRentals(false)
    setShowAdmin(false)
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
        <nav>
          <button
            type="button"
            onClick={() => {
              setShowRentals(false)
              setShowAdmin(false)
              setGameId(null)
            }}
          >
            게임 목록
          </button>{' '}
          <button
            type="button"
            onClick={() => {
              setShowAdmin(false)
              setShowRentals(true)
            }}
          >
            내 대여
          </button>
          {/* 관리자 메뉴는 ADMIN에게만 보인다(서버도 /api/admin/**을 ADMIN만 허용). */}
          {me.role === 'ADMIN' && (
            <>
              {' '}
              <button
                type="button"
                onClick={() => {
                  setShowRentals(false)
                  setShowAdmin(true)
                }}
              >
                관리자
              </button>
            </>
          )}
        </nav>
        {showAdmin && me.role === 'ADMIN' ? (
          <AdminPage />
        ) : showRentals ? (
          <MyRentalsPage />
        ) : gameId === null ? (
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
