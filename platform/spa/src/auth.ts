// P8: token in localStorage deliberately — Playwright storageState persists
// it across specs. Dev-only client secret, same trust level as the skeleton.
import { GATEWAY } from './config';

const REDIRECT = location.origin + '/login/oauth2/code/gateway';
const BASIC = 'Basic ' + btoa('gateway:dev-local-secret');

const b64url = (s: string) =>
  btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

async function sha256(s: string): Promise<string> {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s));
  return String.fromCharCode(...new Uint8Array(buf));
}

export async function login() {
  const verifier = b64url(btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32)))));
  localStorage.setItem('ofbiz.verifier', verifier);
  const state = b64url(btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(16)))));
  sessionStorage.setItem('ofbiz.state', state);
  const challenge = b64url(await sha256(verifier));
  const auth = new URL(GATEWAY + '/oauth2/authorize');
  auth.search = new URLSearchParams({
    response_type: 'code', client_id: 'gateway', redirect_uri: REDIRECT,
    scope: 'openid', code_challenge: challenge, code_challenge_method: 'S256',
    state,
  }).toString();
  location.assign(auth);
}

export async function handleCallback(code: string, state: string): Promise<boolean> {
  // Claim the single-use verifier first so a duplicate invocation (React
  // StrictMode double-invoke) is a no-op before any fetch.
  const verifier = localStorage.getItem('ofbiz.verifier');
  if (!verifier) return false;
  localStorage.removeItem('ofbiz.verifier');
  if (sessionStorage.getItem('ofbiz.state') !== state) return false;
  sessionStorage.removeItem('ofbiz.state');
  const res = await fetch(GATEWAY + '/oauth2/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: BASIC,
    },
    body: new URLSearchParams({
      grant_type: 'authorization_code', code, client_id: 'gateway',
      redirect_uri: REDIRECT, code_verifier: verifier,
    }),
  });
  if (!res.ok) return false;
  const { access_token } = await res.json();
  if (!access_token) return false;
  localStorage.setItem('ofbiz.token', access_token);
  return true;
}
