const apiBaseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;

type FetchOptions = RequestInit & { retry?: boolean };

const getToken = () => localStorage.getItem('adminToken');
const setToken = (token: string) => localStorage.setItem('adminToken', token);
const clearToken = () => localStorage.removeItem('adminToken');

const refreshAccessToken = async () => {
  if (!apiBaseUrl) {
    return false;
  }
  const response = await fetch(`${apiBaseUrl}/api/admin/refresh`, {
    method: 'POST',
    credentials: 'include'
  });
  if (!response.ok) {
    return false;
  }
  const data = await response.json();
  if (data?.token) {
    setToken(data.token);
    return true;
  }
  return false;
};

export const adminApiFetch = async (input: string, options: FetchOptions = {}) => {
  if (!apiBaseUrl) {
    throw new Error('API base URL is not configured');
  }

  const headers = new Headers(options.headers || {});
  const token = getToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${apiBaseUrl}${input}`, {
    ...options,
    headers,
    credentials: 'include'
  });

  if ((response.status === 401 || response.status === 403) && !options.retry) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      return adminApiFetch(input, { ...options, retry: true });
    }
    clearToken();
  }

  return response;
};
