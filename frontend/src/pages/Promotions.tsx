import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import styles from '../styles/Promotions.module.css';

type TireCatalogItem = {
  id: number;
  brand: string;
  series: string;
  origin?: string | null;
  size: string;
  price: number | null;
};

type InstallationCost = {
  sizeRange: string;
  cost: number;
  minSize: number;
  maxSize: number;
};

const installationCostsInfo: InstallationCost[] = [
  { sizeRange: '14-16吋', cost: 300, minSize: 14, maxSize: 16 },
  { sizeRange: '17-18吋', cost: 400, minSize: 17, maxSize: 18 },
  { sizeRange: '19-20吋', cost: 500, minSize: 19, maxSize: 20 }
];

const SHIPPING_COST_PER_TIRE = 100;

const getSizeInch = (size: string): number | null => {
  const match = size.match(/R\s*(\d{2,3})/i);
  if (!match) {
    return null;
  }
  const value = Number(match[1]);
  return Number.isFinite(value) ? value : null;
};

const getInstallationCost = (tireSizeInch: number | null) => {
  if (tireSizeInch === null) {
    return null;
  }
  const foundCostInfo = installationCostsInfo.find(
    (info) => tireSizeInch >= info.minSize && tireSizeInch <= info.maxSize
  );
  return foundCostInfo ? foundCostInfo.cost : null;
};

const formatPrice = (value: number | null, suffix: string) => {
  if (value === null) {
    return '現場報價';
  }
  return `${value.toLocaleString()}${suffix}`;
};

const Promotions = () => {
  const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL;
  const apiBaseUrl = (rawApiBaseUrl ?? '').trim();

  const [tireCatalog, setTireCatalog] = useState<TireCatalogItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    document.title = '輪胎限時促銷 - 廣翊輪胎館';
  }, []);

  useEffect(() => {
    if (rawApiBaseUrl === undefined) {
      return;
    }

    const loadCatalog = async () => {
      setIsLoading(true);
      setErrorMessage('');

      try {
        const response = await fetch(`${apiBaseUrl}/api/tires?active=true`);
        if (!response.ok) {
          setErrorMessage(`載入促銷輪胎失敗（${response.status}）。`);
          return;
        }
        const data = await response.json();
        const items = Array.isArray(data) ? data : (data.items ?? []);
        setTireCatalog(items);
      } catch (error) {
        setErrorMessage('載入促銷輪胎失敗，請稍後再試。');
      } finally {
        setIsLoading(false);
      }
    };

    void loadCatalog();
  }, [apiBaseUrl, rawApiBaseUrl]);

  return (
    <div className={styles.container}>
      <h1 className={styles.pageTitle}>輪胎限時促銷</h1>
      <p className={styles.pageSubtitle}>於 2025/05/10 更新以下促銷輪胎</p>

      <div className={styles.installationInfoSection}>
        <h2 className={styles.subHeading}>服務選項說明</h2>
        <div className={styles.serviceOptionGlobalContainer}>
          <div className={styles.serviceOptionGlobal}>
            <h3 className={styles.serviceOptionTitle}>現場安裝</h3>
            <p>包含輪胎拆裝及平衡，工錢如下：</p>
            <ul className={styles.installationListSmall}>
              {installationCostsInfo.map((item) => (
                <li key={item.sizeRange}>
                  {item.sizeRange}： <span className={styles.costHighlight}>{item.cost}元/條</span>
                </li>
              ))}
            </ul>
          </div>
          <div className={styles.serviceOptionGlobal}>
            <h3 className={styles.serviceOptionTitle}>寄送到府</h3>
            <p>
              運費：<span className={styles.costHighlight}>{SHIPPING_COST_PER_TIRE}元/條</span>
            </p>
            <p className={styles.infoNoteSmall}>(價格不含安裝)</p>
            <p className={`${styles.infoNoteSmall} ${styles.pickupNote}`}>
              亦歡迎填寫表單預約後來店自取
            </p>
          </div>
        </div>
      </div>

      {isLoading && <p className={styles.infoNoteSmall}>載入中...</p>}
      {errorMessage && <p className={styles.noResultsMessage}>{errorMessage}</p>}

      {!isLoading && !errorMessage && tireCatalog.length > 0 ? (
        <div className={styles.promotionsGrid}>
          {tireCatalog.map((tire) => {
            const sizeInch = getSizeInch(tire.size);
            const installCost = getInstallationCost(sizeInch);
            const installedPrice =
              tire.price !== null && installCost !== null ? tire.price + installCost : null;
            const shippedPrice = tire.price !== null ? tire.price + SHIPPING_COST_PER_TIRE : null;

            return (
              <div key={tire.id} className={styles.promoCard}>
                <div className={styles.promoContent}>
                  <h3 className={styles.promoName}>{`${tire.brand} ${tire.series}`}</h3>
                  <p className={styles.promoTirePrice}>
                    規格：<span className={styles.priceValue}>{tire.size}</span>
                  </p>
                  {tire.origin && (
                    <p className={styles.infoNoteSmall}>產地：{tire.origin}</p>
                  )}
                  <p className={styles.promoTirePrice}>
                    輪胎優惠價：
                    <span className={styles.priceValue}>{formatPrice(tire.price, '元/條')}</span>
                  </p>

                  <div className={styles.serviceOptionsContainer}>
                    <div className={styles.serviceOptionCard}>
                      <h4 className={styles.serviceOptionCardTitle}>選擇一：現場安裝</h4>
                      <p className={styles.serviceDetail}>
                        安裝費 ({sizeInch ? `${sizeInch}吋` : '—'}):
                        <span className={styles.costHighlightSm}>
                          {formatPrice(installCost, '元/條')}
                        </span>
                      </p>
                      <p className={styles.totalEstimate}>
                        完工總價 (1條)：
                        <span className={styles.totalPriceValue}>
                          {formatPrice(installedPrice, '元')}
                        </span>
                      </p>
                    </div>

                    <div className={styles.serviceOptionCard}>
                      <h4 className={styles.serviceOptionCardTitle}>選擇二：寄送到府</h4>
                      <p className={styles.serviceDetail}>
                        運費：
                        <span className={styles.costHighlightSm}>
                          {formatPrice(SHIPPING_COST_PER_TIRE, '元/條')}
                        </span>
                      </p>
                      <p className={styles.totalEstimate}>
                        寄送總價 (1條)：
                        <span className={styles.totalPriceValue}>
                          {formatPrice(shippedPrice, '元')}
                        </span>
                      </p>
                    </div>
                  </div>

                  <Link to={`/order?tireId=${tire.id}`} className={styles.orderButton}>
                    立即訂購
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        !isLoading &&
        !errorMessage && (
          <p className={styles.noResultsMessage}>沒有找到符合條件的促銷輪胎。</p>
        )
      )}

      <p className={styles.footerNote}>
        **所有優惠價格與內容以現場報價為準，本公司保留活動修改及終止之權利。**
      </p>
    </div>
  );
};

export default Promotions;
