import { useState } from 'react';
import { api } from '../api';

// P9-F5..F9 long-tail listing surfaces: one Load button per domain, matching
// each monolith app's find screen.
const LISTS = [
  { title: 'Positions (humanres)', endpoint: '/api/v1/positions', testid: 'positions' },
  { title: 'Employments (humanres)', endpoint: '/api/v1/employments', testid: 'employments' },
  { title: 'Contents (content)', endpoint: '/api/v1/contents', testid: 'contents' },
  { title: 'Campaigns (marketing)', endpoint: '/api/v1/campaigns', testid: 'campaigns' },
  { title: 'Tracking codes (marketing)', endpoint: '/api/v1/tracking-codes', testid: 'tracking-codes' },
  { title: 'Opportunities (marketing)', endpoint: '/api/v1/opportunities', testid: 'opportunities' },
  { title: 'Routings (manufacturing)', endpoint: '/api/v1/routings', testid: 'routings' },
  { title: 'MRP proposals (manufacturing)', endpoint: '/api/v1/mrp/proposals', testid: 'mrp-proposals' },
  { title: 'Work efforts (workeffort)', endpoint: '/api/v1/workefforts', testid: 'workefforts' },
  { title: 'Timesheets (workeffort)', endpoint: '/api/v1/timesheets', testid: 'timesheets' },
];

const DOMAINS = [
  { title: 'BOM (manufacturing)', endpoint: '/api/v1/boms', id: 'productId',
    fields: [['product', 'productId'], ['component', 'componentId'], ['qty', 'quantity']] },
  { title: 'Work effort (workeffort)', endpoint: '/api/v1/workefforts', id: 'workEffortId',
    fields: [['id', 'workEffortId'], ['type', 'workEffortTypeId'], ['name', 'workEffortName']] },
  { title: 'Position (humanres)', endpoint: '/api/v1/positions', id: 'emplPositionId',
    fields: [['id', 'emplPositionId'], ['type', 'emplPositionTypeId']] },
  { title: 'Content', endpoint: '/api/v1/contents', id: 'contentId',
    fields: [['id', 'contentId'], ['name', 'contentName'], ['type', 'contentTypeId']] },
  { title: 'Campaign (marketing)', endpoint: '/api/v1/campaigns', id: 'marketingCampaignId',
    fields: [['id', 'marketingCampaignId'], ['name', 'campaignName']] },
];

const PAYLOAD: Record<string, (v: Record<string, string>) => unknown> = {
  productId: v => ({ productId: v.productId,
    components: [{ productId: v.componentId, quantity: Number(v.quantity) || 1, scrapPct: 0 }] }),
  workEffortId: v => ({ ...v, workEffortTypeId: v.workEffortTypeId || 'TASK' }),
  emplPositionId: v => ({ ...v, statusId: 'ACTIVE' }),
  contentId: v => ({ ...v, contentTypeId: v.contentTypeId || 'DOCUMENT' }),
  marketingCampaignId: v => ({ ...v, statusId: 'ACTIVE', fromDate: '2026-01-01', thruDate: '2026-12-31' }),
};

export default function P6() {
  const [results, setResults] = useState<Record<string, string>>({});
  const [listRows, setListRows] = useState<Record<string, unknown[]>>({});
  const load = async (key: string, endpoint: string) => {
    try {
      setListRows(r => ({ ...r, [key]: [] }));
      const rows = await api<unknown[]>(endpoint);
      setListRows(r => ({ ...r, [key]: rows }));
    } catch (e) {
      setResults(s => ({ ...s, [key]: (e as Error).message }));
    }
  };
  return (
    <section aria-labelledby="p6-h">
      <h2 id="p6-h">Long-tail domains (P6)</h2>
      <div>
        <h3>Find screens (P9 parity)</h3>
        {LISTS.map(l => (
          <div key={l.testid}>
            <button data-testid={`load-${l.testid}`} onClick={() => void load(l.testid, l.endpoint)}>
              {l.title}
            </button>{' '}
            <span data-testid={`count-${l.testid}`}>
              {listRows[l.testid] ? `${(listRows[l.testid] as unknown[]).length} rows` : ''}
            </span>
            <p className="err">{results[l.testid] ?? ''}</p>
          </div>
        ))}
      </div>
      {DOMAINS.map(d => (
        <div key={d.id}>
          <h3>{d.title}</h3>
          <form onSubmit={async e => {
            e.preventDefault();
            const v = Object.fromEntries(new FormData(e.currentTarget)) as Record<string, string>;
            try {
              await api(d.endpoint, { method: 'POST', body: PAYLOAD[d.id](v) });
              const saved = await api(`${d.endpoint}/${encodeURIComponent(v[d.id])}`);
              setResults(r => ({ ...r, [d.id]: 'saved + read back: ' + JSON.stringify(saved).slice(0, 120) }));
            } catch (err) {
              setResults(r => ({ ...r, [d.id]: (err as Error).message }));
            }
          }}>
            {d.fields.map(([label, name]) => (
              <label key={name}>{label} <input name={name} aria-label={label} /></label>
            ))}
            <button>create</button>
          </form>
          <p data-testid={`p6-result-${d.id}`}>{results[d.id] ?? '—'}</p>
        </div>
      ))}
    </section>
  );
}
