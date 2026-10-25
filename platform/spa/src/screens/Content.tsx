import { useState } from 'react';
import { api } from '../api';
import { GATEWAY } from '../config';

export default function Content() {
  const [contentId, setContentId] = useState('');
  const [status, setStatus] = useState('');
  const [data, setData] = useState('');
  const [err, setErr] = useState('');

  // create → upload (raw bytes) → publish → read back statusId from the server
  const createUploadPublish = async () => {
    setErr('');
    try {
      await api('/api/v1/contents', { method: 'POST',
        body: { contentId, contentTypeId: 'DOCUMENT', contentName: 'e2e ' + contentId } });
      const token = localStorage.getItem('ofbiz.token');
      if (!token) throw new Error('not signed in');
      const up = await fetch(`${GATEWAY}/api/v1/contents/${contentId}/upload`, {
        method: 'POST',
        headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'text/plain' },
        body: 'e2e content roundtrip ' + contentId });
      if (!up.ok) throw new Error(`upload -> ${up.status}`);
      await api(`/api/v1/contents/${contentId}/publish`, { method: 'POST' });
      // server truth: read back rather than trusting the non-throw path
      const saved = await api<{ contentId: string; statusId?: string }>(`/api/v1/contents/${contentId}`);
      setStatus(saved.statusId ? String(saved.statusId).toLowerCase() : '(no statusId in readback)');
    } catch (e) { setErr((e as Error).message); }
  };

  const fetchData = async () => {
    setErr('');
    try {
      const token = localStorage.getItem('ofbiz.token');
      if (!token) throw new Error('not signed in');
      const res = await fetch(`${GATEWAY}/api/v1/contents/${contentId}/data`,
        { headers: { Authorization: 'Bearer ' + token } });
      if (!res.ok) throw new Error(`data -> ${res.status}`);
      setData((await res.text()).slice(0, 200));
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="cnt-h">
      <h2 id="cnt-h">Content</h2>
      <p className="err" data-testid="cnt-err">{err}</p>
      <label>Content ID <input value={contentId} onChange={e => setContentId(e.target.value)} /></label>{' '}
      <button onClick={() => void createUploadPublish()} disabled={!contentId}>Create + upload + publish</button>
      <p data-testid="cnt-status">{status}</p>
      {status && <button onClick={() => void fetchData()}>Fetch data</button>}
      <p data-testid="cnt-data">{data}</p>
    </section>
  );
}
