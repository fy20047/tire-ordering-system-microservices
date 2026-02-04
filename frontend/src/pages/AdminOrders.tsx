import { ChangeEvent, FormEvent, useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { adminApiFetch } from '../api/adminApi';
import { useAuth } from '../context/AuthContext';
import styles from '../styles/AdminOrders.module.css';

type OrderStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';
type InstallationOption = 'INSTALL' | 'PICKUP' | 'DELIVERY';

type AdminOrder = {
  id: number;
  status: OrderStatus;
  quantity: number;
  customerName: string;
  phone: string;
  email?: string | null;
  installationOption: InstallationOption;
  deliveryAddress?: string | null;
  carModel?: string | null;
  notes?: string | null;
  createdAt?: string;
  updatedAt?: string;
  tireId: number;
  tireBrand: string;
  tireSeries: string;
  tireOrigin?: string | null;
  tireSize: string;
  tirePrice?: number | null;
};

type Filters = {
  status: 'all' | OrderStatus;
  keyword: string;
};

const defaultFilters: Filters = {
  status: 'all',
  keyword: ''
};

const statusLabel: Record<OrderStatus, string> = {
  PENDING: '待處理',
  CONFIRMED: '已確認',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
};

const installationLabel: Record<InstallationOption, string> = {
  INSTALL: '到店安裝',
  PICKUP: '到店取貨',
  DELIVERY: '住家配送'
};

const AdminOrders = () => {
  const navigate = useNavigate();
  const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL;
  const { isAuthenticated, isLoading, logout } = useAuth();

  const [orders, setOrders] = useState<AdminOrder[]>([]);
  const [filters, setFilters] = useState<Filters>(defaultFilters);
  const [statusDrafts, setStatusDrafts] = useState<Record<number, OrderStatus>>({});
  const [loading, setLoading] = useState(false);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  useEffect(() => {
    document.title = '訂單管理';
    if (isLoading) {
      return;
    }
    if (!isAuthenticated) {
      navigate('/admin/login');
      return;
    }
    void fetchOrders();
  }, [isAuthenticated, isLoading, navigate]);

  const fetchOrders = async (nextFilters?: Filters) => {
    if (rawApiBaseUrl === undefined) {
      return;
    }

    setLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    const appliedFilters = nextFilters ?? filters;
    const params = new URLSearchParams();
    if (appliedFilters.status !== 'all') {
      params.set('status', appliedFilters.status);
    }

    const query = params.toString();

    try {
      const response = await adminApiFetch(`/api/admin/orders${query ? `?${query}` : ''}`);

      if (response.status === 401 || response.status === 403) {
        await logout();
        navigate('/admin/login');
        return;
      }

      if (!response.ok) {
        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
          const data = await response.json();
          setErrorMessage(data.message || `載入訂單失敗（${response.status}）。`);
        } else {
          setErrorMessage(`載入訂單失敗（${response.status}）。`);
        }
        return;
      }

      const data = await response.json();
      const items = (data.items ?? []) as AdminOrder[];
      setOrders(items);

      const draftMap: Record<number, OrderStatus> = {};
      items.forEach((order) => {
        draftMap[order.id] = order.status;
      });
      setStatusDrafts(draftMap);
    } catch (error) {
      setErrorMessage('載入訂單失敗，請稍後再試。');
    } finally {
      setLoading(false);
    }
  };

  const handleFilterChange = (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = event.target;
    setFilters((prev) => ({ ...prev, [name]: value }));
  };

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void fetchOrders(filters);
  };

  const handleResetFilters = () => {
    const clearedFilters = defaultFilters;
    setFilters(clearedFilters);
    void fetchOrders(clearedFilters);
  };

  const handleStatusDraftChange = (orderId: number, status: OrderStatus) => {
    setStatusDrafts((prev) => ({ ...prev, [orderId]: status }));
  };

  const handleUpdateStatus = async (orderId: number) => {
    if (rawApiBaseUrl === undefined) {
      return;
    }

    const status = statusDrafts[orderId];
    if (!status) {
      return;
    }

    setSavingId(orderId);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const response = await adminApiFetch(`/api/admin/orders/${orderId}/status`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ status })
      });

      if (response.status === 401 || response.status === 403) {
        await logout();
        navigate('/admin/login');
        return;
      }

      if (!response.ok) {
        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
          const data = await response.json();
          setErrorMessage(data.message || '更新狀態失敗。');
        } else {
          setErrorMessage(`更新狀態失敗（${response.status}）。`);
        }
        return;
      }

      setSuccessMessage('訂單狀態已更新。');
      await fetchOrders(filters);
    } catch (error) {
      setErrorMessage('更新狀態失敗，請稍後再試。');
    } finally {
      setSavingId(null);
    }
  };

  const filteredOrders = useMemo(() => {
    const keyword = filters.keyword.trim().toLowerCase();
    if (!keyword) {
      return orders;
    }

    return orders.filter((order) => {
      const composite = [
        String(order.id),
        order.customerName,
        order.phone,
        order.email ?? '',
        order.carModel ?? '',
        order.tireBrand,
        order.tireSeries,
        order.tireSize
      ]
        .join(' ')
        .toLowerCase();
      return composite.includes(keyword);
    });
  }, [filters.keyword, orders]);

  const formatPrice = (price?: number | null) => (price == null ? '—' : price.toLocaleString());
  const formatDate = (value?: string) => {
    if (!value) return '—';
    const normalized = value.replace('T', ' ');
    const withoutMs = normalized.split('.')[0];
    return withoutMs.replace('Z', '');
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>訂單管理</h1>
        <button type="button" className={styles.secondaryButton} onClick={() => navigate('/admin/tires')}>
          回輪胎管理
        </button>
      </div>

      <section className={styles.card}>
        <h2 className={styles.sectionTitle}>搜尋/篩選</h2>
        <form className={styles.filterForm} onSubmit={handleSearch}>
          <div className={styles.formRow}>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="status">狀態</label>
              <select
                id="status"
                name="status"
                value={filters.status}
                onChange={handleFilterChange}
                className={styles.input}
              >
                <option value="all">全部</option>
                <option value="PENDING">待處理</option>
                <option value="CONFIRMED">已確認</option>
                <option value="COMPLETED">已完成</option>
                <option value="CANCELLED">已取消</option>
              </select>
            </div>
            <div className={styles.formGroupWide}>
              <label className={styles.label} htmlFor="keyword">關鍵字</label>
              <input
                id="keyword"
                name="keyword"
                value={filters.keyword}
                onChange={handleFilterChange}
                className={styles.input}
                placeholder="訂單編號 / 姓名 / 電話 / 輪胎關鍵字"
              />
            </div>
          </div>
          <div className={styles.actionsRow}>
            <button type="submit" className={styles.primaryButton} disabled={loading}>
              {loading ? '查詢中...' : '查詢'}
            </button>
            <button type="button" className={styles.secondaryButton} onClick={handleResetFilters}>
              清除條件
            </button>
          </div>
        </form>
      </section>

      <section className={styles.card}>
        <h2 className={styles.sectionTitle}>訂單列表</h2>
        {errorMessage && <p className={styles.errorMessage}>{errorMessage}</p>}
        {successMessage && <p className={styles.successMessage}>{successMessage}</p>}
        {loading ? (
          <p className={styles.helperText}>載入中...</p>
        ) : (
          <div className={styles.tableWrapper}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>訂單編號</th>
                  <th>訂單</th>
                  <th>客戶資料</th>
                  <th>輪胎資訊</th>
                  <th>狀態</th>
                  <th>時間</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {filteredOrders.length === 0 ? (
                  <tr>
                    <td colSpan={7} className={styles.emptyCell}>查無資料</td>
                  </tr>
                ) : (
                  filteredOrders.map((order) => (
                    <tr key={order.id}>
                      <td>
                        <span className={styles.orderId}>#{order.id}</span>
                      </td>
                      <td>
                        <div className={styles.cellStack}>
                          <span>數量：{order.quantity}</span>
                          <span>{installationLabel[order.installationOption]}</span>
                        </div>
                      </td>
                      <td>
                        <div className={styles.cellStack}>
                          <span className={styles.cellTitle}>{order.customerName}</span>
                          <span>{order.phone}</span>
                          {order.email && <span>{order.email}</span>}
                          {order.carModel && <span>車款：{order.carModel}</span>}
                          {order.deliveryAddress && <span>地址：{order.deliveryAddress}</span>}
                          {order.notes && <span className={styles.noteText}>備註：{order.notes}</span>}
                        </div>
                      </td>
                      <td>
                        <div className={styles.cellStack}>
                          <span className={styles.cellTitle}>
                            {order.tireBrand} {order.tireSeries}
                          </span>
                          <span>
                            {order.tireSize}
                            {order.tireOrigin ? ` / ${order.tireOrigin}` : ''}
                          </span>
                          <span>單價：{formatPrice(order.tirePrice)}</span>
                        </div>
                      </td>
                      <td>
                        <span className={`${styles.statusBadge} ${styles[`status${order.status}`]}`}>
                          {statusLabel[order.status]}
                        </span>
                        <div className={styles.statusControl}>
                          <select
                            className={styles.input}
                            value={statusDrafts[order.id] ?? order.status}
                            onChange={(event) =>
                              handleStatusDraftChange(order.id, event.target.value as OrderStatus)
                            }
                          >
                            <option value="PENDING">待處理</option>
                            <option value="CONFIRMED">已確認</option>
                            <option value="COMPLETED">已完成</option>
                            <option value="CANCELLED">已取消</option>
                          </select>
                        </div>
                      </td>
                      <td>
                        <div className={styles.cellStack}>
                          <span>建立：{formatDate(order.createdAt)}</span>
                          <span>更新：{formatDate(order.updatedAt)}</span>
                        </div>
                      </td>
                      <td>
                        <button
                          type="button"
                          className={styles.primaryButton}
                          onClick={() => handleUpdateStatus(order.id)}
                          disabled={savingId === order.id}
                        >
                          {savingId === order.id ? '更新中...' : '更新狀態'}
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
};

export default AdminOrders;
