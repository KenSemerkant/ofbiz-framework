import { useState } from 'react';
import { api } from '../api';

type Page<T> = { items: T[]; totalElements: number; page: number; totalPages: number };
type Party = { partyId: string; kind: string; displayName: string };
type Mech = { contactMechId: string; type: string; value: string; purposes?: string[] };
type PartyDetail = { partyId: string; kind: string; displayName: string; roles: string[]; contactMechs: Mech[] };

// P9-F4/P10-F0: find + detail, plus the partymgr edit slice — create/edit
// person or group, grant/revoke roles, add contact mechs. The upsert
// contract is full-state: we GET the current party, apply the user's edits
// client-side, and POST the whole desired state (the service computes role
// granted/revoked deltas and replaces mechs).
export default function Parties() {
  const [role, setRole] = useState('CUSTOMER');
  const [rows, setRows] = useState<Party[]>([]);
  const [msg, setMsg] = useState('');
  const [count, setCount] = useState('');
  const [detail, setDetail] = useState<PartyDetail | null>(null);
  const [editing, setEditing] = useState(false);
  // editor state (mirrors UpsertPartyRequest)
  const [kind, setKind] = useState('PERSON');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [groupName, setGroupName] = useState('');
  const [rolesText, setRolesText] = useState('');
  const [mechs, setMechs] = useState<Mech[]>([]);
  const [mechType, setMechType] = useState('EMAIL_ADDRESS');
  const [mechValue, setMechValue] = useState('');
  const [partyId, setPartyId] = useState('');

  const run = async (fn: () => Promise<void>) => {
    try { setMsg(''); await fn(); } catch (e) { setMsg((e as Error).message); }
  };

  const search = () => run(async () => {
    const q = new URLSearchParams({ page: '0', size: '20' });
    if (role.trim()) q.set('role', role.trim());
    const page = await api<Page<Party>>(`/api/v1/parties?${q}`);
    setRows(page.items);
    setCount(`${page.totalElements} parties (page ${page.page + 1}/${page.totalPages})`);
    setMsg('');
    setDetail(null);
    setEditing(false);
  });

  const open = async (id: string) => {
    try {
      setDetail(await api<PartyDetail>(`/api/v1/parties/${encodeURIComponent(id)}`));
      setEditing(false);
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  const startEdit = (d: PartyDetail | null) => {
    setEditing(true);
    setDetail(d);
    setKind(d?.kind === 'PARTY_GROUP' ? 'PARTY_GROUP' : 'PERSON');
    const names = (d?.displayName ?? '').split(' ');
    setFirstName(d && d.kind === 'PERSON' ? (names[0] ?? '') : '');
    setLastName(d && d.kind === 'PERSON' ? (names.slice(1).join(' ') ?? '') : '');
    setGroupName(d?.kind === 'PARTY_GROUP' ? (d.displayName ?? '') : '');
    setRolesText((d?.roles ?? []).join(', '));
    setMechs(d?.contactMechs ?? []);
    setPartyId(d?.partyId ?? '');
    setMechType('EMAIL_ADDRESS');
    setMechValue('');
  };

  const upsertParty = () => run(async () => {
    const body = {
      partyId: partyId.trim(),
      kind,
      firstName: kind === 'PERSON' ? firstName.trim() : undefined,
      lastName: kind === 'PERSON' ? lastName.trim() : undefined,
      groupName: kind === 'PARTY_GROUP' ? groupName.trim() : undefined,
      roles: rolesText.split(',').map(r => r.trim()).filter(Boolean),
      contactMechs: mechs.map(m => ({
        contactMechId: m.contactMechId, type: m.type, value: m.value, purposes: m.purposes ?? [],
      })),
    };
    await api('/api/v1/parties', { method: 'POST', body });
    await open(body.partyId);
    setEditing(false);
  });

  const addMech = () => {
    if (!mechValue.trim() || !partyId.trim()) return;
    setMechs(m => [...m, {
      contactMechId: `${partyId}-M${m.length + 1}`, type: mechType, value: mechValue.trim(), purposes: [],
    }]);
    setMechValue('');
  };

  return (
    <section aria-labelledby="parties-h">
      <h2 id="parties-h">Parties</h2>
      <p className="err" data-testid="parties-msg">{msg}</p>
      <form onSubmit={e => { e.preventDefault(); search(); }}>
        <label>Role <input value={role} onChange={e => setRole(e.target.value)} placeholder="blank = all" /></label>
        <button>Search</button>
      </form>
      <button data-testid="new-party" onClick={() => startEdit(null)}>New party</button>
      <table>
        <thead><tr><th>ID</th><th>Kind</th><th>Name</th></tr></thead>
        <tbody>
          {rows.map(p => <tr key={p.partyId} onClick={() => void open(p.partyId)}><td>{p.partyId}</td><td>{p.kind}</td><td>{p.displayName}</td></tr>)}
        </tbody>
      </table>
      <p data-testid="parties-count">{count}</p>

      {detail && !editing && <div data-testid="party-detail">
        <h3>{detail.partyId} — {detail.displayName}</h3>
        <p>Kind {detail.kind}</p>
        <p data-testid="party-roles">Roles: {detail.roles.join(', ') || 'none'}</p>
        <ul>{detail.contactMechs.map(m => <li key={m.contactMechId}>{m.type}: {m.value}</li>)}</ul>
        <button data-testid="edit-party" onClick={() => startEdit(detail)}>Edit</button>
      </div>}

      {editing && <form data-testid="party-editor" onSubmit={e => { e.preventDefault(); upsertParty(); }}>
        <h3>{partyId ? `Edit ${partyId}` : 'New party'}</h3>
        <label>ID <input value={partyId} onChange={e => setPartyId(e.target.value)} disabled={!!detail} required /></label>
        <label>Kind <select value={kind} onChange={e => setKind(e.target.value)} disabled={!!detail}>
          <option value="PERSON">Person</option>
          <option value="PARTY_GROUP">Group</option>
        </select></label>
        {kind === 'PERSON' && <>
          <label>First name <input value={firstName} onChange={e => setFirstName(e.target.value)} /></label>
          <label>Last name <input value={lastName} onChange={e => setLastName(e.target.value)} /></label>
        </>}
        {kind === 'PARTY_GROUP' &&
          <label>Group name <input value={groupName} onChange={e => setGroupName(e.target.value)} /></label>}
        <label>Roles (comma-separated) <input value={rolesText} onChange={e => setRolesText(e.target.value)} placeholder="CUSTOMER, SUPPLIER" /></label>
        <fieldset>
          <legend>Contact mechs</legend>
          <ul>{mechs.map((m, i) => <li key={m.contactMechId}>
            {m.type}: {m.value} <button type="button" onClick={() => setMechs(xs => xs.filter((_, j) => j !== i))}>remove</button>
          </li>)}</ul>
          <label>Type <select value={mechType} onChange={e => setMechType(e.target.value)}>
            <option value="EMAIL_ADDRESS">Email</option>
            <option value="TELECOM_NUMBER">Phone</option>
            <option value="POSTAL_ADDRESS">Postal</option>
          </select></label>
          <label>Value <input value={mechValue} onChange={e => setMechValue(e.target.value)} /></label>
          <button type="button" data-testid="add-mech" onClick={addMech}>Add mech</button>
        </fieldset>
        <button>Save party</button>
        <button type="button" onClick={() => setEditing(false)}>Cancel</button>
      </form>}
    </section>
  );
}
