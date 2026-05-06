import React from 'react';
import {
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Box,
  Typography,
} from '@mui/material';
import { useNavigate, useLocation } from 'react-router-dom';
import DashboardIcon from '@mui/icons-material/Dashboard';
import CodeIcon from '@mui/icons-material/Code';
import HistoryIcon from '@mui/icons-material/History';
import BarChartIcon from '@mui/icons-material/BarChart';
import { brand } from '@/theme/theme';

const menuItems = [
  {
    label: 'Главная',
    icon: DashboardIcon,
    path: '/',
    exact: true,
  },
  {
    label: 'Задачи',
    icon: CodeIcon,
    path: '/tasks',
    exact: false,
  },
  {
    label: 'Мои решения',
    icon: HistoryIcon,
    path: '/submissions',
    exact: false,
  },
  {
    label: 'Статистика',
    icon: BarChartIcon,
    path: '/statistics',
    exact: false,
  },
];

interface SidebarProps {
  onNavigate?: () => void;
}

const Sidebar: React.FC<SidebarProps> = ({ onNavigate }) => {
  const navigate = useNavigate();
  const location = useLocation();

  const isActive = (path: string, exact: boolean) => {
    if (exact) return location.pathname === path;
    return location.pathname === path || location.pathname.startsWith(path + '/');
  };

  const handleNav = (path: string) => {
    navigate(path);
    onNavigate?.();
  };

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        py: 1,
      }}
    >
      {/* Nav links */}
      <List sx={{ px: 0, flexGrow: 1 }}>
        {menuItems.map((item) => {
          const active = isActive(item.path, item.exact);
          const Icon = item.icon;

          return (
            <ListItem key={item.label} disablePadding sx={{ display: 'block' }}>
              <ListItemButton
                selected={active}
                onClick={() => handleNav(item.path)}
                sx={{
                  mx: '8px',
                  my: '2px',
                  borderRadius: '10px',
                  width: 'calc(100% - 16px)',
                  position: 'relative',
                  overflow: 'hidden',
                  ...(active && {
                    '&::before': {
                      content: '""',
                      position: 'absolute',
                      left: 0,
                      top: '25%',
                      bottom: '25%',
                      width: 3,
                      borderRadius: '0 3px 3px 0',
                      background: `linear-gradient(180deg, ${brand.indigo} 0%, ${brand.rose} 100%)`,
                    },
                  }),
                }}
              >
                <ListItemIcon
                  sx={{
                    minWidth: 38,
                    color: active ? 'primary.main' : 'text.secondary',
                    transition: 'color 0.15s',
                  }}
                >
                  <Icon fontSize="small" />
                </ListItemIcon>
                <ListItemText
                  primary={item.label}
                  primaryTypographyProps={{
                    fontSize: '0.875rem',
                    fontWeight: active ? 700 : 500,
                    color: active ? 'primary.main' : 'text.primary',
                    noWrap: true,
                  }}
                />
              </ListItemButton>
            </ListItem>
          );
        })}
      </List>

      {/* Footer version tag */}
      <Box sx={{ px: 2.5, pb: 2.5 }}>
        <Typography
          variant="caption"
          sx={{
            color: 'text.disabled',
            fontFamily: '"JetBrains Mono", monospace',
            fontSize: '0.65rem',
            display: 'block',
          }}
        >
          v1.0 · web-resolver-task
        </Typography>
      </Box>
    </Box>
  );
};

export default Sidebar;
