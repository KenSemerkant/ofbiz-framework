import { useState } from 'react';
import { api } from '../api';

type Position = { emplPositionId: string; statusId: string };
type Onboarding = { onboardingId: string; status: string; steps: string[]; failedReason?: string };

export default function HireToRetire() {
  const [positionId, setPositionId] = useState('');
  const [position, setPosition] = useState<Position | null>(null);
  const [onboarding, setOnboarding] = useState<Onboarding | null>(null);
  const [readback, setReadback] = useState('');
  const [err, setErr] = useState('');

  const partyId = 'P1';

  const createPosition = async () => {
    setErr('');
    try {
      await api('/api/v1/positions', { method: 'POST',
        body: { emplPositionId: positionId, emplPositionTypeId: 'ENGINEER', statusId: 'ACTIVE' } });
      setPosition({ emplPositionId: positionId, statusId: 'ACTIVE' });
    } catch (e) { setErr((e as Error).message); }
  };

  const startOnboarding = async () => {
    setErr('');
    try {
      setOnboarding(await api<Onboarding>('/api/v1/onboarding', { method: 'POST',
        body: { partyId, emplPositionId: positionId } }));
    } catch (e) { setErr((e as Error).message); }
  };

  const readBack = async () => {
    setErr('');
    try {
      const o = await api<Onboarding>(`/api/v1/onboarding/${onboarding!.onboardingId}`);
      setReadback(`status ${o.status}, steps: ${o.steps.join(' → ')}`);
    } catch (e) { setErr((e as Error).message); }
  };

  const retirePosition = async () => {
    setErr('');
    try {
      await api(`/api/v1/positions/${positionId}`, { method: 'PUT',
        body: { emplPositionTypeId: 'ENGINEER', statusId: 'INACTIVE' } });
      const saved = await api<{ emplPositionId: string; statusId?: string }>(`/api/v1/positions/${positionId}`);
      setPosition({ emplPositionId: saved.emplPositionId, statusId: saved.statusId ?? 'INACTIVE' });
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="htr-h">
      <h2 id="htr-h">Hire-to-retire</h2>
      <p className="err" data-testid="htr-err">{err}</p>
      <p>party <strong>{partyId}</strong> (saga adds EMPLOYEE role + employment)</p>
      <label>Position ID <input value={positionId} onChange={e => setPositionId(e.target.value)} /></label>{' '}
      <button onClick={() => void createPosition()}>Create position</button>
      <p data-testid="htr-position">{position && `position ${position.emplPositionId} ${position.statusId}`}</p>
      {position && <button onClick={() => void startOnboarding()}>Start onboarding</button>}
      <p data-testid="htr-onboarding">{onboarding && `onboarding ${onboarding.onboardingId} status ${onboarding.status}`}</p>
      {onboarding && <button onClick={() => void readBack()}>Read back</button>}
      <p data-testid="htr-readback">{readback}</p>
      {onboarding && <button onClick={() => void retirePosition()}>Retire position</button>}
    </section>
  );
}
