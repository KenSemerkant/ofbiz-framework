import { useState } from 'react';
import { api } from '../api';

// P9-F2: product-area parity slice — products + price (P8) plus categories,
// stores, facilities, stock per facility, and shipments, matching the
// monolith product app's find/browse surfaces.
type Page<T> = { items: T[]; totalElements: number; page: number; totalPages: number };
type Product = { productId: string; internalName: string; productName?: string };
type Category = { productCategoryId: string; primaryParentCategoryId?: string; categoryName: string };
type Store = { productStoreId: string; storeName: string; defaultCurrencyUomId: string; inventoryFacilityId?: string };
type Facility = { facilityId: string; facilityName: string; facilityTypeId?: string };
type Item = { inventoryItemId: string; productId: string; facilityId: string; quantityOnHandTotal: string; availableToPromiseTotal: string };
type Shipment = { shipmentId: string; orderId: string; status: string };

export default function Catalog() {
  const [tab, setTab] = useState('products');
  const [rows, setRows] = useState<Product[]>([]);
  const [msg, setMsg] = useState('');
  const [prices, setPrices] = useState<Record<string, string>>({});
  const [categories, setCategories] = useState<Category[]>([]);
  const [stores, setStores] = useState<Store[]>([]);
  const [facilities, setFacilities] = useState<Facility[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [shipments, setShipments] = useState<Shipment[]>([]);
  const [facilityId, setFacilityId] = useState('');

  const run = async (fn: () => Promise<void>) => {
    try { setMsg(''); await fn(); } catch (e) { setMsg((e as Error).message); }
  };

  const browse = () => run(async () => {
    const page = await api<Page<Product>>('/api/v1/products?page=0&size=20');
    setRows(page.items);
    setMsg(`${page.totalElements} products (page ${page.page + 1}/${page.totalPages})`);
  });

  const lookup = async (productId: string) => {
    try {
      const at = encodeURIComponent(new Date().toISOString());
      const { price } = await api<{ price: string }>(`/api/v1/products/${productId}/price?currency=USD&at=${at}`);
      setPrices(p => ({ ...p, [productId]: price + ' USD' }));
    } catch (e) {
      setPrices(p => ({ ...p, [productId]: 'n/a (' + (e as Error).message + ')' }));
    }
  };

  const loadCategories = () => run(async () => setCategories(await api<Category[]>('/api/v1/categories')));
  const loadStores = () => run(async () => setStores(await api<Store[]>('/api/v1/stores')));
  const loadFacilities = () => run(async () => setFacilities(await api<Facility[]>('/api/v1/facilities')));
  const searchStock = () => run(async () => {
    const q = facilityId ? `?facilityId=${encodeURIComponent(facilityId)}` : '';
    setItems(await api<Item[]>(`/api/v1/inventory-items${q}`));
  });
  const loadShipments = () => run(async () => setShipments(await api<Shipment[]>('/api/v1/shipments')));

  const tabs = [
    ['products', 'Products'], ['categories', 'Categories'], ['stores', 'Stores'],
    ['facilities', 'Facilities'], ['stock', 'Stock'], ['shipments', 'Shipments'],
  ] as const;

  return (
    <section aria-labelledby="catalog-h">
      <h2 id="catalog-h">Catalog</h2>
      <p className="err" data-testid="catalog-msg">{msg}</p>
      <nav className="tabs" aria-label="Catalog sections">
        {tabs.map(([id, label]) =>
          <button key={id} data-testid={`cat-tab-${id}`} aria-pressed={tab === id} onClick={() => setTab(id)}>{label}</button>)}
      </nav>

      {tab === 'products' && <>
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
      </>}

      {tab === 'categories' && <>
        <button onClick={loadCategories}>Load categories</button>
        <table data-testid="category-table">
          <thead><tr><th>ID</th><th>Parent</th><th>Name</th></tr></thead>
          <tbody>
            {categories.map(c => <tr key={c.productCategoryId}><td>{c.productCategoryId}</td><td>{c.primaryParentCategoryId ?? ''}</td><td>{c.categoryName}</td></tr>)}
          </tbody>
        </table>
      </>}

      {tab === 'stores' && <>
        <button onClick={loadStores}>Load stores</button>
        <table data-testid="store-table">
          <thead><tr><th>ID</th><th>Name</th><th>Currency</th><th>Facility</th></tr></thead>
          <tbody>
            {stores.map(s => <tr key={s.productStoreId}><td>{s.productStoreId}</td><td>{s.storeName}</td><td>{s.defaultCurrencyUomId}</td><td>{s.inventoryFacilityId ?? ''}</td></tr>)}
          </tbody>
        </table>
      </>}

      {tab === 'facilities' && <>
        <button onClick={loadFacilities}>Load facilities</button>
        <table data-testid="facility-table">
          <thead><tr><th>ID</th><th>Name</th><th>Type</th></tr></thead>
          <tbody>
            {facilities.map(f => <tr key={f.facilityId}><td>{f.facilityId}</td><td>{f.facilityName}</td><td>{f.facilityTypeId ?? ''}</td></tr>)}
          </tbody>
        </table>
      </>}

      {tab === 'stock' && <>
        <form onSubmit={e => { e.preventDefault(); searchStock(); }}>
          <label>Facility <input value={facilityId} onChange={e => setFacilityId(e.target.value)} /></label>
          <button>Search stock</button>
        </form>
        <table data-testid="stock-table">
          <thead><tr><th>Item</th><th>Product</th><th>Facility</th><th>QOH</th><th>ATP</th></tr></thead>
          <tbody>
            {items.map(i => <tr key={i.inventoryItemId}><td>{i.inventoryItemId}</td><td>{i.productId}</td><td>{i.facilityId}</td><td>{i.quantityOnHandTotal}</td><td>{i.availableToPromiseTotal}</td></tr>)}
          </tbody>
        </table>
      </>}

      {tab === 'shipments' && <>
        <button onClick={loadShipments}>Load shipments</button>
        <table data-testid="shipment-table">
          <thead><tr><th>Shipment</th><th>Order</th><th>Status</th></tr></thead>
          <tbody>
            {shipments.map(s => <tr key={s.shipmentId}><td>{s.shipmentId}</td><td>{s.orderId}</td><td>{s.status}</td></tr>)}
          </tbody>
        </table>
      </>}
    </section>
  );
}
