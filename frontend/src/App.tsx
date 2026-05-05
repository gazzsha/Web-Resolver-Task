import { Routes, Route } from 'react-router-dom';
import { Box } from '@mui/material';
import Layout from './components/common/Layout';
import Dashboard from './pages/Dashboard';
import TaskList from './pages/TaskList';
import TaskDetail from './pages/TaskDetail';
import Submission from './pages/Submission';
import Results from './pages/Results';

function App() {
  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Layout>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/tasks" element={<TaskList />} />
          <Route path="/tasks/:id" element={<TaskDetail />} />
          <Route path="/submit/:id" element={<Submission />} />
          <Route path="/results/:id" element={<Results />} />
        </Routes>
      </Layout>
    </Box>
  );
}

export default App;
