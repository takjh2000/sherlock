import { useState, type FormEvent } from 'react'
import { ApiError, authApi, type Me } from './api'

type Props = {
  initialStudentId?: string
  onLoggedIn: (me: Me) => void
  onNeedSetup: (studentId: string) => void
}

export default function LoginPage({
  initialStudentId = '',
  onLoggedIn,
  onNeedSetup,
}: Props) {
  const [studentId, setStudentId] = useState(initialStudentId)
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    try {
      onLoggedIn(await authApi.login(studentId, password))
    } catch (err) {
      if (err instanceof ApiError && err.code === 'PASSWORD_NOT_SET') {
        onNeedSetup(studentId)
      } else {
        setError(err instanceof Error ? err.message : '로그인에 실패했습니다.')
      }
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <h1>로그인</h1>
      <label>
        학번
        <input
          value={studentId}
          onChange={(e) => setStudentId(e.target.value)}
          autoComplete="username"
        />
      </label>
      <label>
        비밀번호
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />
      </label>
      {error && <p role="alert">{error}</p>}
      <button type="submit">로그인</button>
      <button type="button" onClick={() => onNeedSetup(studentId)}>
        처음이신가요? 비밀번호 설정
      </button>
    </form>
  )
}
