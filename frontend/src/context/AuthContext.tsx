import React, { createContext, useContext, useState, useEffect } from 'react';
import { setAccessToken } from '../api/adminApi'; // 匯入 Token Setter

interface AuthContextType {
    isAuthenticated: boolean;
    isLoading: boolean;
    login: (token: string) => void;
    logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>(null!);

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    // isLoading 預設為 true，確保檢查完 Token 之前不渲染 App (避免畫面閃爍)
    const [isLoading, setIsLoading] = useState(true);
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;

    // 登入:接收 Token，存入記憶體變數，並更新狀態
    const login = (token: string) => {
        setAccessToken(token); // 注入到 API Client (adminApi.ts)
        setIsAuthenticated(true);
    };

    // 登出：清除記憶體，並呼叫後端移除 Cookie
    const logout = async () => {
        setAccessToken(null); // 清除 adminApi 的變數
        setIsAuthenticated(false);

        // 呼叫後端移除 HttpOnly Cookie
        if (apiBaseUrl) {
            try {
                await fetch(`${apiBaseUrl}/api/admin/logout`, {
                    method: 'POST',
                    credentials: 'include'
                });
            } catch (e) {
                console.error("Backend logout failed", e);
            }
        }
    };

    // App 啟動時的靜默換證 (Silent Refresh)
    useEffect(() => {
        const initAuth = async () => {
            if (!apiBaseUrl) {
                console.error("API Base URL not configured");
                setIsLoading(false);
                return;
            }

            try {
                // 直接使用 fetch 呼叫 /refresh
                // credentials: 'include' 確保瀏覽器自動帶上 HttpOnly Cookie
                const response = await fetch(`${apiBaseUrl}/api/admin/refresh`, {
                    method: 'POST',
                    credentials: 'include'
                });

                if (response.ok) {
                    const data = await response.json();
                    // 相容後端可能回傳 accessToken 或 token 欄位
                    const token = data.accessToken || data.token;
                    if (token) {
                        login(token); // 恢復登入狀態
                    } else {
                        throw new Error("No access token found");
                    }
                } else {
                    // 401/403 代表 Cookie 過期或無效
                    await logout();
                }
            } catch (error) {
                // 任何錯誤都視為未登入
                setAccessToken(null);
                setIsAuthenticated(false);
            } finally {
                setIsLoading(false); // 初始化結束
            }
        };

        initAuth();
    }, [apiBaseUrl]);

    return (
        <AuthContext.Provider value={{ isAuthenticated, isLoading, login, logout }}>
            {/* 只有當 isLoading 為 false 時才渲染子元件 */}
            {!isLoading && children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);