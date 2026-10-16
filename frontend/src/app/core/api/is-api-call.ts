/** The one definition of which requests belong to the API, shared by every interceptor. */
export function isApiCall(url: string, apiBaseUrl: string): boolean {
  return url === apiBaseUrl || url.startsWith(`${apiBaseUrl}/`);
}
