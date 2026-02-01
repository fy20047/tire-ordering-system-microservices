import { ChangeEvent, FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { adminApiFetch } from '../api/adminApi';
import styles from '../styles/AdminTires.module.css';

type Tire = {
  id: number;
  brand: string;
  series: string;
  origin?: string | null;
  size: string;
  price: number | null;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
};

type TireFormState = {
  brand: string;
  series: string;
  origin: string;
  size: string;
  price: string;
  isActive: boolean;
};

type Filters = {
  brand: string;
  series: string;
  size: string;
  active: 'all' | 'true' | 'false';
};

const defaultFormState: TireFormState = {
  brand: '',
  series: '',
  origin: '',
  size: '',
  price: '',
  isActive: true
};

const AdminTires = () => {
  const navigate = useNavigate();
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;
  const getToken = () => localStorage.getItem('adminToken');

  const [tires, setTires] = useState<Tire[]>([]);
  const [filters, setFilters] = useState<Filters>({
    brand: '',
    series: '',
    size: '',
    active: 'all'
  });
  const [form, setForm] = useState<TireFormState>(defaultFormState);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  useEffect(() => {
    document.title = '輪胎管理';
    if (!getToken()) {
      navigate('/admin/login');
      return;
    }
    void fetchTires();
  }, [navigate]);

  const fetchTires = async (nextFilters?: Filters) => {
    if (!apiBaseUrl || !getToken()) {
      return;
    }

    setLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    const appliedFilters = nextFilters ?? filters;
    const params = new URLSearchParams();
    if (appliedFilters.brand.trim()) params.set('brand', appliedFilters.brand.trim());
    if (appliedFilters.series.trim()) params.set('series', appliedFilters.series.trim());
    if (appliedFilters.size.trim()) params.set('size', appliedFilters.size.trim());
    if (appliedFilters.active !== 'all') params.set('active', appliedFilters.active);

    const query = params.toString();

    try {
      const response = await adminApiFetch(`/api/admin/tires${query ? `?${query}` : ''}`);

      if (response.status === 401 || response.status === 403) {
        localStorage.removeItem('adminToken');
        navigate('/admin/login');
        return;
      }

      if (!response.ok) {
        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
          const data = await response.json();
          setErrorMessage(data.message || `載入輪胎資料失敗（${response.status}）。`);
        } else {
          setErrorMessage(`載入輪胎資料失敗（${response.status}）。`);
        }
        return;
      }

      const data = await response.json();
      setTires(data.items ?? []);
    } catch (error) {
      setErrorMessage('載入輪胎資料失敗，請稍後再試。');
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
    void fetchTires();
  };

  const handleResetFilters = () => {
    const clearedFilters: Filters = { brand: '', series: '', size: '', active: 'all' };
    setFilters(clearedFilters);
    void fetchTires(clearedFilters);
  };

  const handleFormChange = (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const target = event.target;
    const value =
      target instanceof HTMLInputElement && target.type === 'checkbox'
        ? target.checked
        : target.value;

    setForm((prev) => ({
      ...prev,
      [target.name]: value
    }));
  };

  const handleEdit = (tire: Tire) => {
    setEditingId(tire.id);
    setForm({
      brand: tire.brand,
      series: tire.series,
      origin: tire.origin ?? '',
      size: tire.size,
      price: tire.price === null ? '' : String(tire.price),
      isActive: tire.isActive
    });
    setSuccessMessage('');
    setErrorMessage('');
  };

  const handleCancelEdit = () => {
    setEditingId(null);
    setForm(defaultFormState);
  };

  const handleSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!apiBaseUrl || !getToken()) {
      return;
    }

    setSaving(true);
    setErrorMessage('');
    setSuccessMessage('');

    const payload = {
      brand: form.brand.trim(),
      series: form.series.trim(),
      origin: form.origin.trim() || null,
      size: form.size.trim(),
      price: form.price.trim() === '' ? null : Number(form.price),
      isActive: form.isActive
    };

    const isEditing = editingId !== null;
    // const url = isEditing
    //   ? `${apiBaseUrl}/api/admin/tires/${editingId}`
    //   : `${apiBaseUrl}/api/admin/tires`;
    const method = isEditing ? 'PUT' : 'POST';

    try {
      const response = await adminApiFetch(`/api/admin/tires${isEditing ? `/${editingId}` : ''}`, {
        method,
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      if (response.status === 401 || response.status === 403) {
        localStorage.removeItem('adminToken');
        navigate('/admin/login');
        return;
      }

      if (!response.ok) {
        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
          const data = await response.json();
          setErrorMessage(data.message || '儲存失敗，請確認欄位內容。');
        } else {
          setErrorMessage(`儲存失敗（${response.status}）。`);
        }
        return;
      }

      setSuccessMessage(isEditing ? '輪胎資料已更新。' : '輪胎資料已新增。');
      setForm(defaultFormState);
      setEditingId(null);
      await fetchTires();
    } catch (error) {
      setErrorMessage('儲存失敗，請稍後再試。');
    } finally {
      setSaving(false);
    }
  };

  const handleToggleActive = async (tire: Tire) => {
    if (!apiBaseUrl || !getToken()) {
      return;
    }

    try {
      const response = await adminApiFetch(`/api/admin/tires/${tire.id}/active`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ isActive: !tire.isActive })
      });

      if (response.status === 401 || response.status === 403) {
        localStorage.removeItem('adminToken');
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

      await fetchTires();
    } catch (error) {
      setErrorMessage('更新狀態失敗，請稍後再試。');
    }
  };

  const handleLogout = async () => {
    try {
      await adminApiFetch('/api/admin/logout', { method: 'POST' });
    } catch (error) {
      // best-effort logout; still clear local token
    }
    localStorage.removeItem('adminToken');
    navigate('/admin/login');
  };

  const formatPrice = (price: number | null) => (price === null ? '—' : price.toLocaleString());
  const formatDate = (value?: string) => {
    if (!value) return '—';
    const normalized = value.replace('T', ' ');
    const withoutMs = normalized.split('.')[0];
    return withoutMs.replace('Z', '');
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>輪胎商品管理</h1>
        <div className={styles.headerActions}>
          <button type="button" className={styles.secondaryButton} onClick={() => navigate('/admin/orders')}>
            前往訂單管理
          </button>
          <button type="button" className={styles.secondaryButton} onClick={handleLogout}>
            登出
          </button>
        </div>
      </div>

      <section className={styles.card}>
        <h2 className={styles.sectionTitle}>{editingId ? '編輯輪胎' : '新增輪胎'}</h2>
        <form className={styles.form} onSubmit={handleSave}>
          <div className={styles.formRow}>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="formBrand">品牌</label>
              <input
                id="formBrand"
                name="brand"
                value={form.brand}
                onChange={handleFormChange}
                className={styles.input}
                required
              />
            </div>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="formSeries">系列</label>
              <input
                id="formSeries"
                name="series"
                value={form.series}
                onChange={handleFormChange}
                className={styles.input}
                required
              />
            </div>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="formOrigin">產地</label>
              <input
                id="formOrigin"
                name="origin"
                value={form.origin}
                onChange={handleFormChange}
                className={styles.input}
              />
            </div>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="formSize">尺寸</label>
              <input
                id="formSize"
                name="size"
                value={form.size}
                onChange={handleFormChange}
                className={styles.input}
                required
              />
            </div>
          </div>
          <div className={styles.formRow}>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="formPrice">價格</label>
              <input
                id="formPrice"
                name="price"
                type="number"
                value={form.price}
                onChange={handleFormChange}
                className={styles.input}
                min="0"
              />
            </div>
            <div className={styles.formGroupCheckbox}>
              <label className={styles.checkboxLabel}>
                <input
                  type="checkbox"
                  name="isActive"
                  checked={form.isActive}
                  onChange={handleFormChange}
                />
                上架
              </label>
            </div>
          </div>
          {errorMessage && <p className={styles.errorMessage}>{errorMessage}</p>}
          {successMessage && <p className={styles.successMessage}>{successMessage}</p>}
          <div className={styles.actionsRow}>
            <button type="submit" className={styles.primaryButton} disabled={saving}>
              {saving ? '儲存中...' : editingId ? '更新輪胎' : '新增輪胎'}
            </button>
            {editingId && (
              <button type="button" className={styles.secondaryButton} onClick={handleCancelEdit}>
                取消編輯
              </button>
            )}
          </div>
        </form>
      </section>

      <section className={styles.card}>
        <h2 className={styles.sectionTitle}>搜尋/篩選</h2>
        <form className={styles.filterForm} onSubmit={handleSearch}>
          <div className={styles.formRow}>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="brand">品牌</label>
              <input
                id="brand"
                name="brand"
                value={filters.brand}
                onChange={handleFilterChange}
                className={styles.input}
              />
            </div>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="series">系列</label>
              <input
                id="series"
                name="series"
                value={filters.series}
                onChange={handleFilterChange}
                className={styles.input}
              />
            </div>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="size">尺寸</label>
              <input
                id="size"
                name="size"
                value={filters.size}
                onChange={handleFilterChange}
                className={styles.input}
              />
            </div>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="active">上架狀態</label>
              <select
                id="active"
                name="active"
                value={filters.active}
                onChange={handleFilterChange}
                className={styles.input}
              >
                <option value="all">全部</option>
                <option value="true">上架中</option>
                <option value="false">已下架</option>
              </select>
            </div>
          </div>
          <div className={styles.actionsRow}>
            <button type="submit" className={styles.primaryButton} disabled={loading}>
              {loading ? '搜尋中...' : '搜尋'}
            </button>
            <button type="button" className={styles.secondaryButton} onClick={handleResetFilters}>
              清除條件
            </button>
          </div>
        </form>
      </section>

      <section className={styles.card}>
        <h2 className={styles.sectionTitle}>輪胎列表</h2>
        {loading ? (
          <p className={styles.helperText}>載入中...</p>
        ) : (
          <div className={styles.tableWrapper}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>品牌</th>
                  <th>系列</th>
                  <th>產地</th>
                  <th>尺寸</th>
                  <th>價格</th>
                  <th>上架</th>
                  <th>更新時間</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {tires.length === 0 ? (
                  <tr>
                    <td colSpan={8} className={styles.emptyCell}>查無資料</td>
                  </tr>
                ) : (
                  tires.map((tire) => (
                    <tr key={tire.id}>
                      <td>{tire.brand}</td>
                      <td>{tire.series}</td>
                      <td>{tire.origin || '—'}</td>
                      <td>{tire.size}</td>
                      <td>{formatPrice(tire.price)}</td>
                      <td>
                        <span className={tire.isActive ? styles.activeBadge : styles.inactiveBadge}>
                          {tire.isActive ? '上架' : '下架'}
                        </span>
                      </td>
                      <td>{formatDate(tire.updatedAt)}</td>
                      <td>
                        <div className={styles.tableActions}>
                          <button
                            type="button"
                            className={styles.linkButton}
                            onClick={() => handleEdit(tire)}
                          >
                            編輯
                          </button>
                          <button
                            type="button"
                            className={styles.linkButton}
                            onClick={() => handleToggleActive(tire)}
                          >
                            {tire.isActive ? '下架' : '上架'}
                          </button>
                        </div>
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

export default AdminTires;
