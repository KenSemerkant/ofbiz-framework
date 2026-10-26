import { useState } from 'react';
import { api } from '../api';

// P9-F3: order-area parity slice — order search, quote list, return list,
// matching the monolith order manager's find surfaces.
type Line = { productId: string; quantity: number; unitPrice?: string };
type Order = { orderId: string; customerId: string; status: string; grandTotal?: string; lines?: Line[] };
type Quote = { quoteId: string; customerId: string; status: string };
type ReturnOrder = { returnId: string; orderId: string; status: string };

export default function OrdersArea() {
  const [tab, setTab] = useState('orders');
  const [msg, setMsg] = useState('');
  const [customerId, setCustomerId] = useState('');
  const [status, setStatus] = useState('');
  const [orders, setOrders] = useState<Order[]>([]);
  const [quotes, setQuotes] = useState<Quote[]>([]);
  const [returns, setReturns] = useState<ReturnOrder[]>([]);

  const run = async (fn: () => Promise<void>) => {
    try { setMsg(''); await fn(); } catch (e) { setMsg((e as Error).message); }
  };

  const searchOrders = () => run(async () => {
    const q = new URLSearchParams();
    if (customerId) q.set('customerId', customerId);
    if (status) q.set('status', status);
    setOrders(await api<Order[]>(`/api/v1/orders?${q}`));
  });
  const loadQuotes = () => run(async () => setQuotes(await api<Quote[]>('/api/v1/quotes')));
  const loadReturns = () => run(async () => setReturns(await api<ReturnOrder[]>('/api/v1/returns')));

  const tabs = [['orders', 'Orders'], ['quotes', 'Quotes'], ['returns', 'Returns']] as const;

  return (
    <section aria-labelledby="orders-h">
      <h2 id="orders-h">Orders</h2>
      <p className="err" data-testid="orders-msg">{msg}</p>
      <nav className="tabs" aria-label="Order sections">
        {tabs.map(([id, label]) =>
          <button key={id} data-testid={`ord-tab-${id}`} aria-pressed={tab === id} onClick={() => setTab(id)}>{label}</button>)}
      </nav>

      {tab === 'orders' && <>
        <form onSubmit={e => { e.preventDefault(); searchOrders(); }}>
          <label>Customer <input value={customerId} onChange={e => setCustomerId(e.target.value)} /></label>
          <label>Status <input value={status} onChange={e => setStatus(e.target.value)} placeholder="e.g. APPROVED" /></label>
          <button>Search</button>
        </form>
        <table data-testid="order-table">
          <thead><tr><th>Order</th><th>Customer</th><th>Status</th><th>Total</th></tr></thead>
          <tbody>
            {orders.map(o => <tr key={o.orderId}><td>{o.orderId}</td><td>{o.customerId}</td><td>{o.status}</td><td>{o.grandTotal ?? ''}</td></tr>)}
          </tbody>
        </table>
      </>}

      {tab === 'quotes' && <>
        <button onClick={loadQuotes}>Load quotes</button>
        <table data-testid="quote-table">
          <thead><tr><th>Quote</th><th>Customer</th><th>Status</th></tr></thead>
          <tbody>
            {quotes.map(q => <tr key={q.quoteId}><td>{q.quoteId}</td><td>{q.customerId}</td><td>{q.status}</td></tr>)}
          </tbody>
        </table>
      </>}

      {tab === 'returns' && <>
        <button onClick={loadReturns}>Load returns</button>
        <table data-testid="return-table">
          <thead><tr><th>Return</th><th>Order</th><th>Status</th></tr></thead>
          <tbody>
            {returns.map(r => <tr key={r.returnId}><td>{r.returnId}</td><td>{r.orderId}</td><td>{r.status}</td></tr>)}
          </tbody>
        </table>
      </>}
    </section>
  );
}
