import { useState } from 'react';
import { api } from '../api';

// P9-F1: monolith accounting app parity slice — AR find, payments, chart of
// accounts, journal, and the three statements (trial balance / balance sheet /
// income statement), all read via the gateway like every other screen.
type Invoice = { invoiceId: string; customerId: string; status: string; total: string; currencyUomId: string };
type Payment = { paymentId: string; invoiceId: string; status: string; amount: string; currencyUomId: string };
type Account = { glAccountId: string; accountCode: string; accountName: string; postBalance: string; glAccountClassId: string };
type Entry = { glAccountId: string; amount: string; debitCredit: string };
type Transaction = { acctgTransId: string; transType: string; description: string; periodId: string; posted: boolean; entries: Entry[] };
type TbRow = { glAccountId: string; accountCode: string; accountName: string; debitTotal: string; creditTotal: string; balance: string };
type TrialBalance = { rows: TbRow[]; totalDebits: string; totalCredits: string };
type StmtRow = { glAccountId: string; accountCode: string; accountName: string; amount: string };
type BalanceSheet = { assets: StmtRow[]; liabilities: StmtRow[]; equity: StmtRow[]; totalAssets: string; totalLiabilitiesAndEquity: string };
type IncomeStatement = { income: StmtRow[]; expenses: StmtRow[]; netIncome: string };

export default function Accounting() {
  const [tab, setTab] = useState('invoices');
  const [msg, setMsg] = useState('');
  const [customerId, setCustomerId] = useState('');
  const [status, setStatus] = useState('');
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [tb, setTb] = useState<TrialBalance | null>(null);
  const [bs, setBs] = useState<BalanceSheet | null>(null);
  const [is, setIs] = useState<IncomeStatement | null>(null);

  const run = async (fn: () => Promise<void>) => {
    try {
      setMsg('');
      await fn();
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  const searchInvoices = () => run(async () => {
    const q = new URLSearchParams();
    if (customerId) q.set('customerId', customerId);
    if (status) q.set('status', status);
    setInvoices(await api<Invoice[]>(`/api/v1/invoices?${q}`));
  });
  const loadPayments = () => run(async () => setPayments(await api<Payment[]>('/api/v1/payments')));
  const loadAccounts = () => run(async () => setAccounts(await api<Account[]>('/api/v1/gl/accounts')));
  const loadTransactions = () => run(async () => setTransactions(await api<Transaction[]>('/api/v1/gl/transactions')));
  const loadTrialBalance = () => run(async () => setTb(await api<TrialBalance>('/api/v1/gl/trial-balance')));
  const loadBalanceSheet = () => run(async () => setBs(await api<BalanceSheet>('/api/v1/gl/balance-sheet')));
  const loadIncomeStatement = () => run(async () => setIs(await api<IncomeStatement>('/api/v1/gl/income-statement')));

  const tabs = [
    ['invoices', 'Invoices'], ['payments', 'Payments'], ['accounts', 'GL accounts'],
    ['journal', 'Journal'], ['statements', 'Statements'],
  ] as const;

  return (
    <section aria-labelledby="accounting-h">
      <h2 id="accounting-h">Accounting</h2>
      <p className="err" data-testid="accounting-msg">{msg}</p>
      <nav className="tabs" aria-label="Accounting sections">
        {tabs.map(([id, label]) =>
          <button key={id} data-testid={`tab-${id}`} aria-pressed={tab === id} onClick={() => setTab(id)}>{label}</button>)}
      </nav>

      {tab === 'invoices' && <>
        <form onSubmit={e => { e.preventDefault(); searchInvoices(); }}>
          <label>Customer <input value={customerId} onChange={e => setCustomerId(e.target.value)} /></label>
          <label>Status <input value={status} onChange={e => setStatus(e.target.value)} placeholder="e.g. SETTLED" /></label>
          <button>Search</button>
        </form>
        <table data-testid="invoice-table">
          <thead><tr><th>Invoice</th><th>Customer</th><th>Status</th><th>Total</th></tr></thead>
          <tbody>
            {invoices.map(i => <tr key={i.invoiceId}><td>{i.invoiceId}</td><td>{i.customerId}</td><td>{i.status}</td><td>{i.total} {i.currencyUomId}</td></tr>)}
          </tbody>
        </table>
      </>}

      {tab === 'payments' && <>
        <button onClick={loadPayments}>Load payments</button>
        <table data-testid="payment-table">
          <thead><tr><th>Payment</th><th>Invoice</th><th>Status</th><th>Amount</th></tr></thead>
          <tbody>
            {payments.map(p => <tr key={p.paymentId}><td>{p.paymentId}</td><td>{p.invoiceId}</td><td>{p.status}</td><td>{p.amount} {p.currencyUomId}</td></tr>)}
          </tbody>
        </table>
      </>}

      {tab === 'accounts' && <>
        <button onClick={loadAccounts}>Load chart of accounts</button>
        <table data-testid="gl-account-table">
          <thead><tr><th>Code</th><th>Name</th><th>Class</th><th>Post balance</th></tr></thead>
          <tbody>
            {accounts.map(a => <tr key={a.glAccountId}><td>{a.accountCode}</td><td>{a.accountName}</td><td>{a.glAccountClassId}</td><td>{a.postBalance}</td></tr>)}
          </tbody>
        </table>
      </>}

      {tab === 'journal' && <>
        <button onClick={loadTransactions}>Load journal</button>
        <table data-testid="journal-table">
          <thead><tr><th>Transaction</th><th>Type</th><th>Description</th><th>Period</th><th>Posted</th></tr></thead>
          <tbody>
            {transactions.map(t => <tr key={t.acctgTransId}><td>{t.acctgTransId}</td><td>{t.transType}</td><td>{t.description}</td><td>{t.periodId}</td><td>{t.posted ? 'yes' : 'no'}</td></tr>)}
          </tbody>
        </table>
      </>}

      {tab === 'statements' && <>
        <button onClick={loadTrialBalance}>Trial balance</button>
        <button onClick={loadBalanceSheet}>Balance sheet</button>
        <button onClick={loadIncomeStatement}>Income statement</button>
        {tb && <div data-testid="trial-balance">
          <h3>Trial balance</h3>
          <table>
            <thead><tr><th>Code</th><th>Account</th><th>Debits</th><th>Credits</th></tr></thead>
            <tbody>
              {tb.rows.map(r => <tr key={r.glAccountId}><td>{r.accountCode}</td><td>{r.accountName}</td><td>{r.debitTotal}</td><td>{r.creditTotal}</td></tr>)}
            </tbody>
          </table>
          <p data-testid="tb-totals">{tb.totalDebits} / {tb.totalCredits}</p>
        </div>}
        {bs && <div data-testid="balance-sheet">
          <h3>Balance sheet</h3>
          <ul>
            {bs.assets.map(r => <li key={r.glAccountId}>ASSET {r.accountCode} {r.accountName} {r.amount}</li>)}
            {bs.liabilities.map(r => <li key={r.glAccountId}>LIABILITY {r.accountCode} {r.accountName} {r.amount}</li>)}
            {bs.equity.map(r => <li key={r.glAccountId}>EQUITY {r.accountCode} {r.accountName} {r.amount}</li>)}
          </ul>
          <p data-testid="bs-totals">{bs.totalAssets} vs {bs.totalLiabilitiesAndEquity} + net income</p>
        </div>}
        {is && <div data-testid="income-statement">
          <h3>Income statement</h3>
          <ul>
            {is.income.map(r => <li key={r.glAccountId}>INCOME {r.accountCode} {r.accountName} {r.amount}</li>)}
            {is.expenses.map(r => <li key={r.glAccountId}>EXPENSE {r.accountCode} {r.accountName} {r.amount}</li>)}
          </ul>
          <p data-testid="net-income">Net income {is.netIncome}</p>
        </div>}
      </>}
    </section>
  );
}
