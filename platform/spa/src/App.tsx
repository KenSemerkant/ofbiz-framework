import { Routes, Route, NavLink, useSearchParams, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import { login, handleCallback } from './auth';

export default function App() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const code = params.get('code');
  const signedIn = !!localStorage.getItem('ofbiz.token');
  useEffect(() => {
    if (!code) return;
    void handleCallback(code).then(ok => navigate(ok ? '/parties' : '/', { replace: true }));
  }, [code, navigate]);
  return (
    <>
      <nav>
        <NavLink to="/parties">Parties</NavLink>
        <NavLink to="/catalog">Catalog</NavLink>
        <NavLink to="/p6">Long-tail</NavLink>
        <NavLink to="/order-to-cash">Order-to-cash</NavLink>
        {!signedIn
          ? <button data-testid="signin" onClick={() => void login()}>Sign in</button>
          : <span data-testid="signed-in">signed in</span>}
      </nav>
      <Routes>
        <Route path="/" element={<h1>OFBiz journeys</h1>} />
        <Route path="/login/oauth2/code/gateway" element={<h1>OFBiz journeys</h1>} />
        <Route path="/parties" element={<h1>parties (Task 4)</h1>} />
        <Route path="/catalog" element={<h1>catalog (Task 4)</h1>} />
        <Route path="/p6" element={<h1>long-tail (Task 4)</h1>} />
        <Route path="/order-to-cash" element={<h1>order-to-cash (Task 6)</h1>} />
      </Routes>
    </>
  );
}
