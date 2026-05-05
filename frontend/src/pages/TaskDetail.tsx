import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Paper,
  Button,
  Grid,
  Chip,
  CircularProgress,
  Card,
  CardContent,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import InfoIcon from '@mui/icons-material/Info';
import TimerIcon from '@mui/icons-material/Timer';
import MemoryIcon from '@mui/icons-material/Memory';
import type { Task } from '@/types';
import { taskService } from '@/services/api';

const TaskDetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [task, setTask] = useState<Task | null>(null);

  useEffect(() => {
    const fetchTask = async () => {
      try {
        const data = await taskService.getById(id || '');
        setTask(data);
      } catch (error) {
        console.error('Failed to fetch task:', error);
      } finally {
        setLoading(false);
      }
    };

    if (id) {
      fetchTask();
    }
  }, [id]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!task) {
    return (
      <Box sx={{ p: 3, textAlign: 'center' }}>
        <Typography variant="h6" gutterBottom>
          Task not found
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          The task you're looking for doesn't exist or has been removed.
        </Typography>
        <Button variant="contained" onClick={() => navigate('/tasks')}>
          Back to Task List
        </Button>
      </Box>
    );
  }

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

  return (
    <Box>
      <Typography variant="h4" gutterBottom sx={{ mb: 4 }}>
        {task.title}
      </Typography>

      <Grid container spacing={3}>
        {/* Task Description */}
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 3, mb: 3 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
              <Chip
                label={task.difficulty}
                color={getDifficultyColor(task.difficulty) as any}
                sx={{ mr: 2 }}
              />
              {task.category && (
                <Chip label={task.category} variant="outlined" size="small" />
              )}
            </Box>

            <Typography variant="body1" paragraph>
              {task.description}
            </Typography>

            <Box sx={{ mt: 4 }}>
              <Typography variant="h6" gutterBottom>
                Example
              </Typography>
              <Paper
                variant="outlined"
                sx={{
                  p: 2,
                  bgcolor: 'grey.50',
                  fontFamily: 'monospace',
                  fontSize: '0.875rem',
                }}
              >
                <Typography variant="body2" paragraph>
                  <strong>Input:</strong> nums = [2, 7, 11, 15], target = 9
                </Typography>
                <Typography variant="body2">
                  <strong>Output:</strong> [0, 1]
                </Typography>
                <Typography variant="body2" sx={{ mt: 1 }}>
                  <strong>Explanation:</strong> Because nums[0] + nums[1] == 9, we return [0, 1].
                </Typography>
              </Paper>
            </Box>

            <Box sx={{ mt: 4 }}>
              <Typography variant="h6" gutterBottom>
                Constraints
              </Typography>
              <Box component="ul" sx={{ pl: 2 }}>
                <li>2 {'<='} nums.length {'<='} 10^4</li>
                <li>-10^9 {'<='} nums[i] {'<='} 10^9</li>
                <li>-10^9 {'<='} target {'<='} 10^9</li>
                <li>Only one valid answer exists</li>
              </Box>
            </Box>
          </Paper>
        </Grid>

        {/* Action Panel */}
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3, mb: 3 }}>
            <Typography variant="h6" gutterBottom>
              Task Details
            </Typography>
            <Box sx={{ mt: 2 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <TimerIcon sx={{ mr: 2, color: 'text.secondary' }} />
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Time Limit
                  </Typography>
                  <Typography variant="body1">5 seconds</Typography>
                </Box>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <MemoryIcon sx={{ mr: 2, color: 'text.secondary' }} />
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Memory Limit
                  </Typography>
                  <Typography variant="body1">256 MB</Typography>
                </Box>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center' }}>
                <InfoIcon sx={{ mr: 2, color: 'text.secondary' }} />
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Test Cases
                  </Typography>
                  <Typography variant="body1">10 tests</Typography>
                </Box>
              </Box>
            </Box>
          </Paper>

          <Button
            variant="contained"
            size="large"
            fullWidth
            endIcon={<PlayArrowIcon />}
            onClick={() => navigate(`/submit/${id}`)}
            sx={{ mb: 2 }}
          >
            Start Coding
          </Button>

          <Card variant="outlined">
            <CardContent>
              <Typography variant="subtitle2" gutterBottom>
                Tips
              </Typography>
              <Typography variant="body2" color="text.secondary">
                • Think about edge cases first
                <br />
                • Consider time and space complexity
                <br />
                • Test with sample inputs
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default TaskDetail;
