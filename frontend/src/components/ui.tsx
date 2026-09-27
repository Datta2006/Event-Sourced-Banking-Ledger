import type { ReactNode } from 'react';

/** Map a contract status enum value to a semantic tone. */
function toneFor(status: string): string {
  if (['UP', 'COMPLETED', 'APPROVED', 'DISBURSED', 'CAPTURED'].includes(status)) return 'green';
  if (['FAILED', 'REJECTED', 'DOWN', 'EXPIRED'].includes(status)) return 'red';
  if (['WAITING_FOR_FUNDS', 'STARTING', 'RELEASED', 'COMPENSATED', 'OPEN'].includes(status)) return 'amber';
  if (['INITIATED', 'SOURCE_DEBITED', 'TARGET_CREDITED', 'QUEUED', 'RESERVED', 'REVIEWED'].includes(status)) return 'blue';
  return '';
}

export function StatusBadge({ status }: { status: string }) {
  const tone = toneFor(status);
  const label = status.replaceAll('_', ' ').toLowerCase();
  return (
    <span className={`badge ${tone}`}>
      <span className="dot" aria-hidden />
      {label}
    </span>
  );
}

export function Section({
  title,
  note,
  right,
  children,
}: {
  title: string;
  note?: string;
  right?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="section">
      <div className="section-head">
        <div>
          <h2 className="section-title">{title}</h2>
          {note && <div className="section-note">{note}</div>}
        </div>
        {right}
      </div>
      {children}
    </section>
  );
}

export function ErrorNote({ error }: { error?: string }) {
  if (!error) return null;
  return <p className="error-text mono">⚠ {error}</p>;
}
