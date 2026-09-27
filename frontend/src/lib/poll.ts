import { useEffect, useRef, useState } from 'react';

/** Poll an async getter on an interval; exposes data + a tick counter for re-fetch triggers. */
export function usePoll<T>(fn: () => Promise<T>, intervalMs: number, deps: unknown[] = []) {
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<string | undefined>(undefined);
  const fnRef = useRef(fn);
  fnRef.current = fn;

  useEffect(() => {
    let alive = true;
    let timer: number | undefined;
    const run = async () => {
      try {
        const d = await fnRef.current();
        if (alive) {
          setData(d);
          setError(undefined);
        }
      } catch (e) {
        if (alive) setError(e instanceof Error ? e.message : String(e));
      } finally {
        if (alive) timer = window.setTimeout(run, intervalMs);
      }
    };
    void run();
    return () => {
      alive = false;
      if (timer) clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervalMs, ...deps]);

  return { data, error };
}

// ---------------------------------------------------------------------------
// FLIP reorder — WAAPI, the sanctioned tool (animate skill §3).
// ---------------------------------------------------------------------------

/**
 * True when the [data-flip-key] order inside `container` differs from
 * `prevOrder`. Cheap enough to run on every poll tick.
 */
export function orderChanged(
  container: HTMLElement | null,
  prevOrder: string[],
): boolean {
  if (!container) return false;
  const rows = container.querySelectorAll<HTMLElement>('[data-flip-key]');
  if (rows.length !== prevOrder.length) return true;
  for (let i = 0; i < rows.length; i++) {
    if (rows[i].dataset.flipKey !== prevOrder[i]) return true;
  }
  return false;
}

/**
 * FLIP: capture each row's rect BEFORE a reorder lands, then call this after
 * the DOM has committed to invert and play the movement. Rows slide from their
 * old position; new rows simply appear (no origin to animate from).
 *
 * Uses capture-compare rather than measuring inside a post-commit effect:
 * by the time the effect runs, React has already moved the nodes, so
 * before/after rects would be identical and nothing would play.
 */
export function flipReorder(
  container: HTMLElement | null,
  firstRects: Map<string, DOMRect>,
): void {
  if (!container) return;
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

  const rows = container.querySelectorAll<HTMLElement>('[data-flip-key]');
  rows.forEach((el) => {
    const key = el.dataset.flipKey ?? '';
    const before = firstRects.get(key);
    if (!before) return; // new row — handled by entrance animation
    const after = el.getBoundingClientRect();
    const dy = before.top - after.top;
    if (Math.abs(dy) < 1) return;
    el.animate(
      [{ transform: `translateY(${dy}px)` }, { transform: 'translateY(0)' }],
      {
        duration: 280,
        easing: 'cubic-bezier(0.23, 1, 0.32, 1)',
        composite: 'replace',
      },
    );
  });
}

/** Capture rects of all keyed rows inside `container`, keyed by data-flip-key. */
export function captureRects(container: HTMLElement | null): Map<string, DOMRect> {
  const first = new Map<string, DOMRect>();
  if (!container) return first;
  container
    .querySelectorAll<HTMLElement>('[data-flip-key]')
    .forEach((el) => first.set(el.dataset.flipKey ?? '', el.getBoundingClientRect()));
  return first;
}

/** Tint flash for value changes (credit/debit pulse on balance cells). */
export function flashTint(el: HTMLElement | null, direction: 'up' | 'down'): void {
  if (!el) return;
  const color = direction === 'up' ? 'var(--credit)' : 'var(--debit)';
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    // keep comprehension, drop motion: settle the color with a transition
    el.style.transition = 'color 0.6s ease';
    el.style.color = color;
    setTimeout(() => {
      el.style.color = '';
    }, 1200);
    return;
  }
  el.animate(
    [
      { backgroundColor: 'transparent' },
      { backgroundColor: direction === 'up' ? 'var(--credit-bg)' : 'var(--debit-bg)', offset: 0.25 },
      { backgroundColor: 'transparent' },
    ],
    { duration: 900, easing: 'ease-out' },
  );
  el.style.color = color;
  setTimeout(() => {
    el.style.color = '';
  }, 1400);
}
