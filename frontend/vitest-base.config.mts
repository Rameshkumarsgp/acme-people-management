import { defineConfig } from 'vitest/config';

// Vitest's on-disk module cache is keyed on the spec file alone, so a change in the code under test is
// not seen while the spec is unchanged, and a stale green is possible. Turn it off.
export default defineConfig({
  test: {
    fsModuleCache: false,
  },
});
