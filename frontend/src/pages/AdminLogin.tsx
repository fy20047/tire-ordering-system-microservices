import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from '../styles/AdminLogin.module.css';

type LoginResponse = {
  token: string;
  expiresInSeconds: number;
};

const AdminLogin = () => {
  const navigate = useNavigate();
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    document.title = '管理員登入';
    const existingToken = localStorage.getItem('adminToken');
    if (existingToken) {
      navigate('/admin/tires');
    }
  }, [navigate]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!apiBaseUrl) {
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
        credentials: 'include' // 這樣 refresh cookie 才能寫進瀏覽器
      });

      const result = (await response.json()) as LoginResponse & { message?: string };

      if (!response.ok) {
        setErrorMessage(result.message || '登入失敗，請檢查帳號與密碼。');
        setIsSubmitting(false);
        return;
      }

      localStorage.setItem('adminToken', result.token);
      navigate('/admin/tires');
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
