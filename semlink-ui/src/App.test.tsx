import { describe, it, expect } from 'vitest';

describe('App', () => {
  it('should export default', async () => {
    const mod = await import('./App');
    expect(mod.default).toBeDefined();
  });
});
