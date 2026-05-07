import { Routes, Route } from 'react-router-dom';
import { Box } from '@mui/material';
import Layout from './components/common/Layout';
import { RequireAuth } from './components/auth/RequireAuth';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import Dashboard from './pages/Dashboard';
import TaskList from './pages/TaskList';
import TaskDetail from './pages/TaskDetail';
import Submission from './pages/Submission';
import Results from './pages/Results';
import SubmissionsPage from './pages/SubmissionsPage';
import StatisticsPage from './pages/StatisticsPage';
import NotFoundPage from './pages/NotFoundPage';

function App() {
  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/*"
          element={
            <RequireAuth>
              <Layout>
                <Routes>
                  <Route path="/" element={<Dashboard />} />
                  <Route path="/tasks" element={<TaskList />} />
                  <Route path="/tasks/:id" element={<TaskDetail />} />
                  <Route path="/submit/:id" element={<Submission />} />
                  <Route path="/results/:id" element={<Results />} />
                  <Route path="/submissions" element={<SubmissionsPage />} />
                  <Route path="/statistics" element={<StatisticsPage />} />
                  <Route path="*" element={<NotFoundPage />} />
                </Routes>
              </Layout>
            </RequireAuth>
          }
        />
      </Routes>
    </Box>
  );
}

export default App;
