import { useState } from 'react';
import { api } from '../api';

type Order = { orderId: string; status: string; trackingCode: string };
type AttributionDetail = { trackingCode: string; campaignId: string; orderCount: number };
const runId = () => 'e2e-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8) + '-';

export default function Campaign() {
  const [run, setRun] = useState('');
  const [facility, setFacility] = useState('');
  const [setup, setSetup] = useState('');
  const [order, setOrder] = useState<Order | null>(null);
  const [attribution, setAttribution] = useState('');
  const [err, setErr] = useState('');

  const customerId = 'P1';
  const productId = 'MP1';

  const setupCampaign = async () => {
    setErr('');
    try {
      const campaignId = run + 'cmp';
      const codeId = run + 'tc';
      await api('/api/v1/campaigns', { method: 'POST',
        body: { marketingCampaignId: campaignId, campaignName: 'e2e campaign ' + run,
          statusId: 'ACTIVE', fromDate: '2026-01-01', thruDate: '2026-12-31' } });
      await api('/api/v1/tracking-codes', { method: 'POST',
        body: { trackingCodeId: codeId, marketingCampaignId: campaignId,
          description: 'e2e attribution', fromDate: '2026-01-01', thruDate: '2026-12-31' } });
      setSetup(`campaign ${campaignId} + code ${codeId}`);
    } catch (e) { setErr((e as Error).message); }
  };

  const placeTrackedOrder = async () => {
    setErr('');
    try {
      const itemId = runId() + 'inv';
      await api('/api/v1/inventory-items', { method: 'POST',
        body: { inventoryItemId: itemId, productId, facilityId: facility } });
      await api(`/api/v1/inventory-items/${itemId}/receive`, { method: 'POST', body: { quantity: 5 } });
      setOrder(await api<Order>('/api/v1/orders', { method: 'POST',
        body: { customerId, currencyUomId: 'USD', trackingCode: run + 'tc',
          lines: [{ productId, facilityId: facility, quantity: 1 }] } }));
    } catch (e) { setErr((e as Error).message); }
  };

  const showAttribution = async () => {
    setErr('');
    // attribution is an event-driven read model (order.order.placed → marketing consumer);
    // bounded retry until the tracked order lands — same shape as Returns.tsx reserve saga.
    try {
      let detail: AttributionDetail | null = null;
      for (let attempt = 0; ; attempt++) {
        try {
          detail = await api<AttributionDetail>(`/api/v1/attribution/${run + 'tc'}`);
          if (detail.orderCount >= 1) break;
        } catch (e) {
          if (attempt >= 9) throw e; // 404 until the read model catches up
        }
        await new Promise(r => setTimeout(r, 300));
      }
      setAttribution(`code ${detail!.trackingCode} campaign ${detail!.campaignId} orders ${detail!.orderCount}`);
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="cmp-h">
      <h2 id="cmp-h">Campaign-to-attribution</h2>
      <p className="err" data-testid="cmp-err">{err}</p>
      <p>customer <strong>{customerId}</strong>, product <strong>{productId}</strong> ×1</p>
      <label>Run prefix <input value={run} onChange={e => setRun(e.target.value)} /></label>{' '}
      <button onClick={() => void setupCampaign()}>Create campaign + code</button>
      <p data-testid="cmp-setup">{setup}</p>
      <label>Facility <input value={facility} onChange={e => setFacility(e.target.value)} /></label>{' '}
      <button onClick={() => void placeTrackedOrder()}>Place tracked order</button>
      <p data-testid="cmp-order">{order && `order ${order.orderId} status ${order.status}`}</p>
      {order && <button onClick={() => void showAttribution()}>Show attribution</button>}
      <p data-testid="cmp-attribution">{attribution}</p>
    </section>
  );
}
