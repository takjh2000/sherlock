import { useCallback, useEffect, useState } from 'react'
import { adminApi, type AdminMember, type MemberImportResult, type Page } from './api'

function message(err: unknown, fallback: string) {
  return err instanceof Error ? err.message : fallback
}

function statusLabel(member: AdminMember) {
  if (!member.active) return '비활성'
  return member.passwordSet ? '활성' : '첫 로그인 전'
}

export default function AdminMembersPanel() {
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)
  const [members, setMembers] = useState<Page<AdminMember> | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [studentId, setStudentId] = useState('')
  const [name, setName] = useState('')
  const [importResult, setImportResult] = useState<MemberImportResult | null>(null)

  const load = useCallback(() => {
    return adminApi
      .members(keyword, page)
      .then((data) => {
        setError('')
        setMembers(data)
      })
      .catch((err) => setError(message(err, '불러오지 못했습니다.')))
  }, [keyword, page])

  useEffect(() => {
    void load()
  }, [load])

  async function run(action: () => Promise<unknown>, done: string, fallback: string) {
    try {
      await action()
      setError('')
      setNotice(done)
      await load()
    } catch (err) {
      setNotice('')
      setError(message(err, fallback))
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    await run(
      async () => {
        await adminApi.createMember(studentId.trim(), name.trim())
        setStudentId('')
        setName('')
      },
      '회원을 등록했습니다.',
      '등록에 실패했습니다.',
    )
  }

  async function handleImport(file: File | undefined) {
    if (!file) return
    setImportResult(null)
    await run(
      async () => setImportResult(await adminApi.importMembers(file)),
      '엑셀 등록을 마쳤습니다.',
      '엑셀 등록에 실패했습니다.',
    )
  }

  return (
    <div>
      <h3>회원 관리</h3>
      {error && <p role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}

      <form onSubmit={handleCreate} aria-label="회원 단건 등록">
        <label>
          학번
          <input required maxLength={20} value={studentId} onChange={(e) => setStudentId(e.target.value)} />
        </label>{' '}
        <label>
          이름
          <input required maxLength={50} value={name} onChange={(e) => setName(e.target.value)} />
        </label>{' '}
        <button type="submit">회원 등록</button>
      </form>

      <div>
        <label>
          명단 엑셀(xlsx) 업로드
          <input
            type="file"
            accept=".xlsx"
            onChange={(e) => {
              void handleImport(e.target.files?.[0])
              e.target.value = ''
            }}
          />
        </label>
        <small> A열: 학번, B열: 이름 (첫 행이 &quot;학번&quot; 머리글이면 건너뜁니다)</small>
      </div>
      {importResult && (
        <div>
          <p>
            {importResult.created}명을 등록했고 {importResult.skipped.length}행을 건너뛰었습니다.
          </p>
          <ul>
            {importResult.skipped.map((s) => (
              <li key={s.row}>
                {s.row}행 {s.studentId}: {s.reason}
              </li>
            ))}
          </ul>
        </div>
      )}

      <label>
        회원 검색
        <input
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value)
            setPage(0)
          }}
        />
      </label>
      {members && members.content.length === 0 && <p>회원이 없습니다.</p>}
      <ul>
        {members?.content.map((m) => (
          <li key={m.id}>
            <strong>{m.name}</strong> ({m.studentId}) · {statusLabel(m)}
            {m.role === 'ADMIN' ? ' · 관리자' : ''}
            {m.role === 'MEMBER' && (
              <>
                {m.active && (
                  <>
                    {' '}
                    <button
                      type="button"
                      onClick={() => {
                        if (window.confirm(`${m.name}님을 비활성화할까요?`)) {
                          void run(
                            () => adminApi.deactivateMember(m.id),
                            '비활성화했습니다.',
                            '비활성화에 실패했습니다.',
                          )
                        }
                      }}
                    >
                      비활성화
                    </button>
                  </>
                )}{' '}
                <button
                  type="button"
                  onClick={() => {
                    if (
                      window.confirm(
                        `${m.name}님의 비밀번호를 초기화할까요? 로그인 중인 세션도 끊기고 다시 첫 로그인을 해야 합니다.`,
                      )
                    ) {
                      void run(
                        () => adminApi.resetPassword(m.id),
                        '비밀번호를 초기화했습니다.',
                        '초기화에 실패했습니다.',
                      )
                    }
                  }}
                >
                  비밀번호 초기화
                </button>
              </>
            )}
          </li>
        ))}
      </ul>
      {members && members.totalPages > 1 && (
        <div>
          <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>
            이전
          </button>{' '}
          {page + 1} / {members.totalPages}{' '}
          <button
            type="button"
            disabled={page + 1 >= members.totalPages}
            onClick={() => setPage(page + 1)}
          >
            다음
          </button>
        </div>
      )}
    </div>
  )
}
