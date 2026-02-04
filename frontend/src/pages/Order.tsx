import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import styles from '../styles/TireOrder.module.css';

type Tire = {
  id: number;
  brand: string;
  series: string;
  size: string;
  price: number | null;
  isActive?: boolean;
};

type OrderFormData = {
  customerName: string;
  phone: string;
  email: string;
  quantity: string;
  installationOption: 'INSTALL' | 'PICKUP' | 'DELIVERY';
  deliveryAddress: string;
  carModel: string;
  notes: string;
};

type SubmitStatus = 'success' | 'error' | null;

const OrderPage = () => {
  const [searchParams] = useSearchParams();
  const tireIdParam = searchParams.get('tireId');
  const parsedTireId = tireIdParam ? Number(tireIdParam) : null;
  const hasTireId = parsedTireId !== null && Number.isFinite(parsedTireId);
  const seriesParam = (searchParams.get('series') ?? '').trim();
  const sizeParam = (searchParams.get('size') ?? '').trim();
  const hasPrefill = Boolean(seriesParam || sizeParam);
  const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL;
  const apiBaseUrl = (rawApiBaseUrl ?? '').trim();

  const [tireOptions, setTireOptions] = useState<Tire[]>([]);
  const [selectedTireId, setSelectedTireId] = useState<number | null>(null);
  const [isTireLocked, setIsTireLocked] = useState(false);
  const [tireLoading, setTireLoading] = useState(true);
  const [tireError, setTireError] = useState<string | null>(null);
  const [widthFilter, setWidthFilter] = useState('');
  const selectedTire = useMemo(
    () => tireOptions.find((tire) => tire.id === selectedTireId) ?? null,
    [tireOptions, selectedTireId]
  );

  const [formData, setFormData] = useState<OrderFormData>({
    customerName: '',
    phone: '',
    email: '',
    quantity: '1',
    installationOption: 'INSTALL',
    deliveryAddress: '',
    carModel: '',
    notes: ''
  });

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitStatus, setSubmitStatus] = useState<SubmitStatus>(null);
  const [submitMessage, setSubmitMessage] = useState('');

  const widthOptions = useMemo(() => {
    const widths = new Set<string>();
    tireOptions.forEach((tire) => {
      const width = extractWidth(tire.size);
      if (width) {
        widths.add(width);
      }
    });
    return Array.from(widths).sort((a, b) => Number(a) - Number(b));
  }, [tireOptions]);

  const filteredTireOptions = useMemo(() => {
    if (!widthFilter) {
      return tireOptions;
    }
    return tireOptions.filter((tire) => {
      if (isAssistanceOption(tire)) {
        return true;
      }
      return extractWidth(tire.size) === widthFilter;
    });
  }, [tireOptions, widthFilter]);

  useEffect(() => {
    let isActive = true;

    const loadTires = async () => {
      if (rawApiBaseUrl === undefined) {
        setTireError('API Base URL 未設定，請先設定 .env。');
        setTireLoading(false);
        return;
      }

      setTireLoading(true);
      setTireError(null);

      if (hasTireId && parsedTireId !== null) {
        try {
          const response = await fetch(`${apiBaseUrl}/api/tires/${parsedTireId}`);
          if (response.ok) {
            const tire = (await response.json()) as Tire;
            if (!isActive) return;
            setTireOptions([tire]);
            setSelectedTireId(tire.id);
            setIsTireLocked(true);
            setTireLoading(false);
            return;
          }
        } catch (error) {
          // Fall back to list if single tire fetch fails
        }
      }

      try {
        const response = await fetch(`${apiBaseUrl}/api/tires?active=true`);
        if (!response.ok) {
          throw new Error('Failed to load tires');
        }
        const data = await response.json();
        const items = Array.isArray(data) ? data : (data.items ?? []);
        if (!isActive) return;
        setTireOptions(items);
        if (hasTireId && parsedTireId !== null) {
          const matched = items.find((item: Tire) => item.id === parsedTireId);
          if (matched) {
            setSelectedTireId(matched.id);
            setIsTireLocked(true);
            const matchedWidth = extractWidth(matched.size);
            if (matchedWidth) {
              setWidthFilter(matchedWidth);
            }
          } else {
            setTireError('找不到指定輪胎，請手動選擇。');
            setIsTireLocked(false);
          }
        } else if (hasPrefill) {
          const matched = items.find((item: Tire) => matchesPrefill(item, seriesParam, sizeParam));
          if (matched) {
            setSelectedTireId(matched.id);
            setIsTireLocked(true);
            const matchedWidth = extractWidth(matched.size);
            if (matchedWidth) {
              setWidthFilter(matchedWidth);
            }
          } else {
            setTireError('找不到指定輪胎，請手動選擇。');
            setIsTireLocked(false);
          }
        }
      } catch (error) {
        if (!isActive) return;
        setTireError('無法載入輪胎清單，請稍後再試。');
      } finally {
        if (isActive) {
          setTireLoading(false);
        }
      }
    };

    loadTires();

    return () => {
      isActive = false;
    };
  }, [apiBaseUrl, hasTireId, parsedTireId, hasPrefill, seriesParam, sizeParam]);

  const handleInputChange = (
    event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const { name, value } = event.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
      ...(name === 'installationOption' && value !== 'DELIVERY'
        ? { deliveryAddress: '' }
        : {})
    }));
  };

  const handleTireSelect = (event: ChangeEvent<HTMLSelectElement>) => {
    const nextId = event.target.value ? Number(event.target.value) : null;
    setSelectedTireId(nextId);
  };

  const handleWidthFilterChange = (event: ChangeEvent<HTMLSelectElement>) => {
    const value = event.target.value;
    setWidthFilter(value);
    if (!value || !selectedTireId) {
      return;
    }
    const selected = tireOptions.find((item) => item.id === selectedTireId);
    const selectedWidth = selected ? extractWidth(selected.size) : null;
    if (selectedWidth && selectedWidth !== value && !isTireLocked) {
      setSelectedTireId(null);
    }
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (rawApiBaseUrl === undefined) {
      setSubmitStatus('error');
      setSubmitMessage('API Base URL 未設定，請先設定 .env。');
      return;
    }

    if (!selectedTireId) {
      setSubmitStatus('error');
      setSubmitMessage('請先選擇輪胎。');
      return;
    }

    if (!formData.customerName.trim()) {
      setSubmitStatus('error');
      setSubmitMessage('請填寫姓名。');
      return;
    }

    if (!formData.phone.trim()) {
      setSubmitStatus('error');
      setSubmitMessage('請填寫聯絡電話。');
      return;
    }

    if (!formData.carModel.trim()) {
      setSubmitStatus('error');
      setSubmitMessage('請填寫車款資訊。');
      return;
    }

    if (selectedTire && isAssistanceOption(selectedTire) && !formData.notes.trim()) {
      setSubmitStatus('error');
      setSubmitMessage('選擇客服協助 / 維修服務 / 找不到輪胎時，請填寫備註。');
      return;
    }

    if (formData.installationOption === 'DELIVERY' && !formData.deliveryAddress.trim()) {
      setSubmitStatus('error');
      setSubmitMessage('請填寫配送地址。');
      return;
    }

    const quantityNumber = Number(formData.quantity);
    if (!Number.isFinite(quantityNumber) || quantityNumber < 1) {
      setSubmitStatus('error');
      setSubmitMessage('請填寫正確的數量。');
      return;
    }

    setIsSubmitting(true);
    setSubmitStatus(null);
    setSubmitMessage('');

    const payload = {
      tireId: selectedTireId,
      quantity: quantityNumber,
      customerName: formData.customerName.trim(),
      phone: formData.phone.trim(),
      email: formData.email.trim() || undefined,
      installationOption: formData.installationOption,
      deliveryAddress:
        formData.installationOption === 'DELIVERY' ? formData.deliveryAddress.trim() : null,
      carModel: formData.carModel.trim(),
      notes: formData.notes.trim() || undefined
    };

    try {
      const response = await fetch(`${apiBaseUrl}/api/orders`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      const result = await response.json().catch(() => ({}));

      if (response.ok) {
        const returnedOrderId = result.orderId ?? result.id ?? '—';
        setSubmitStatus('success');
        setSubmitMessage(`訂單已送出，您的單號為：${returnedOrderId}，客服將與您聯繫確認！`);
        setFormData((prev) => ({
          ...prev,
          customerName: '',
          phone: '',
          email: '',
          quantity: '1',
          installationOption: 'INSTALL',
          deliveryAddress: '',
          carModel: '',
          notes: ''
        }));
      } else {
        setSubmitStatus('error');
        setSubmitMessage(result.message || '訂單送出失敗，請稍後再試。');
      }
    } catch (error) {
      setSubmitStatus('error');
      setSubmitMessage('訂單送出失敗，請確認網路連線或稍後再試。');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResetOrder = () => {
    setSubmitStatus(null);
    setSubmitMessage('');
    if (!isTireLocked) {
      setSelectedTireId(null);
    }
    setWidthFilter('');
    setFormData({
      customerName: '',
      phone: '',
      email: '',
      quantity: '1',
      installationOption: 'INSTALL',
      deliveryAddress: '',
      carModel: '',
      notes: ''
    });
  };

  function extractWidth(size: string) {
    if (!size) {
      return null;
    }
    const match = size.match(/^(\d{3})\s*\/?/);
    return match ? match[1] : null;
  }

  function normalizeText(value: string) {
    return value.replace(/[^a-z0-9]/gi, '').toUpperCase();
  }

  function normalizeSize(value: string) {
    return value.replace(/[^0-9R]/gi, '').toUpperCase();
  }

  function isSeriesMatch(dbSeries: string, promoSeries: string) {
    const left = normalizeText(dbSeries);
    const right = normalizeText(promoSeries);
    if (!left || !right) return false;
    return left.includes(right) || right.includes(left);
  }

  function isSizeMatch(dbSize: string, promoSize: string) {
    return normalizeSize(dbSize) === normalizeSize(promoSize);
  }

  function matchesPrefill(tire: Tire, series: string, size: string) {
    if (series && !isSeriesMatch(tire.series, series)) {
      return false;
    }
    if (size && !isSizeMatch(tire.size, size)) {
      return false;
    }
    return true;
  }

  function formatTireOptionLabel(tire: Tire) {
    if (isAssistanceOption(tire)) {
      return '客服協助 / 維修服務 / 找不到輪胎';
    }
    const size = tire.size?.trim(); // 拿輪胎尺寸，順便去掉前後空白
    const hasRealSize = size && size !== '-' && size !== '—'; // 判斷尺寸是不是有效，只要尺寸不是 - 或 —，就算有效
    const parts = [tire.brand, tire.series, hasRealSize ? size : ''].filter(Boolean);
    const baseLabel = parts.join(' ');
    const priceLabel = tire.price !== null ? ` - ${tire.price} 元` : ''; // 如果有價格才加 - 價格
    return `${baseLabel}${priceLabel}`;
  }

  function isAssistanceOption(tire: Tire) {
    const combined = `${tire.brand ?? ''} ${tire.series ?? ''}`.replace(/\s+/g, '');
    return (
      combined.includes('客服協助') ||
      combined.includes('找不到輪胎') ||
      combined.includes('維修服務') ||
      combined.includes('其他')
    );
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.pageTitle}>輪胎訂購</h1>
      <p className={styles.pageSubtitle}>填寫完畢後，店家會主動與您聯繫確認後續資料、價格、付費等流程。</p>

      {submitStatus === 'success' && (
        <div className={`${styles.submitMessage} ${styles.successMessage}`}>
          <div>{submitMessage}</div>
        </div>
      )}

      {submitStatus === 'success' && (
        <div className={styles.successResetWrapper}>
          <button type="button" className={styles.successResetButton} onClick={handleResetOrder}>
            重新回至訂購頁面
          </button>
        </div>
      )}

      {submitStatus === 'error' && (
        <div className={`${styles.submitMessage} ${styles.errorMessage}`}>{submitMessage}</div>
      )}

      {submitStatus !== 'success' && (
        <form onSubmit={handleSubmit} className={styles.orderForm}>
          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>輪胎資訊</legend>
            {tireLoading ? (
              <p className={styles.helperText}>輪胎清單載入中...</p>
            ) : (
              <>
                {tireError && <p className={styles.helperText}>{tireError}</p>}
                <div className={styles.formGroup}>
                  <label htmlFor="widthFilter" className={styles.label}>胎面寬度篩選</label>
                  <select
                    id="widthFilter"
                    name="widthFilter"
                    value={widthFilter}
                    onChange={handleWidthFilterChange}
                    className={styles.input}
                    disabled={isTireLocked}
                  >
                    <option value="">全部</option>
                    {widthOptions.map((width) => (
                      <option key={width} value={width}>
                        {width}
                      </option>
                    ))}
                  </select>
                </div>
                <div className={styles.formGroup}>
                  <label htmlFor="tireId" className={styles.label}>
                    選擇輪胎 <span className={styles.required}>*</span>
                  </label>
                  <select
                    id="tireId"
                    name="tireId"
                    value={selectedTireId ?? ''}
                    onChange={handleTireSelect}
                    className={styles.input}
                    disabled={isTireLocked}
                  >
                    <option value="">請選擇輪胎</option>
                    {filteredTireOptions.map((tire) => (
                    <option key={tire.id} value={tire.id}>
                      {formatTireOptionLabel(tire)}
                    </option>
                  ))}
                  </select>
                  {isTireLocked && (
                    <p className={styles.helperText}>此輪胎由促銷頁帶入，已鎖定。</p>
                  )}
                  {!isTireLocked && (
                    <p className={styles.helperText}>
                      找不到輪胎或需要維修服務？可選「客服協助 / 維修服務 / 找不到輪胎」，並在備註填寫需求。
                    </p>
                  )}
                </div>
              </>
            )}
          </fieldset>

          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>數量</legend>
            <div className={styles.formGroup}>
              <label htmlFor="quantity" className={styles.label}>
                訂購數量 <span className={styles.required}>*</span>
              </label>
              <input
                type="number"
                id="quantity"
                name="quantity"
                value={formData.quantity}
                onChange={handleInputChange}
                className={styles.input}
                min="1"
                required
              />
            </div>
          </fieldset>

          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>服務方式</legend>
            <div className={styles.formGroup}>
              <label htmlFor="installationOption" className={styles.label}>
                安裝/配送方式 <span className={styles.required}>*</span>
              </label>
              <select
                id="installationOption"
                name="installationOption"
                value={formData.installationOption}
                onChange={handleInputChange}
                className={styles.input}
                required
              >
                <option value="INSTALL">到店安裝</option>
                <option value="PICKUP">到店取貨</option>
                <option value="DELIVERY">住家配送</option>
              </select>
              <p className={styles.helperText}>配送才需要填地址，其他方式免填。</p>
            </div>
            {formData.installationOption === 'DELIVERY' && (
              <div className={styles.formGroup}>
                <label htmlFor="deliveryAddress" className={styles.label}>
                  配送地址 <span className={styles.required}>*</span>
                </label>
                <input
                  type="text"
                  id="deliveryAddress"
                  name="deliveryAddress"
                  value={formData.deliveryAddress}
                  onChange={handleInputChange}
                  className={styles.input}
                  required
                />
              </div>
            )}
            <div className={styles.serviceInfoCard}>
              <h3 className={styles.serviceInfoTitle}>服務選項說明</h3>
              <div className={styles.serviceInfoGrid}>
                <div className={styles.serviceInfoItem}>
                  <p>現場安裝：</p>
                  <ul>
                    <li>14-16吋：300 元/條</li>
                    <li>17-18吋：400 元/條</li>
                    <li>19-20吋：500 元/條</li>
                  </ul>
                </div>
                <div className={styles.serviceInfoItem}>
                  <p>寄送到府：</p>
                  <ul>
                    <li>運費 100 元/條（不含安裝）</li>
                  </ul>
                  <p className={styles.serviceInfoNote}>亦可填寫表單後預約到店自取</p>
                </div>
              </div>
            </div>
          </fieldset>

          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>車款資訊</legend>
            <div className={styles.formGroup}>
              <label htmlFor="carModel" className={styles.label}>
                車款資訊 <span className={styles.required}>*</span>
              </label>
              <input
                type="text"
                id="carModel"
                name="carModel"
                value={formData.carModel}
                onChange={handleInputChange}
                className={styles.input}
                placeholder="例：Toyota Altis 2019"
                required
              />
            </div>
          </fieldset>

          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>訂購人資料</legend>
            <div className={styles.formGroup}>
              <label htmlFor="customerName" className={styles.label}>
                姓名 <span className={styles.required}>*</span>
              </label>
              <input
                type="text"
                id="customerName"
                name="customerName"
                value={formData.customerName}
                onChange={handleInputChange}
                className={styles.input}
                required
              />
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="phone" className={styles.label}>
                聯絡電話 <span className={styles.required}>*</span>
              </label>
              <input
                type="tel"
                id="phone"
                name="phone"
                value={formData.phone}
                onChange={handleInputChange}
                className={styles.input}
                required
              />
            </div>
            <div className={styles.formGroup}>
              <label htmlFor="email" className={styles.label}>Email</label>
              <input
                type="email"
                id="email"
                name="email"
                value={formData.email}
                onChange={handleInputChange}
                className={styles.input}
              />
            </div>
          </fieldset>

          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>備註</legend>
            <div className={styles.formGroup}>
              <label htmlFor="notes" className={styles.label}>
                備註（預約時間或其他需求）
                {selectedTire && isAssistanceOption(selectedTire) && (
                  <span className={styles.required}>*</span>
                )}
              </label>
              <textarea
                id="notes"
                name="notes"
                value={formData.notes}
                onChange={handleInputChange}
                className={styles.textarea}
                rows={4}
                placeholder="例：希望平日晚上安裝、請先電話聯繫"
                required={Boolean(selectedTire && isAssistanceOption(selectedTire))}
              />
              {selectedTire && isAssistanceOption(selectedTire) && (
                <p className={styles.helperText}>請輸入維修項目或輪胎需求，否則無法送出。</p>
              )}
            </div>
          </fieldset>

          <div className={styles.formActions}>
            <button type="submit" className={styles.submitButton} disabled={isSubmitting}>
              {isSubmitting ? '送出中...' : '送出訂單'}
            </button>
          </div>
        </form>
      )}
    </div>
  );
};

export default OrderPage;
