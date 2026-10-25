import { useState } from 'react';
import { api } from '../api';

type Quote = { quoteId: string; status: string };
type Order = { orderId: string; status: string; grandTotal: number; currencyUomId: string };
type Invoice = { invoiceId: string };
type Payment = { paymentId: string; status: string };
const runId = () => 'e2e-' + Date.now().toString(36) + '-';

export default function OrderToCash() {
  const [facility, setFacility] = useState('');
  const [stock, setStock] = useState('');
  const [quote, setQuote] = useState<Quote | null>(null);
  const [order, setOrder] = useState<Order | null>(null);
  const [billing, setBilling] = useState('');
  const [gl, setGl] = useState('');
  const [err, setErr] = useState('');

  const customerId = 'P1';
  const productId = 'MP1';
  const UNIT_PRICE = '9.99';
  const qty = 2;

  const seedStock = async () => {
    try {
      setErr('');
      const itemId = runId() + 'inv';
      await api('/api/v1/inventory-items', { method: 'POST',
        body: { inventoryItemId: itemId, productId, facilityId: facility } });
      await api(`/api/v1/inventory-items/${itemId}/receive`, { method: 'POST', body: { quantity: 10 } });
      setStock('stocked');
    } catch (e) { setErr((e as Error).message); }
  };

  const createQuote = async () => {
    try {
      setErr('');
      setQuote(await api<Quote>('/api/v1/quotes', { method: 'POST',
        body: { customerId, currencyUomId: 'USD',
          lines: [{ productId, facilityId: facility, quantity: qty }] } }));
    } catch (e) { setErr((e as Error).message); }
  };

  const convert = async () => {
    try {
      setErr('');
      setOrder(await api<Order>(`/api/v1/quotes/${quote!.quoteId}/convert`, { method: 'POST' }));
    } catch (e) { setErr((e as Error).message); }
  };

  const approve = async () => {
    try {
      setErr('');
      await api(`/api/v1/orders/${order!.orderId}/approve`, { method: 'POST' });
      setOrder(await api<Order>(`/api/v1/orders/${order!.orderId}`));
    } catch (e) { setErr((e as Error).message); }
  };

  const invoiceAndCapture = async () => {
    try {
      setErr('');
      const inv = runId() + 'inv';
      const pay = runId() + 'pay';
      const invoice = await api<Invoice>('/api/v1/invoices', { method: 'POST',
        body: { invoiceId: inv, customerId, billingAccountId: null, currencyUomId: 'USD',
          lines: [{ invoiceItemSeqId: '00001', invoiceItemTypeId: 'INV_FPROD_ITEM',
            description: 'e2e order-to-cash', quantity: qty, amount: UNIT_PRICE }] } });
      await api<Payment>('/api/v1/payments', { method: 'POST',
        body: { paymentId: pay, invoiceId: inv, idempotencyKey: pay,
          amount: String(order!.grandTotal), currencyUomId: 'USD' } });
      await api<Payment>(`/api/v1/payments/${pay}/authorize`, { method: 'POST' });
      const captured = await api<Payment>(`/api/v1/payments/${pay}/capture`, { method: 'POST' });
      setBilling(`invoice ${invoice.invoiceId} — payment ${pay} status: ${captured.status}`);
    } catch (e) { setErr((e as Error).message); }
  };

  const showTrialBalance = async () => {
    try {
      setErr('');
      const tb = await api<unknown>('/api/v1/gl/trial-balance');
      setGl('trial balance: ' + JSON.stringify(tb).slice(0, 200));
    } catch (e) { setErr((e as Error).message); }
  };

  return (
    <section aria-labelledby="otc-h">
      <h2 id="otc-h">Order-to-cash</h2>
      <p className="err" data-testid="otc-err">{err}</p>
      <p>customer <strong>{customerId}</strong>, product <strong>{productId}</strong> × {qty}</p>
      <label>Facility <input value={facility} onChange={e => setFacility(e.target.value)} /></label>{' '}
      <button onClick={() => void seedStock()}>Seed stock</button>
      <p data-testid="otc-stock">{stock}</p>
      <button onClick={() => void createQuote()}>Create quote</button>
      <p data-testid="otc-quote">{quote && `quote ${quote.quoteId} created (status: ${quote.status})`}</p>
      {quote && <button onClick={() => void convert()}>Convert to order</button>}
      <p data-testid="otc-order">
        {order && `order ${order.orderId} status ${order.status} — total ${order.grandTotal} ${order.currencyUomId}`}
      </p>
      {order && <button onClick={() => void approve()}>Approve</button>}
      {order && order.status === 'APPROVED' && <button onClick={() => void invoiceAndCapture()}>Invoice + capture</button>}
      <p data-testid="otc-billing">{billing}</p>
      <button onClick={() => void showTrialBalance()}>Show trial balance</button>
      <p data-testid="otc-gl">{gl}</p>
    </section>
  );
}
