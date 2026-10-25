import { useState } from 'react';
import { api, apiText } from '../api';

// per-effort feed (plan NOTE): deterministic for the effort this screen just
// created; the global feed renders every calendar-ready effort — an effort
// without both dates is omitted entirely, and its own export 409s. The create
// payload therefore pins both dates so the per-effort export has its VEVENT.
const EST_START = '2026-01-05';
const EST_END = '2026-01-11';

export default function CalendarScreen() {
  const [effortId, setEffortId] = useState('');
  const [name, setName] = useState('');
  const [effort, setEffort] = useState('');
  const [feed, setFeed] = useState('');
  const [err, setErr] = useState('');

  const createEffort = async () => {
    setErr('');
    try {
      await api('/api/v1/workefforts', { method: 'POST',
        body: { workEffortId: effortId, workEffortTypeId: 'TASK', workEffortName: name,
          estimatedStartDate: EST_START, estimatedCompletionDate: EST_END } });
      const saved = await api<{ workEffortId: string }>(`/api/v1/workefforts/${effortId}`);
      setEffort(`effort ${saved.workEffortId}`);
    } catch (e) { setErr((e as Error).message); }
  };

  const loadFeed = async () => {
    setErr('');
    try {
      setFeed((await apiText(`/api/v1/workefforts/${effortId}/calendar.ics`)).slice(0, 2000));
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="cal-h">
      <h2 id="cal-h">Calendar</h2>
      <p className="err" data-testid="cal-err">{err}</p>
      <label>Work effort ID <input value={effortId} onChange={e => setEffortId(e.target.value)} /></label>{' '}
      <label>Name <input value={name} onChange={e => setName(e.target.value)} /></label>{' '}
      <button onClick={() => void createEffort()} disabled={!effortId || !name}>Create effort</button>
      <p data-testid="cal-effort">{effort}</p>
      {effort && <button onClick={() => void loadFeed()}>Load feed</button>}
      <pre data-testid="cal-feed">{feed}</pre>
    </section>
  );
}
