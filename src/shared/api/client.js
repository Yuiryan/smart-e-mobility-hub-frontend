export class ApiError extends Error {
  status;
  constructor(status, message, error = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = error.code;
    this.details = error.details;
    this.requestId = error.requestId;
  }
}
export async function request(path, options = {}) {
  const { withStatus = false, ...fetchOptions } = options;
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body && !(options.body instanceof FormData))
    headers.set('Content-Type', 'application/json');
  const timeout = AbortSignal.timeout(15000);
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL || '/api'}${path}`,
    {
      ...fetchOptions,
      headers,
      credentials: 'include',
      signal: options.signal
        ? AbortSignal.any([options.signal, timeout])
        : timeout,
    },
  );
  const text = response.status === 204 ? '' : await response.text();
  let data;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    throw new ApiError(
      response.status,
      `HTTP ${response.status}: invalid JSON response`,
    );
  }
  if (!response.ok)
    throw new ApiError(
      response.status,
      data?.error?.message || `HTTP ${response.status}`,
      data?.error,
    );
  return withStatus ? { status: response.status, data } : data;
}
