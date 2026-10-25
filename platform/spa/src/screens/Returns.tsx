import { useState } from 'react';
import { api } from '../api';

type Order = { orderId: string; status: string };
type ReturnOrder = { returnId: string; orderId: string; status: string;
  lines: { productId: string; facilityId: string; quantity: number }[] };
const runId = () => 'e2e-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8) + '-';

export default function Returns() {
  const [facility, setFacility] = useState('');
  const [stock, setStock] = useState('');
  const [order, setOrder] = useState<Order | null>(null);
  const [ret, setRet] = useState<ReturnOrder | null>(null);
  const [readback, setReadback] = useState('');
  const [err, setErr] = useState('');

  const customerId = 'P1';
  const productId = 'MP1';

  const seedStock = async () => {
    setErr('');
    try {
      const itemId = runId() + 'inv';
      await api('/api/v1/inventory-items', { method: 'POST',
        body: { inventoryItemId: itemId, productId, facilityId: facility } });
      await api(`/api/v1/inventory-items/${itemId}/receive`, { method: 'POST', body: { quantity: 5 } });
      setStock('stocked');
    } catch (e) { setErr((e as Error).message); }
  };

  const placeOrder = async () => {
    setErr('');
    try {
      setOrder(await api<Order>('/api/v1/orders', { method: 'POST',
        body: { customerId, currencyUomId: 'USD',
          lines: [{ productId, facilityId: facility, quantity: 2 }] } }));
    } catch (e) { setErr((e as Error).message); }
  };

  const transition = async (action: 'approve' | 'complete') => {
    setErr('');
    try {
      await api(`/api/v1/orders/${order!.orderId}/${action}`, { method: 'POST' });
      setOrder(await api<Order>(`/api/v1/orders/${order!.orderId}`));
    } catch (e) { setErr((e as Error).message); }
  };

  const createReturn = async () => {
    setErr('');
    try {
      // the reserve saga (placed → Kafka consumer → reservation receipt in
      // order-svc saga_state) is async; retry briefly until the receipt for
      // this order's lines is on file server-side (else returns 409)
      for (let attempt = 0; ; attempt++) {
        try {
          setRet(await api<ReturnOrder>('/api/v1/returns', { method: 'POST',
            body: { orderId: order!.orderId,
              lines: [{ productId, facilityId: facility, quantity: 1 }] } }));
          break;
        } catch (e) {
          if (attempt >= 9) throw e;
          await new Promise(r => setTimeout(r, 300));
        }
      }
    } catch (e) { setErr((e as Error).message); }
  };

  const readBack = async () => {
    setErr('');
    try {
      const r = await api<ReturnOrder>(`/api/v1/returns/${ret!.returnId}`);
      setReadback(`${r.status} ${r.lines.map(l => `${l.productId}×${l.quantity}`).join(', ')}`);
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="ret-h">
      <h2 id="ret-h">Returns</h2>
      <p className="err" data-testid="ret-err">{err}</p>
      <p>customer <strong>{customerId}</strong>, product <strong>{productId}</strong> ×2, return 1</p>
      <label>Facility <input value={facility} onChange={e => setFacility(e.target.value)} /></label>{' '}
      <button onClick={() => void seedStock()}>Seed stock</button>
      <p data-testid="ret-stock">{stock}</p>
      <button onClick={() => void placeOrder()}>Place order</button>
      <p data-testid="ret-order">{order && `order ${order.orderId} status ${order.status}`}</p>
      {order && <button onClick={() => void transition('approve')}>Approve</button>}
      {order && <button onClick={() => void transition('complete')}>Complete</button>}
      {order?.status === 'COMPLETED' && <button onClick={() => void createReturn()}>Create return</button>}
      <p data-testid="ret-return">{ret && `return ${ret.returnId} status ${ret.status}`}</p>
      {ret && <button onClick={() => void readBack()}>Read back</button>}
      <p data-testid="ret-readback">{readback}</p>
    </section>
  );
}
