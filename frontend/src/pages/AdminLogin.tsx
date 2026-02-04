import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext'; // 引入 Context Hook
import styles from '../styles/AdminLogin.module.css';

// 根據後端 AdminLoginResponse 定義
type LoginResponse = {
  accessToken?: string;
  token?: string;
  expiresInSeconds: number;
};

const AdminLogin = () => {
  const navigate = useNavigate();
  const { login, isAuthenticated } = useAuth(); // 使用 Context
  const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL;
  const apiBaseUrl = (rawApiBaseUrl ?? '').trim();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  // 如果已經是登入狀態 (Context 為 true)，直接轉址
  useEffect(() => {
    document.title = '管理員登入';
    // const existingToken = localStorage.getItem('adminToken');
    if (isAuthenticated) {
      navigate('/admin/tires');
    }
  }, [isAuthenticated, navigate]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (rawApiBaseUrl === undefined) {
      setErrorMessage('API Base URL 未設定，請先設定 .env。');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/admin/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
        credentials: 'include' // 讓後端寫入 HttpOnly Refresh Cookie
      });

      const result = (await response.json()) as LoginResponse & { message?: string };

      if (!response.ok) {
        setErrorMessage(result.message || '登入失敗，請檢查帳號與密碼。');
        setIsSubmitting(false);
        return;
      }

      // localStorage.setItem('adminToken', result.token);
      // 不再使用 localStorage.setItem
      // 改為呼叫 Context 的 login，把 Token 存入記憶體
      const accessToken = result.accessToken || result.token;
      if (!accessToken) {
        setErrorMessage('登入失敗：缺少 access token。');
        setIsSubmitting(false);
        return;
      }
      login(accessToken);

    } catch (error) {
      setErrorMessage('登入失敗，請稍後再試。');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.loginBox}>
        <h1 className={styles.title}>管理員登入</h1>
        <form onSubmit={handleSubmit}>
          <div className={styles.formGroup}>
            <label htmlFor="username" className={styles.label}>帳號</label>
            <input
              type="text"
              id="username"
              name="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              className={styles.input}
              required
              disabled={isSubmitting}
              autoComplete="username"
            />
          </div>
          <div className={styles.formGroup}>
            <label htmlFor="password" className={styles.label}>密碼</label>
            <input
              type="password"
              id="password"
              name="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className={styles.input}
              required
              disabled={isSubmitting}
              autoComplete="current-password"
            />
          </div>
          {errorMessage && <p className={styles.errorMessage}>{errorMessage}</p>}
          <button type="submit" className={styles.submitButton} disabled={isSubmitting}>
            {isSubmitting ? '登入中...' : '登入'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default AdminLogin;
