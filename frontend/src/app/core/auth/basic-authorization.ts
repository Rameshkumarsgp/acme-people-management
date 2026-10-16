/** The value of an HTTP Basic Authorization header. The text is encoded as UTF-8 first, because btoa only accepts Latin-1. */
export function encodeBasicAuthorization(username: string, password: string): string {
  const utf8Bytes = new TextEncoder().encode(`${username}:${password}`);
  const binaryString = Array.from(utf8Bytes, (byte) => String.fromCharCode(byte)).join('');
  return `Basic ${btoa(binaryString)}`;
}
