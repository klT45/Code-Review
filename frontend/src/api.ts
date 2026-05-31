const cloudApiBaseUrl = 'http://101.35.244.21/ai-pr-review-api';
const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim().replace(/\/$/, '');
const defaultApiBaseUrl = import.meta.env.PROD ? cloudApiBaseUrl : '';
const apiBaseUrl = configuredApiBaseUrl ?? defaultApiBaseUrl;

export function apiUrl(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${apiBaseUrl}${normalizedPath}`;
}
