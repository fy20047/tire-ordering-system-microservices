const apiBaseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined; // 讀 API Base，沒有設定就直接 throw，避免靜默錯誤

type FetchOptions = RequestInit & { retry?: boolean };

const getToken = () => localStorage.getItem('adminToken'); // 從 localStorage 拿 adminToken
const setToken = (token: string) => localStorage.setItem('adminToken', token);
const clearToken = () => localStorage.removeItem('adminToken');

const refreshAccessToken = async (): Promise<boolean> => {
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
  const data = (await response.json()) as { token?: string };
  if (data.token) {
    setToken(data.token);
    return true;
  }
  return false;
};

// 統一管理後台 API 的呼叫（自動帶 access token、帶 cookie、401/403 自動刷新並重試一次）
export const adminApiFetch = async (
  input: string,
  options: FetchOptions = {}
): Promise<Response> => {
  if (!apiBaseUrl) {
    throw new Error('API base URL is not configured');
  }

  const headers = new Headers(options.headers || {});
  const token = getToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`); // 若有 token 就加到 Authorization: Bearer ...
  }

  const response = await fetch(`${apiBaseUrl}${input}`, {
    ...options,
    headers,
    credentials: 'include' // 讓瀏覽器送出 refresh cookie
  });

  if ((response.status === 401 || response.status === 403) && !options.retry) { // access token 過期
    const refreshed = await refreshAccessToken(); // 換一個新 access token
    if (refreshed) {
      return adminApiFetch(input, { ...options, retry: true }); // 重試原本請求
    }
    clearToken(); // 失敗就清掉 localStorage token
  }

  return response;
};
