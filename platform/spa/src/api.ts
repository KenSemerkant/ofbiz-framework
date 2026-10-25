const GATEWAY = location.origin.replace(/:\d+$/, ':8080');

export async function api<T>(path: string, init?: RequestInit & { body?: unknown }): Promise<T> {
  const token = localStorage.getItem('ofbiz.token');
  const res = await fetch(GATEWAY + path, {
    ...init,
    headers: {
      Authorization: 'Bearer ' + token,
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
    },
    ...(init?.body ? { body: JSON.stringify(init.body) } : {}),
  });
  if (!res.ok) throw new Error(`${init?.method ?? 'GET'} ${path} -> ${res.status}`);
  return res.status === 204 ? (undefined as T) : res.json();
}
