import { useState } from 'react';
import { api } from '../api';

type Page<T> = { items: T[]; totalElements: number; page: number; totalPages: number };
type Product = { productId: string; internalName: string; productName?: string };

export default function Catalog() {
  const [rows, setRows] = useState<Product[]>([]);
  const [msg, setMsg] = useState('');
  const [prices, setPrices] = useState<Record<string, string>>({});

  const browse = async () => {
    try {
      const page = await api<Page<Product>>('/api/v1/products?page=0&size=20');
      setRows(page.items);
      setMsg(`${page.totalElements} products (page ${page.page + 1}/${page.totalPages})`);
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  const lookup = async (productId: string) => {
    try {
      const at = encodeURIComponent(new Date().toISOString());
      const { price } = await api<{ price: string }>(`/api/v1/products/${productId}/price?currency=USD&at=${at}`);
      setPrices(p => ({ ...p, [productId]: price + ' USD' }));
    } catch (e) {
      setPrices(p => ({ ...p, [productId]: 'n/a (' + (e as Error).message + ')' }));
    }
  };

  return (
    <section aria-labelledby="catalog-h">
      <h2 id="catalog-h">Catalog</h2>
      <p className="err" data-testid="catalog-msg">{msg}</p>
      <button onClick={() => void browse()}>Browse catalog</button>
      <table>
        <thead><tr><th>ID</th><th>Internal name</th><th>Product name</th><th>Price (USD)</th></tr></thead>
        <tbody>
          {rows.map(p => (
            <tr key={p.productId}>
              <td>{p.productId}</td><td>{p.internalName}</td><td>{p.productName ?? ''}</td>
              <td data-testid={`price-${p.productId}`}>
                {prices[p.productId] ?? <button onClick={() => void lookup(p.productId)}>lookup</button>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p data-testid="catalog-count">{msg}</p>
    </section>
  );
}
