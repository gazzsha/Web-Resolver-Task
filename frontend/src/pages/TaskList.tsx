import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Grid,
  Card,
  CardContent,
  Typography,
  Box,
  Chip,
  Button,
  TextField,
  InputAdornment,
  CircularProgress,
  Alert,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { taskService } from '@/services/api';
import type { Task } from '@/types';

const TaskList = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [tasks, setTasks] = useState<Task[]>([]);

  useEffect(() => {
    const fetchTasks = async () => {
      try {
        setLoading(true);
        const data = await taskService.getAll();
        console.log('Fetched tasks:', data);
        setTasks(data);
        setError(null);
      } catch (err: any) {
        console.error('Failed to fetch tasks:', err);
        setError('Failed to load tasks from API. Using demo data.');
        // Demo data with UUIDs
        setTasks([
          {
            testId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
            title: 'Two Sum',
            description: 'Given an array of integers, return indices of the two numbers that add up to a specific target.',
            difficulty: 'Easy',
            category: 'Array',
          },
          {
            testId: 'b2c3d4e5-f6a7-8901-bcde-f12345678901',
            title: 'Valid Parentheses',
            description: 'Determine if a string containing just parentheses is valid.',
            difficulty: 'Easy',
            category: 'String',
          },
          {
            testId: 'c3d4e5f6-a7b8-9012-cdef-123456789012',
            title: 'Merge Two Sorted Lists',
            description: 'Merge two sorted linked lists into one sorted list.',
            difficulty: 'Easy',
            category: 'Linked List',
          },
          {
            testId: 'e5f6a7b8-c9d0-1234-ef01-345678901234',
            title: 'Valid Palindrome',
            description: 'Check if a string is a palindrome after removing non-alphanumeric characters.',
            difficulty: 'Easy',
            category: 'String',
          },
          {
            testId: 'c5d6e7f8-a9b0-1234-8901-345678901234',
            title: 'Median of Two Sorted Arrays',
            description: 'Find the median of two sorted arrays with O(log(m+n)) complexity.',
            difficulty: 'Hard',
            category: 'Array',
          },
        ]);
      } finally {
        setLoading(false);
      }
    };

    fetchTasks();
  }, []);

  const filteredTasks = tasks.filter(
    (task) =>
      task.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      task.description.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const getDifficultyColor = (difficulty: string) => {
    switch (difficulty) {
      case 'Easy':
        return 'success';
      case 'Medium':
        return 'warning';
      case 'Hard':
        return 'error';
      default:
        return 'default';
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom sx={{ mb: 4 }}>
        Algorithmic Tasks
      </Typography>

      {error && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {/* Search Bar */}
      <TextField
        fullWidth
        placeholder="Search tasks..."
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        sx={{ mb: 4, maxWidth: 600 }}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon />
            </InputAdornment>
          ),
        }}
      />

      {/* Task Cards */}
      <Grid container spacing={3}>
        {filteredTasks.map((task) => (
          <Grid item xs={12} md={6} lg={4} key={task.testId}>
            <Card
              sx={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                transition: 'transform 0.2s, box-shadow 0.2s',
                '&:hover': {
                  transform: 'translateY(-4px)',
                  boxShadow: '0px 8px 16px rgba(0, 0, 0, 0.15)',
                },
              }}
            >
              <CardContent sx={{ flexGrow: 1 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                  <Typography variant="h6" gutterBottom>
                    {task.title}
                  </Typography>
                  <Chip
                    label={task.difficulty}
                    color={getDifficultyColor(task.difficulty) as any}
                    size="small"
                  />
                </Box>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2, minHeight: 60 }}>
                  {task.description}
                </Typography>
                <Chip
                  label={task.category || 'General'}
                  size="small"
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </CardContent>
              <CardContent sx={{ pt: 0 }}>
                <Button
                  variant="contained"
                  fullWidth
                  endIcon={<PlayArrowIcon />}
                  onClick={() => navigate(`/tasks/${task.testId}`)}
                >
                  Solve Task
                </Button>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
};

export default TaskList;
