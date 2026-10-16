import { encodeBasicAuthorization } from './basic-authorization';

describe('encodeBasicAuthorization', () => {
  it('encodes a username and password as an HTTP Basic header value', () => {
    expect(encodeBasicAuthorization('hr', 's3cret')).toBe('Basic aHI6czNjcmV0');
  });

  it('encodes non-ASCII characters as UTF-8, which btoa alone cannot do', () => {
    expect(encodeBasicAuthorization('hr', 'pässword')).toBe('Basic aHI6cMOkc3N3b3Jk');
  });

  it('keeps colons inside the password', () => {
    expect(encodeBasicAuthorization('hr', 'pa:ss')).toBe('Basic aHI6cGE6c3M=');
  });
});
