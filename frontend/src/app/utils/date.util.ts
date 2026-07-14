export function startOfDay(date: Date): Date {
  const next = new Date(date);
  next.setHours(0, 0, 0, 0);
  return next;
}

export function parseIsoDate(value: string | null): Date | null {
  if (!value) return null;
  const parts = value.split('-').map(Number);
  if (parts.length !== 3 || parts.some(Number.isNaN)) return null;
  return new Date(parts[0], parts[1] - 1, parts[2]);
}

export function toIsoDate(date: Date): string {
  const d = startOfDay(date);
  const month = `${d.getMonth() + 1}`.padStart(2, '0');
  const day   = `${d.getDate()}`.padStart(2, '0');
  return `${d.getFullYear()}-${month}-${day}`;
}
