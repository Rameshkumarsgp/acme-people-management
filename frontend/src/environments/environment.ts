/*
 * These values are fixed at build time. In an ideal setup they would be fetched at startup instead, so
 * one build can run in every environment: a config file served next to the app and written from the
 * container's environment variables, or a central configuration service. See docs/tradeoffs.md.
 */
export const environment = {
  /** Where the API lives. A relative path works behind nginx and behind the dev server proxy. */
  apiBaseUrl: '/api',
  /** How long the browser waits for an API answer before giving up. */
  apiTimeoutInMs: 15_000,
};
