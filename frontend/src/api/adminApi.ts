const apiBaseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined; // 讀 API Base，沒有設定就直接 throw，避免靜默錯誤

type FetchOptions = RequestInit & { retry?: boolean };

// 改用記憶體變數取代 localStorage
// 這個變數只會存在於當前頁面的記憶體中，F5 刷新後會變回 null (這是預期的安全行為)
let accessToken: string | null = null;

// 匯出 Setter，讓外部 (AuthContext) 可以主動設定 Token
// 例如：登入成功時、或 App 初始化呼叫 /refresh 成功時，會呼叫此函式
export const setAccessToken = (token: string | null) => {
  accessToken = token;
};

// 內部 Helper：取得 Token (給下方的 fetch 使用)
const getAccessToken = () => accessToken;

const refreshAccessToken = async (): Promise<boolean> => {
  if (!apiBaseUrl) {
    return false;
  }
  try {
    const response = await fetch(`${apiBaseUrl}/api/admin/refresh`, {
      method: 'POST',
      credentials: 'include' // 確保帶上 HttpOnly Cookie (refresh token)
    });

    if (!response.ok) {
      return false;
    }

    // 注意：根據您的 Java AdminLoginResponse，欄位名稱可能是 accessToken
    // 這裡加一點相容性檢查，若您的後端回傳 { accessToken: "..." } 也能運作
    const data = (await response.json()) as { token?: string; accessToken?: string };
    const newToken = data.token || data.accessToken;

    if (newToken) {
      setAccessToken(newToken); // 更新記憶體變數
      return true;
    }
  } catch (err) {
    console.error('Refresh logic failed', err);
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

  // 從記憶體變數讀取 Token
  const token = getAccessToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${apiBaseUrl}${input}`, {
    ...options,
    headers,
    credentials: 'include'
  });

  if ((response.status === 401 || response.status === 403) && !options.retry) { // access token 過期
    const refreshed = await refreshAccessToken(); // 換一個新 access token
    if (refreshed) {
      // 重試時，遞迴呼叫會自動再次執行 getAccessToken()，此時已經是新的變數了
      return adminApiFetch(input, { ...options, retry: true });
    }
    // 失敗時只需將變數清空 (不需操作 localStorage)
    setAccessToken(null);
  }
  return response;
};
