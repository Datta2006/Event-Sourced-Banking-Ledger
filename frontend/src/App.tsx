import { Routes, Route, Navigate } from 'react-router-dom';
import { LoginPage } from './LoginPage';
import { CustomerShell, BankShell } from './shells/Shells';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/customer/*" element={<CustomerShell />} />
      <Route path="/bank/*" element={<BankShell />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}
