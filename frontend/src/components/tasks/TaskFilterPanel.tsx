import React from 'react';
import {
  Box,
  Typography,
  TextField,
  InputAdornment,
  ToggleButton,
  ToggleButtonGroup,
  Button,
  Autocomplete,
  alpha,
  useTheme,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import FilterListIcon from '@mui/icons-material/FilterList';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import { brand } from '@/theme/theme';

type Difficulty = 'all' | 'Easy' | 'Medium' | 'Hard';
type SolvedStatus = 'all' | 'solved' | 'unsolved';

export interface TaskFilterPanelProps {
  search: string;
  onSearchChange: (v: string) => void;
  difficulty: Difficulty;
  onDifficultyChange: (v: Difficulty) => void;
  solvedStatus: SolvedStatus;
  onSolvedStatusChange: (v: SolvedStatus) => void;
  category: string | null;
  onCategoryChange: (v: string | null) => void;
  categories: string[];
  onReset: () => void;
  dirty: boolean;
}

const TaskFilterPanel: React.FC<TaskFilterPanelProps> = ({
  search,
  onSearchChange,
  difficulty,
  onDifficultyChange,
  solvedStatus,
  onSolvedStatusChange,
  category,
  onCategoryChange,
  categories,
  onReset,
  dirty,
}) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  const toggleSx = {
    '& .MuiToggleButton-root': {
      borderRadius: '6px !important',
      px: 1,
      py: 0.4,
      fontSize: '0.72rem',
      fontWeight: 600,
      border: `1px solid ${isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.12)'} !important`,
      mx: '2px',
      '&.Mui-selected': {
        background: alpha(brand.indigo, isDark ? 0.2 : 0.1),
        color: 'primary.main',
        borderColor: `${alpha(brand.indigo, 0.4)} !important`,
      },
    },
  };

  return (
    <Box
      sx={{
        width: 240,
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        position: 'sticky',
        top: 80,
        alignSelf: 'flex-start',
        maxHeight: 'calc(100vh - 96px)',
        overflowY: 'auto',
        pr: 0.5,
        '&::-webkit-scrollbar': { width: '3px' },
        '&::-webkit-scrollbar-track': { background: 'transparent' },
        '&::-webkit-scrollbar-thumb': {
          background: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)',
          borderRadius: '2px',
        },
      }}
    >
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
          <FilterListIcon fontSize="small" sx={{ color: 'text.secondary' }} />
          <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
            Фильтры
          </Typography>
        </Box>
        {dirty && (
          <Button
            size="small"
            variant="text"
            startIcon={<RestartAltIcon fontSize="small" />}
            onClick={onReset}
            sx={{ fontSize: '0.72rem', py: 0.25, px: 0.75, minWidth: 0 }}
          >
            Сброс
          </Button>
        )}
      </Box>

      {/* Search */}
      <TextField
        placeholder="Поиск..."
        value={search}
        onChange={(e) => onSearchChange(e.target.value)}
        size="small"
        fullWidth
        sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon fontSize="small" sx={{ color: 'text.disabled' }} />
            </InputAdornment>
          ),
        }}
      />

      {/* Difficulty filter */}
      <Box>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75, fontWeight: 600 }}>
          Сложность
        </Typography>
        <ToggleButtonGroup
          value={difficulty}
          exclusive
          onChange={(_, v: Difficulty | null) => v !== null && onDifficultyChange(v)}
          size="small"
          orientation="vertical"
          fullWidth
          sx={{
            '& .MuiToggleButton-root': {
              borderRadius: '6px !important',
              py: 0.5,
              fontSize: '0.78rem',
              fontWeight: 600,
              border: `1px solid ${isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.12)'} !important`,
              mb: '3px',
              justifyContent: 'flex-start',
              pl: 1.5,
              '&.Mui-selected': {
                background: alpha(brand.indigo, isDark ? 0.2 : 0.1),
                color: 'primary.main',
                borderColor: `${alpha(brand.indigo, 0.4)} !important`,
              },
            },
          }}
        >
          <ToggleButton value="all">Все</ToggleButton>
          <ToggleButton value="Easy">Лёгкие</ToggleButton>
          <ToggleButton value="Medium">Средние</ToggleButton>
          <ToggleButton value="Hard">Сложные</ToggleButton>
        </ToggleButtonGroup>
      </Box>

      {/* Solved status filter */}
      <Box>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75, fontWeight: 600 }}>
          Статус решения
        </Typography>
        <ToggleButtonGroup
          value={solvedStatus}
          exclusive
          onChange={(_, v: SolvedStatus | null) => v !== null && onSolvedStatusChange(v)}
          size="small"
          orientation="vertical"
          fullWidth
          sx={toggleSx}
        >
          <ToggleButton value="all" sx={{ '&': { borderRadius: '6px !important', justifyContent: 'flex-start', pl: 1.5, mb: '3px' } }}>
            Все
          </ToggleButton>
          <ToggleButton value="solved" sx={{ '&': { borderRadius: '6px !important', justifyContent: 'flex-start', pl: 1.5, mb: '3px' } }}>
            Решённые
          </ToggleButton>
          <ToggleButton value="unsolved" sx={{ '&': { borderRadius: '6px !important', justifyContent: 'flex-start', pl: 1.5 } }}>
            Нерешённые
          </ToggleButton>
        </ToggleButtonGroup>
      </Box>

      {/* Category autocomplete */}
      <Box>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75, fontWeight: 600 }}>
          Категория
        </Typography>
        <Autocomplete
          options={categories}
          value={category}
          onChange={(_e, v) => onCategoryChange(v)}
          size="small"
          freeSolo={false}
          renderInput={(params) => (
            <TextField
              {...params}
              placeholder="Все категории"
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}
            />
          )}
        />
      </Box>
    </Box>
  );
};

export default TaskFilterPanel;
