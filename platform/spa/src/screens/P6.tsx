import { useState } from 'react';
import { api } from '../api';

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
  return (
    <section aria-labelledby="p6-h">
      <h2 id="p6-h">Long-tail domains (P6)</h2>
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
