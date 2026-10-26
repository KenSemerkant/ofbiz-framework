import { useState } from 'react';
import { api } from '../api';

type Page<T> = { items: T[]; totalElements: number; page: number; totalPages: number };
type Party = { partyId: string; kind: string; displayName: string };
type PartyDetail = { partyId: string; kind: string; displayName: string; roles: string[]; contactMechs: unknown };

// P9-F4: role filter is optional (blank = all parties) and a row click opens
// the party detail (roles + contact mechs) — the monolith PartyMgr slice.
export default function Parties() {
  const [role, setRole] = useState('CUSTOMER');
  const [rows, setRows] = useState<Party[]>([]);
  const [msg, setMsg] = useState('');
  const [count, setCount] = useState('');
  const [detail, setDetail] = useState<PartyDetail | null>(null);

  const search = async () => {
    try {
      const q = new URLSearchParams({ page: '0', size: '20' });
      if (role.trim()) q.set('role', role.trim());
      const page = await api<Page<Party>>(`/api/v1/parties?${q}`);
      setRows(page.items);
      setCount(`${page.totalElements} parties (page ${page.page + 1}/${page.totalPages})`);
      setMsg('');
      setDetail(null);
    } catch (e) {
      setMsg((e as Error).message);
      setCount('');
    }
  };

  const open = async (partyId: string) => {
    try {
      setDetail(await api<PartyDetail>(`/api/v1/parties/${encodeURIComponent(partyId)}`));
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  return (
    <section aria-labelledby="parties-h">
      <h2 id="parties-h">Parties</h2>
      <p className="err" data-testid="parties-msg">{msg}</p>
      <form onSubmit={e => { e.preventDefault(); void search(); }}>
        <label>Role <input value={role} onChange={e => setRole(e.target.value)} placeholder="blank = all" /></label>
        <button>Search</button>
      </form>
      <table>
        <thead><tr><th>ID</th><th>Kind</th><th>Name</th></tr></thead>
        <tbody>
          {rows.map(p => <tr key={p.partyId} onClick={() => void open(p.partyId)}><td>{p.partyId}</td><td>{p.kind}</td><td>{p.displayName}</td></tr>)}
        </tbody>
      </table>
      <p data-testid="parties-count">{count}</p>
      {detail && <div data-testid="party-detail">
        <h3>{detail.partyId} — {detail.displayName}</h3>
        <p>Kind {detail.kind}</p>
        <p data-testid="party-roles">Roles: {detail.roles.join(', ') || 'none'}</p>
        <pre>{JSON.stringify(detail.contactMechs, null, 2)}</pre>
      </div>}
    </section>
  );
}
