import { useState, type FormEvent } from 'react'
import { authApi } from './api'

type Props = {
  initialStudentId?: string
  onDone: (studentId: string) => void
  onCancel: () => void
}

export default function SetupPasswordPage({
  initialStudentId = '',
  onDone,
  onCancel,
}: Props) {
  const [studentId, setStudentId] = useState(initialStudentId)
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    if (password !== confirm) {
      setError('비밀번호가 서로 일치하지 않습니다.')
      return
    }
    try {
      await authApi.setupPassword(studentId, name, password)
      onDone(studentId)
    } catch (err) {
      setError(err instanceof Error ? err.message : '설정에 실패했습니다.')
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <h1>첫 비밀번호 설정</h1>
      <label>
        학번
        <input value={studentId} onChange={(e) => setStudentId(e.target.value)} />
      </label>
      <label>
        이름
        <input value={name} onChange={(e) => setName(e.target.value)} />
      </label>
      <label>
        새 비밀번호 (8자 이상)
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="new-password"
        />
      </label>
      <label>
        비밀번호 확인
        <input
          type="password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          autoComplete="new-password"
        />
      </label>
      {error && <p role="alert">{error}</p>}
      <button type="submit">비밀번호 설정</button>
      <button type="button" onClick={onCancel}>
        로그인으로 돌아가기
      </button>
    </form>
  )
}
