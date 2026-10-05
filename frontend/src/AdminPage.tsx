import { useState } from 'react'
import AdminGamesPanel from './AdminGamesPanel'
import AdminMembersPanel from './AdminMembersPanel'
import AdminRentalsPanel from './AdminRentalsPanel'

type Tab = 'games' | 'members' | 'rentals'

const TABS: { key: Tab; label: string }[] = [
  { key: 'games', label: '게임 관리' },
  { key: 'members', label: '회원 관리' },
  { key: 'rentals', label: '대여 현황' },
]

/** 관리자 전용 페이지. 메뉴는 ADMIN에게만 보이고, API도 서버에서 ADMIN만 허용한다. */
export default function AdminPage() {
  const [tab, setTab] = useState<Tab>('games')

  return (
    <section>
      <h2>관리자</h2>
      <nav aria-label="관리자 메뉴">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            aria-pressed={tab === t.key}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </nav>
      {tab === 'games' && <AdminGamesPanel />}
      {tab === 'members' && <AdminMembersPanel />}
      {tab === 'rentals' && <AdminRentalsPanel />}
    </section>
  )
}
