import { useCallback, useEffect, useState } from 'react'
import { adminApi, type AdminRental, type MemberOverdueStat } from './api'

export default function AdminRentalsPanel() {
  const [active, setActive] = useState<AdminRental[]>([])
  const [overdue, setOverdue] = useState<AdminRental[]>([])
  const [stats, setStats] = useState<MemberOverdueStat[]>([])
  const [error, setError] = useState('')

  const load = useCallback(() => {
    return Promise.all([
      adminApi.activeRentals(),
      adminApi.overdueRentals(),
      adminApi.overdueStats(),
    ])
      .then(([a, o, s]) => {
        setError('')
        setActive(a)
        setOverdue(o)
        setStats(s)
      })
      .catch((err) => setError(err instanceof Error ? err.message : '불러오지 못했습니다.'))
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function forceReturn(rental: AdminRental) {
    if (!window.confirm(`${rental.memberName}님의 '${rental.gameName}'을(를) 반납 처리할까요?`)) return
    try {
      await adminApi.forceReturn(rental.id)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : '반납 처리에 실패했습니다.')
    }
  }

  function rentalItem(rental: AdminRental) {
    return (
      <li key={rental.id}>
        <strong>{rental.gameName}</strong> · {rental.memberName}({rental.studentId}) · 대여{' '}
        {rental.rentedDate} · 반납 예정일 {rental.dueDate}
        {rental.overdueDays > 0 && ` · 연체 ${rental.overdueDays}일`}{' '}
        <button type="button" onClick={() => forceReturn(rental)}>
          강제 반납
        </button>
      </li>
    )
  }

  return (
    <div>
      <h3>대여 현황</h3>
      {error && <p role="alert">{error}</p>}

      <h4>연체 목록</h4>
      {overdue.length === 0 && <p>연체 중인 대여가 없습니다.</p>}
      <ul aria-label="연체 목록">{overdue.map(rentalItem)}</ul>

      <h4>회원별 연체 누적</h4>
      {stats.length === 0 && <p>연체 이력이 없습니다.</p>}
      <ul aria-label="회원별 연체 누적">
        {stats.map((s) => (
          <li key={s.studentId}>
            <strong>{s.name}</strong>({s.studentId}) · 누적 {s.overdueCount}회 / {s.overdueDays}일
            {s.currentlyOverdue > 0 && ` · 현재 연체 ${s.currentlyOverdue}건`}
          </li>
        ))}
      </ul>

      <h4>전체 대여 중</h4>
      {active.length === 0 && <p>대여 중인 게임이 없습니다.</p>}
      <ul aria-label="전체 대여 중">{active.map(rentalItem)}</ul>
    </div>
  )
}
