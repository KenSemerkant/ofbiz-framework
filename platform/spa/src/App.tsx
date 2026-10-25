import { Routes, Route, NavLink, useSearchParams, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { login, handleCallback } from './auth';
import Parties from './screens/Parties';
import Catalog from './screens/Catalog';
import P6 from './screens/P6';
import OrderToCash from './screens/OrderToCash';
import Returns from './screens/Returns';

export default function App() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const code = params.get('code');
  const signedIn = !!localStorage.getItem('ofbiz.token');
  const handled = useRef(false);
  const [authError, setAuthError] = useState<string | null>(null);
  useEffect(() => {
    const error = params.get('error');
    if (error) {
      setAuthError(error);
      return;
    }
    if (!code || handled.current) return;
    handled.current = true;
    void handleCallback(code, params.get('state') ?? '').then(ok =>
      navigate(ok ? '/parties' : '/', { replace: true })
    );
  }, [code, navigate, params]);
  return (
    <>
      <nav>
        <NavLink to="/parties">Parties</NavLink>
        <NavLink to="/catalog">Catalog</NavLink>
        <NavLink to="/p6">Long-tail</NavLink>
        <NavLink to="/order-to-cash">Order-to-cash</NavLink>
        <NavLink to="/returns">Returns</NavLink>
        <NavLink to="/plan-to-ship">Plan-to-ship</NavLink>
        <NavLink to="/hire-to-retire">Hire-to-retire</NavLink>
        {!signedIn
          ? <button data-testid="signin" onClick={() => void login()}>Sign in</button>
          : <span data-testid="signed-in">signed in</span>}
      </nav>
      {authError && <p className="err" data-testid="auth-error">{authError}</p>}
      <Routes>
        <Route path="/" element={<h1>OFBiz journeys</h1>} />
        <Route path="/login/oauth2/code/gateway" element={<h1>OFBiz journeys</h1>} />
        <Route path="/parties" element={<Parties />} />
        <Route path="/catalog" element={<Catalog />} />
        <Route path="/p6" element={<P6 />} />
        <Route path="/order-to-cash" element={<OrderToCash />} />
        <Route path="/returns" element={<Returns />} />
        <Route path="/plan-to-ship" element={<h1>Plan-to-ship</h1>} />
        <Route path="/hire-to-retire" element={<h1>Hire-to-retire</h1>} />
      </Routes>
    </>
  );
}
