export function computeNameHash(name: string | undefined): number {
  if (!name) return 0;
  return name.split('').reduce((a: number, b: string) => {
    a = (a << 5) - a + b.charCodeAt(0);
    return a & a;
  }, 0);
}
