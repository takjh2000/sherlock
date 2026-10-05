import { useEffect, useState } from 'react'
import { MAX_REPORT_LENGTH, rentalApi, type Rental } from './api'

function dueLabel(rental: Rental) {
  if (rental.status === 'RETURNED') return `반납 완료 (${rental.returnedDate})`
  if (rental.overdueDays > 0) return `연체 ${rental.overdueDays}일`
  return rental.daysLeft === 0 ? 'D-Day' : `D-${rental.daysLeft}`
}

export default function MyRentalsPage() {
  const [rentals, setRentals] = useState<Rental[] | null>(null)
  const [error, setError] = useState('')
  // 신고 입력창을 연 대여 id와 입력 내용
  const [reportingId, setReportingId] = useState<number | null>(null)
  const [report, setReport] = useState('')

  function load() {
    return rentalApi
      .mine()
      .then((data) => {
        setError('')
        setRentals(data)
      })
      .catch((err) => setError(err instanceof Error ? err.message : '불러오지 못했습니다.'))
  }

  useEffect(() => {
    void load()
  }, [])

  async function handleReturn(id: number) {
    try {
      await rentalApi.returnRental(id, report)
      setReportingId(null)
      setReport('')
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : '반납에 실패했습니다.')
    }
  }

  return (
    <section>
      <h2>내 대여</h2>
      {error && <p role="alert">{error}</p>}
      {rentals && rentals.length === 0 && <p>대여 내역이 없습니다.</p>}
      <ul>
        {rentals?.map((rental) => (
          <li key={rental.id}>
            <strong>{rental.gameName}</strong> · 반납 예정일 {rental.dueDate} · {dueLabel(rental)}
            {rental.status === 'RENTED' &&
              (reportingId === rental.id ? (
                <div>
                  <label>
                    분실/파손 신고 (선택)
                    <textarea
                      value={report}
                      maxLength={MAX_REPORT_LENGTH}
                      onChange={(e) => setReport(e.target.value)}
                    />
                  </label>
                  <small>
                    {report.length}/{MAX_REPORT_LENGTH}
                  </small>{' '}
                  <button type="button" onClick={() => handleReturn(rental.id)}>
                    반납 확인
                  </button>{' '}
                  <button
                    type="button"
                    onClick={() => {
                      setReportingId(null)
                      setReport('')
                    }}
                  >
                    취소
                  </button>
                </div>
              ) : (
                <>
                  {' '}
                  <button type="button" onClick={() => setReportingId(rental.id)}>
                    반납
                  </button>
                </>
              ))}
          </li>
        ))}
      </ul>
    </section>
  )
}
