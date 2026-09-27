import { useEffect, useLayoutEffect, useRef } from 'react';
import {
  motion,
  useMotionValue,
  useReducedMotion,
  useSpring,
  useTransform,
} from 'motion/react';
import { moneyINR } from '../lib/format';
import { flashTint } from '../lib/poll';

/**
 * Money counter: deliberate settle, barely-there overshoot (Apple-style
 * duration spring, animate skill §5).
 */
const MONEY_SPRING = { duration: 0.45, bounce: 0.1 };

/** Saga step progress: the skill's Apple-style spring, straight from the table. */
const STEP_SPRING = { duration: 0.5, bounce: 0.2 };

/**
 * Drive a spring toward `value` on every change. Retargets mid-flight and
 * carries velocity. Under prefers-reduced-motion the value jumps instead —
 * reduced motion is gentler motion, not frozen UI.
 */
function useSpringNumber(value: number, config: typeof MONEY_SPRING) {
  const reduce = useReducedMotion() ?? false;
  const raw = useMotionValue(value);
  const spring = useSpring(raw, config);
  useLayoutEffect(() => {
    if (reduce) {
      spring.jump(value);
      raw.jump(value);
    } else {
      raw.set(value);
    }
  }, [value, reduce, raw, spring]);
  return spring;
}

/**
 * Spring-animated money figure. Purpose: state indication — a balance is a
 * claim about the world; it should settle, not teleport.
 * The MotionValue renders as children, so no React re-render happens per frame.
 */
export function SpringMoney({
  value,
  className,
  prefix = '',
}: {
  value: number;
  className?: string;
  prefix?: string;
}) {
  const spring = useSpringNumber(value, MONEY_SPRING);
  const text = useTransform(spring, (v) => `${prefix}${moneyINR(v)}`);
  return <motion.span className={className}>{text}</motion.span>;
}

/**
 * Spring money with change-direction tint flash. The flash (color) stays under
 * reduced motion; only the spring motion collapses.
 */
export function TintedSpringMoney({ value }: { value: number }) {
  const spring = useSpringNumber(value, MONEY_SPRING);
  const text = useTransform(spring, (v) => moneyINR(v));
  const ref = useRef<HTMLSpanElement>(null);
  const prev = useRef(value);
  useEffect(() => {
    if (prev.current !== value) {
      flashTint(ref.current, value > prev.current ? 'up' : 'down');
      prev.current = value;
    }
  }, [value]);
  return (
    <motion.span
      ref={ref}
      className="mono balance-lg"
      style={{ display: 'inline-block' }}
    >
      {text}
    </motion.span>
  );
}

/**
 * Progress bar that springs to `value` (0..1). Transform-only: the track is
 * full-width and the fill animates scaleX, never width.
 */
export function SpringProgress({
  value,
  failed = false,
}: {
  value: number;
  failed?: boolean;
}) {
  const reduce = useReducedMotion() ?? false;
  const raw = useMotionValue(value);
  const progress = useSpring(raw, STEP_SPRING);
  useLayoutEffect(() => {
    if (reduce) {
      progress.jump(value);
      raw.jump(value);
    } else {
      raw.set(value);
    }
  }, [value, reduce, raw, progress]);
  return (
    <motion.div
      className="saga-fill"
      style={{
        scaleX: progress,
        background: failed ? 'var(--debit)' : 'var(--accent)',
      }}
    />
  );
}
