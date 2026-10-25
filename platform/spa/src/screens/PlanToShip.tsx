import { useState } from 'react';
import { api } from '../api';

type Order = { orderId: string; status: string };
type Shipment = { shipmentId: string; orderId: string; status: string };
type MrpRun = { proposals: { productId: string; quantity: number }[] };
const runId = () => 'e2e-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8) + '-';

export default function PlanToShip() {
  const [facility, setFacility] = useState('');
  const [itemId, setItemId] = useState('');
  const [stock, setStock] = useState('');
  const [order, setOrder] = useState<Order | null>(null);
  const [mrp, setMrp] = useState('');
  const [shipment, setShipment] = useState<Shipment | null>(null);
  const [err, setErr] = useState('');

  const customerId = 'P1';
  const productId = 'MP1';

  const seedStock = async () => {
    setErr('');
    try {
      const id = runId() + 'inv';
      await api('/api/v1/inventory-items', { method: 'POST',
        body: { inventoryItemId: id, productId, facilityId: facility } });
      await api(`/api/v1/inventory-items/${id}/receive`, { method: 'POST', body: { quantity: 5 } });
      setItemId(id);
      setStock(`stocked ${id}`);
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

  const runMrp = async () => {
    setErr('');
    try {
      const result = await api<MrpRun>('/api/v1/mrp/run', { method: 'POST' });
      setMrp(`MRP run complete: ${result.proposals.length} proposals`);
    } catch (e) { setErr((e as Error).message); }
  };

  const createShipment = async () => {
    setErr('');
    try {
      const id = runId() + 'shp';
      setShipment(await api<Shipment>('/api/v1/shipments', { method: 'POST',
        body: { shipmentId: id, orderId: order!.orderId,
          items: [{ productId, quantity: 2, inventoryItemId: itemId }] } }));
    } catch (e) { setErr((e as Error).message); }
  };

  const transition = async (action: 'pack' | 'ship') => {
    setErr('');
    try {
      setShipment(await api<Shipment>(`/api/v1/shipments/${shipment!.shipmentId}/${action}`, { method: 'POST' }));
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="pts-h">
      <h2 id="pts-h">Plan-to-ship</h2>
      <p className="err" data-testid="pts-err">{err}</p>
      <p>customer <strong>{customerId}</strong>, product <strong>{productId}</strong> ×2</p>
      <label>Facility <input value={facility} onChange={e => setFacility(e.target.value)} /></label>{' '}
      <button onClick={() => void seedStock()}>Seed stock</button>
      <p data-testid="pts-stock">{stock}</p>
      <button onClick={() => void placeOrder()}>Place order</button>
      <p data-testid="pts-order">{order && `order ${order.orderId} status ${order.status}`}</p>
      <button onClick={() => void runMrp()}>Run MRP</button>
      <p data-testid="pts-mrp">{mrp}</p>
      {order && !shipment && <button onClick={() => void createShipment()}>Create shipment</button>}
      <p data-testid="pts-shipment">
        {shipment && `shipment ${shipment.shipmentId} status ${shipment.status}`}
      </p>
      {shipment && <button onClick={() => void transition('pack')}>Pack</button>}{' '}
      {shipment && <button onClick={() => void transition('ship')}>Ship</button>}
    </section>
  );
}
