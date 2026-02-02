import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Layout from './components/Layout';
import Home from './pages/Home';
import Promotions from './pages/Promotions';
import FindTires from './pages/FindTires';
import TireSeriesPage from './pages/TireSeries';
import TireKnowledge from './pages/TireKnowledge';
import RepairServices from './pages/RepairServices';
import About from './pages/About';
import OrderPage from './pages/Order';
import AdminLogin from './pages/AdminLogin';
import AdminOrders from './pages/AdminOrders';
import AdminTires from './pages/AdminTires';
import PagePlaceholder from './pages/PagePlaceholder';

const App = () => {
  return (
    <BrowserRouter>
      {/* 這裡要把 AuthProvider 加進去，讓裡面的 AdminLogin 可以使用 useAuth */}
      <AuthProvider>
        <Layout>
          <Routes>
            {/* 公開頁面 */}
            <Route path="/" element={<Home />} />
            <Route path="/promotions" element={<Promotions />} />
            <Route path="/order" element={<OrderPage />} />
            <Route path="/find-tires" element={<FindTires />} />
            <Route path="/tire-series" element={<TireSeriesPage />} />
            <Route path="/tire-knowledge" element={<TireKnowledge />} />
            <Route path="/repair-services" element={<RepairServices />} />
            <Route path="/about" element={<About />} />

            {/* 後台管理頁面 */}
            <Route path="/admin/login" element={<AdminLogin />} />
            <Route path="/admin/orders" element={<AdminOrders />} />
            <Route path="/admin/tires" element={<AdminTires />} />
            <Route path="/admin/promotions" element={<PagePlaceholder title="促銷活動管理" />} />
            <Route path="/admin/analytics" element={<PagePlaceholder title="訪客統計" />} />

            {/* 404 頁面 */}
            <Route path="*" element={<PagePlaceholder title="頁面不存在" />} />
          </Routes>
        </Layout>
      </AuthProvider>
    </BrowserRouter>
  );
};

export default App;
