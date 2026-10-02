import { afterEach } from 'vitest';

const store = new Map<string, string>();

const localStorageStub: Storage = {
  get length() {
    return store.size;
  },
  key(index: number): string | null {
    return Array.from(store.keys())[index] ?? null;
  },
  getItem(key: string): string | null {
    return store.get(key) ?? null;
  },
  setItem(key: string, value: string): void {
    store.set(key, value);
  },
  removeItem(key: string): void {
    store.delete(key);
  },
  clear(): void {
    store.clear();
  },
};

// Direct assignment (not vi.stubGlobal) so test-level
// vi.unstubAllGlobals() calls cannot remove it.
globalThis.localStorage = localStorageStub;

afterEach(() => {
  store.clear();
});
