import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import styles from '../styles/Promotions.module.css';

type PromotionItem = {
  id: string;
  name: string;
  tirePrice: number;
  tireSizeInch: number;
};

type InstallationCost = {
  sizeRange: string;
  cost: number;
  minSize: number;
  maxSize: number;
};

type ParsedPromo = {
  series: string;
  size: string;
};

type TireCatalogItem = {
  id: number;
  brand: string;
  series: string;
  size: string;
  price: number | null;
  isActive?: boolean;
};

const promotionsData: PromotionItem[] = [
  {
    id: 'PSR17005',
    name: '普利司通 225/60 R18 ALENZA H/L 33 100V 日本',
    tirePrice: 3850,
    tireSizeInch: 18
  },
  {
    id: 'PSRFB03',
    name: '普利司通 225/60 R18 ALENZA LX100 100H 台灣',
    tirePrice: 3810,
    tireSizeInch: 18
  },
  {
    id: 'PSRF829',
    name: '普利司通 225/60 R18 HL422+ 100H 台灣',
    tirePrice: 3530,
    tireSizeInch: 18
  },
  {
    id: 'PSR0FA48',
    name: '普利司通 235/60 R18 ALENZA 001 107W 台灣',
    tirePrice: 3760,
    tireSizeInch: 18
  },
  {
    id: 'PSRFB02',
    name: '普利司通 235/60 R18 ALENZA LX100 103H 台灣',
    tirePrice: 3810,
    tireSizeInch: 18
  },
  {
    id: 'PSRF914',
    name: '普利司通 235/60 R18 D33 103H 台灣',
    tirePrice: 3430,
    tireSizeInch: 18
  },
  {
    id: 'PSR0FA40',
    name: '普利司通 215/55 R17 T005A 094W 台灣',
    tirePrice: 3540,
    tireSizeInch: 17
  },
  {
    id: 'PSR0FA76',
    name: '普利司通 215/55 R17 TURANZA 6 094W 台灣',
    tirePrice: 3400,
    tireSizeInch: 17
  },
  {
    id: 'PSR0F830',
    name: '普利司通 215/55 R17 EP150 098V 台灣',
    tirePrice: 3170,
    tireSizeInch: 17
  },
  {
    id: 'PSR0F781',
    name: '普利司通 215/55 R17 ER33 094V 台灣',
    tirePrice: 3010,
    tireSizeInch: 17
  },
  {
    id: 'PSR0FA26',
    name: '普利司通 215/60 R17 ALENZA 001 096H 台灣',
    tirePrice: 3090,
    tireSizeInch: 17
  },
  {
    id: 'PSR0NJB6',
    name: '普利司通 215/60 R17 TURANZA 6 100H 印尼',
    tirePrice: 3410,
    tireSizeInch: 17
  }
];

const installationCostsInfo: InstallationCost[] = [
  { sizeRange: '14-16吋', cost: 300, minSize: 14, maxSize: 16 },
  { sizeRange: '17-18吋', cost: 400, minSize: 17, maxSize: 18 },
  { sizeRange: '19-20吋', cost: 500, minSize: 19, maxSize: 20 }
];

const SHIPPING_COST_PER_TIRE = 100;

const getWidthFromTireName = (name: string): string | null => {
  const parts = name.split(' ');
  if (parts.length > 1) {
    const specPart = parts[1];
    const widthPart = specPart.split('/')[0];
    if (!Number.isNaN(Number(widthPart))) {
      return widthPart;
    }
  }
  return null;
};

const parsePromoForOrder = (name: string): ParsedPromo => {
  const regex = /^(\S+)\s+(\d{2,3}\/\d{2}\s*R\d{2,3})\s+(.+?)\s+([\w\d]+\s*\S+)$/;
  const match = name.match(regex);

  if (match) {
    return {
      size: match[2],
      series: match[3]
    };
  }

  const sizeMatch = name.match(/(\d{2,3}\/\d{2}\s*R\d{2,3})/);
  const size = sizeMatch ? sizeMatch[1] : '';
  const parts = name.split(' ');
  const series = parts.length > 3 ? parts.slice(2, parts.length - 2).join(' ') : '';

  return { series, size };
};

const normalizeSeries = (value: string) => value.replace(/[^a-z0-9]/gi, '').toUpperCase();
const normalizeSize = (value: string) => value.replace(/[^0-9R]/gi, '').toUpperCase();

const isSeriesMatch = (dbSeries: string, promoSeries: string) => {
  const left = normalizeSeries(dbSeries);
  const right = normalizeSeries(promoSeries);
  if (!left || !right) return false;
  return left.includes(right) || right.includes(left);
};

const isSizeMatch = (dbSize: string, promoSize: string) =>
  normalizeSize(dbSize) === normalizeSize(promoSize);

// const tireWidthOptions = ['155', '165', '175', '185', '195', '205', '215', '225', '235', '245', '255'];

const Promotions = () => {
  const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL;
  const apiBaseUrl = (rawApiBaseUrl ?? '').trim();
  const [selectedWidth] = useState<string>('');
  const [tireCatalog, setTireCatalog] = useState<TireCatalogItem[]>([]);

  useEffect(() => {
    document.title = '輪胎促銷活動 - 廣翊輪胎館';
  }, []);

  useEffect(() => {
    if (rawApiBaseUrl === undefined) {
      return;
    }

    const loadCatalog = async () => {
      try {
        const response = await fetch(`${apiBaseUrl}/api/tires?active=true`);
        if (!response.ok) {
          return;
        }
        const data = await response.json();
        const items = Array.isArray(data) ? data : (data.items ?? []);
        setTireCatalog(items);
      } catch (error) {
        // Fallback to static promo data if API fails
      }
    };

    void loadCatalog();
  }, [apiBaseUrl]);

  const catalogLookup = useMemo(() => tireCatalog, [tireCatalog]);

  const getInstallationCost = (tireSizeInch: number) => {
    const foundCostInfo = installationCostsInfo.find(
      (info) => tireSizeInch >= info.minSize && tireSizeInch <= info.maxSize
    );
    return foundCostInfo ? foundCostInfo.cost : 0;
  };

  const filteredPromotions = selectedWidth
    ? promotionsData.filter((promo) => getWidthFromTireName(promo.name) === selectedWidth)
    : promotionsData;

  return (
    <div className={styles.container}>
      <h1 className={styles.pageTitle}>輪胎限時促銷</h1>
      <p className={styles.pageSubtitle}>於 2025/05/10 更新以下促銷輪胎</p>

      {/*<div className={styles.filterContainer}>*/}
      {/*  <h3 className={styles.filterTitle}>胎面寬度</h3>*/}
      {/*  <div className={styles.filterOptionsList}>*/}
      {/*    <button*/}
      {/*      className={`${styles.filterOptionButton} ${selectedWidth === '' ? styles.activeFilter : ''}`}*/}
      {/*      onClick={() => setSelectedWidth('')}*/}
      {/*    >*/}
      {/*      全部顯示*/}
      {/*    </button>*/}
      {/*    {tireWidthOptions.map((width) => (*/}
      {/*      <button*/}
      {/*        key={width}*/}
      {/*        className={`${styles.filterOptionButton} ${selectedWidth === width ? styles.activeFilter : ''}`}*/}
      {/*        onClick={() => setSelectedWidth(width)}*/}
      {/*      >*/}
      {/*        {width}*/}
      {/*      </button>*/}
      {/*    ))}*/}
      {/*  </div>*/}
      {/*</div>*/}

      <div className={styles.installationInfoSection}>
        <h2 className={styles.subHeading}>服務選項說明</h2>
        <div className={styles.serviceOptionGlobalContainer}>
          <div className={styles.serviceOptionGlobal}>
            <h3 className={styles.serviceOptionTitle}>現場安裝</h3>
            <p>包含輪胎拆裝及平衡，工錢如下：</p>
            <ul className={styles.installationListSmall}>
              {installationCostsInfo.map((item) => (
                <li key={item.sizeRange}>
                  {item.sizeRange}: <span className={styles.costHighlight}>{item.cost}元/條</span>
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
            <p className={`${styles.infoNoteSmall} ${styles.pickupNote}`}>亦歡迎填寫表單預約後來店自取</p>
          </div>
        </div>
      </div>

      {filteredPromotions.length > 0 ? (
        <div className={styles.promotionsGrid}>
          {filteredPromotions.map((promo) => {
            const installCost = getInstallationCost(promo.tireSizeInch);
            const { series, size } = parsePromoForOrder(promo.name);
            const matchedTire = catalogLookup.find(
              (tire) => isSizeMatch(tire.size, size) && isSeriesMatch(tire.series, series)
            );
            const basePrice = matchedTire?.price ?? promo.tirePrice;
            const installedPrice = basePrice + installCost;
            const shippedPrice = basePrice + SHIPPING_COST_PER_TIRE;
            const orderLinkParams = new URLSearchParams();
            if (matchedTire?.id) {
              orderLinkParams.set('tireId', String(matchedTire.id));
            } else {
              if (series) {
                orderLinkParams.set('series', series);
              }
              if (size) {
                orderLinkParams.set('size', size);
              }
            }
            const orderLink = `/order${orderLinkParams.toString() ? `?${orderLinkParams.toString()}` : ''}`;

            return (
              <div key={promo.id} className={styles.promoCard}>
                <div className={styles.promoContent}>
                  <h3 className={styles.promoName}>{promo.name}</h3>
                  <p className={styles.promoTirePrice}>
                    輪胎優惠價： <span className={styles.priceValue}>{basePrice}元/條</span>
                  </p>

                  <div className={styles.serviceOptionsContainer}>
                    <div className={styles.serviceOptionCard}>
                      <h4 className={styles.serviceOptionCardTitle}>選擇一：現場安裝</h4>
                      <p className={styles.serviceDetail}>
                        安裝費 ({promo.tireSizeInch}吋)：{' '}
                        <span className={styles.costHighlightSm}>{installCost}元/條</span>
                      </p>
                      <p className={styles.totalEstimate}>
                        完工總價 (1條)： <span className={styles.totalPriceValue}>{installedPrice}元</span>
                      </p>
                    </div>

                    <div className={styles.serviceOptionCard}>
                      <h4 className={styles.serviceOptionCardTitle}>選擇二：寄送到府</h4>
                      <p className={styles.serviceDetail}>
                        運費： <span className={styles.costHighlightSm}>{SHIPPING_COST_PER_TIRE}元/條</span>
                      </p>
                      <p className={styles.totalEstimate}>
                        寄送總價 (1條)： <span className={styles.totalPriceValue}>{shippedPrice}元</span>
                      </p>
                    </div>
                  </div>

                  <Link to={orderLink} className={styles.orderButton}>
                    立即訂購
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <p className={styles.noResultsMessage}>沒有找到符合條件的促銷輪胎。</p>
      )}

      <p className={styles.footerNote}>
        **所有優惠價格與內容以現場報價為準，本公司保留活動修改及終止之權利。**
      </p>
    </div>
  );
};

export default Promotions;
