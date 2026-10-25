import { useState } from 'react';
import { api } from '../api';

type Page<T> = { items: T[]; totalElements: number; page: number; totalPages: number };
type Party = { partyId: string; kind: string; displayName: string };

export default function Parties() {
  const [role, setRole] = useState('CUSTOMER');
  const [rows, setRows] = useState<Party[]>([]);
  const [msg, setMsg] = useState('');
  const [count, setCount] = useState('');

  const search = async () => {
    try {
      const page = await api<Page<Party>>(`/api/v1/parties?role=${encodeURIComponent(role)}&page=0&size=20`);
      setRows(page.items);
      setCount(`${page.totalElements} parties (page ${page.page + 1}/${page.totalPages})`);
      setMsg('');
    } catch (e) {
      setMsg((e as Error).message);
      setCount('');
    }
  };

  return (
    <section aria-labelledby="parties-h">
      <h2 id="parties-h">Parties</h2>
      <p className="err" data-testid="parties-msg">{msg}</p>
      <form onSubmit={e => { e.preventDefault(); void search(); }}>
        <label>Role <input value={role} onChange={e => setRole(e.target.value)} /></label>
        <button>Search</button>
      </form>
      <table>
        <thead><tr><th>ID</th><th>Kind</th><th>Name</th></tr></thead>
        <tbody>
          {rows.map(p => <tr key={p.partyId}><td>{p.partyId}</td><td>{p.kind}</td><td>{p.displayName}</td></tr>)}
        </tbody>
      </table>
      <p data-testid="parties-count">{count}</p>
    </section>
  );
}
