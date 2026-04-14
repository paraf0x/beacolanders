/**
 * Test protocol logger.
 * Captures every check with expected/actual values for QA review.
 */

interface ProtocolEntry {
  tc: string;
  step: string;
  expected: string;
  actual: string;
  pass: boolean;
}

const entries: ProtocolEntry[] = [];
let currentTc = '';

export function tc(id: string): void {
  currentTc = id;
}

export function check(step: string, expected: unknown, actual: unknown): void {
  const pass = deepEqual(expected, actual);
  entries.push({
    tc: currentTc,
    step,
    expected: fmt(expected),
    actual: fmt(actual),
    pass,
  });
  if (!pass) {
    throw new Error(`[${currentTc}] ${step}\n  Expected: ${fmt(expected)}\n  Actual:   ${fmt(actual)}`);
  }
}

export function checkTruthy(step: string, actual: unknown): void {
  const pass = !!actual;
  entries.push({
    tc: currentTc,
    step,
    expected: 'truthy',
    actual: fmt(actual),
    pass,
  });
  if (!pass) {
    throw new Error(`[${currentTc}] ${step}\n  Expected: truthy\n  Actual:   ${fmt(actual)}`);
  }
}

export function checkDefined(step: string, actual: unknown): void {
  const pass = actual !== undefined && actual !== null;
  entries.push({
    tc: currentTc,
    step,
    expected: 'defined (not null/undefined)',
    actual: actual === undefined ? 'undefined' : actual === null ? 'null' : fmt(actual),
    pass,
  });
  if (!pass) {
    throw new Error(`[${currentTc}] ${step}\n  Expected: defined\n  Actual:   ${fmt(actual)}`);
  }
}

export function checkGte(step: string, actual: number, min: number): void {
  const pass = actual >= min;
  entries.push({
    tc: currentTc,
    step,
    expected: `>= ${min}`,
    actual: String(actual),
    pass,
  });
  if (!pass) {
    throw new Error(`[${currentTc}] ${step}\n  Expected: >= ${min}\n  Actual:   ${actual}`);
  }
}

export function checkContains(step: string, haystack: string, needle: string): void {
  const pass = haystack.includes(needle);
  entries.push({
    tc: currentTc,
    step,
    expected: `contains "${needle}"`,
    actual: `"${haystack.slice(0, 120)}${haystack.length > 120 ? '...' : ''}"`,
    pass,
  });
  if (!pass) {
    throw new Error(`[${currentTc}] ${step}\n  Expected to contain: "${needle}"\n  Actual: "${haystack}"`);
  }
}

export function checkGt(step: string, actual: number, min: number): void {
  const pass = actual > min;
  entries.push({
    tc: currentTc,
    step,
    expected: `> ${min}`,
    actual: String(actual),
    pass,
  });
  if (!pass) {
    throw new Error(`[${currentTc}] ${step}\n  Expected: > ${min}\n  Actual:   ${actual}`);
  }
}

export function checkHasProperty(step: string, obj: unknown, prop: string): void {
  const pass = typeof obj === 'object' && obj !== null && prop in obj;
  entries.push({
    tc: currentTc,
    step,
    expected: `has property "${prop}"`,
    actual: typeof obj === 'object' && obj !== null ? `keys: [${Object.keys(obj).join(', ')}]` : fmt(obj),
    pass,
  });
  if (!pass) {
    throw new Error(
      `[${currentTc}] ${step}\n  Expected property: "${prop}"\n  Actual keys: ${
        typeof obj === 'object' && obj !== null ? Object.keys(obj).join(', ') : fmt(obj)
      }`,
    );
  }
}

export function getProtocol(): ProtocolEntry[] {
  return entries;
}

export function printProtocol(): string {
  const lines: string[] = [];
  let lastTc = '';

  for (const e of entries) {
    if (e.tc !== lastTc) {
      lines.push('');
      lines.push(`── ${e.tc} ${'─'.repeat(Math.max(0, 70 - e.tc.length))}`);
      lastTc = e.tc;
    }
    const icon = e.pass ? '✓' : '✗';
    lines.push(`  ${icon} ${e.step}`);
    lines.push(`    expected: ${e.expected}`);
    lines.push(`    actual:   ${e.actual}`);
  }

  const passed = entries.filter((e) => e.pass).length;
  const failed = entries.filter((e) => !e.pass).length;
  lines.push('');
  lines.push(`═══════════════════════════════════════════════════════════════════════`);
  lines.push(`  ${passed} checks passed, ${failed} failed, ${entries.length} total`);
  lines.push(`═══════════════════════════════════════════════════════════════════════`);

  return lines.join('\n');
}

function fmt(v: unknown): string {
  if (v === undefined) return 'undefined';
  if (v === null) return 'null';
  if (typeof v === 'string') return `"${v.slice(0, 120)}${v.length > 120 ? '...' : ''}"`;
  if (typeof v === 'number' || typeof v === 'boolean') return String(v);
  if (Array.isArray(v)) return `[${v.length} items]`;
  if (typeof v === 'object') return JSON.stringify(v).slice(0, 120);
  return String(v);
}

function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (typeof a !== typeof b) return false;
  if (typeof a === 'object' && a !== null && b !== null) {
    return JSON.stringify(a) === JSON.stringify(b);
  }
  return false;
}
