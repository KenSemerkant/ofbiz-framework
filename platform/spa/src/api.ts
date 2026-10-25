import { GATEWAY } from './config';

export async function api<T>(
  path: string,
  init?: Omit<RequestInit, 'body'> & { body?: unknown },
): Promise<T> {
  const token = localStorage.getItem('ofbiz.token');
  if (!token) throw new Error('not signed in');
  const { body, ...rest } = init ?? {};
  const res = await fetch(GATEWAY + path, {
    ...rest,
    headers: {
      ...(rest.headers as HeadersInit | undefined),
      Authorization: 'Bearer ' + token,
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  if (!res.ok) throw new Error(`${rest.method ?? 'GET'} ${path} -> ${res.status}`);
  return res.status === 204 ? (undefined as T) : res.json();
}
