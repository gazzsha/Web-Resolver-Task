import React, { useState } from 'react';
import {
  Box,
  Toolbar,
  IconButton,
  Typography,
  Avatar,
  Menu,
  MenuItem,
  Tooltip,
  ListItemIcon,
  ListItemText,
  Divider,
  alpha,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import Brightness4Icon from '@mui/icons-material/Brightness4';
import Brightness7Icon from '@mui/icons-material/Brightness7';
import LogoutIcon from '@mui/icons-material/Logout';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAppStore } from '@/store/appStore';
import { useAuthStore } from '@/store/authStore';
import { brand } from '@/theme/theme';

// Map routes to Russian titles
const routeTitles: Record<string, string> = {
  '/': 'Главная',
  '/tasks': 'Задачи',
  '/submissions': 'Мои решения',
  '/statistics': 'Статистика',
};

function getPageTitle(pathname: string): string {
  if (routeTitles[pathname]) return routeTitles[pathname];
  if (pathname.startsWith('/tasks/')) return 'Условие задачи';
  if (pathname.startsWith('/submit/')) return 'Редактор решений';
  if (pathname.startsWith('/results/')) return 'Результаты';
  return 'Web-Resolver-Task';
}

interface HeaderProps {
  mobileMenuButton?: React.ReactNode;
}

const Header: React.FC<HeaderProps> = ({ mobileMenuButton }) => {
  const { toggleSidebar, darkMode, toggleDarkMode } = useAppStore();
  const userEmail = useAuthStore((s) => s.user?.email);
  const username = useAuthStore((s) => s.username);
  const navigate = useNavigate();
  const location = useLocation();

  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const pageTitle = getPageTitle(location.pathname);

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };
  const handleMenuClose = () => setAnchorEl(null);

  const handleLogout = () => {
    handleMenuClose();
    useAuthStore.getState().logout();
    navigate('/login');
  };

  // Avatar initials: first 1-2 chars of username, fallback to first char of email
  const initials = username
    ? username.slice(0, 2).toUpperCase()
    : userEmail
    ? userEmail[0].toUpperCase()
    : '?';

  // Display label in header: username preferred, fallback to email
  const displayName = username ?? userEmail ?? null;

  return (
    <Toolbar
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        minHeight: { xs: 56, sm: 64 },
        px: { xs: 1.5, sm: 2.5 },
        gap: 1,
      }}
    >
      {/* Left: mobile hamburger OR desktop sidebar toggle + logo */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
        {mobileMenuButton ?? (
          <IconButton
            size="medium"
            onClick={toggleSidebar}
            sx={{ color: 'text.secondary', flexShrink: 0 }}
            aria-label="свернуть боковую панель"
          >
            <MenuIcon />
          </IconButton>
        )}

        {/* Logo mark */}
        <Box
          sx={{
            width: 32,
            height: 32,
            borderRadius: '8px',
            background: `linear-gradient(135deg, ${brand.indigo} 0%, ${brand.rose} 100%)`,
            display: { xs: 'none', sm: 'flex' },
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
            boxShadow: `0 4px 12px ${alpha(brand.indigo, 0.4)}`,
          }}
        >
          <Typography
            sx={{
              fontFamily: '"JetBrains Mono", monospace',
              fontWeight: 700,
              fontSize: 11,
              color: '#fff',
              lineHeight: 1,
            }}
          >
            {'</>'}
          </Typography>
        </Box>

        {/* Page title */}
        <Typography
          variant="h6"
          sx={{
            fontWeight: 600,
            fontSize: { xs: '0.95rem', sm: '1.05rem' },
            color: 'text.primary',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {pageTitle}
        </Typography>
      </Box>

      {/* Right: theme toggle + user */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexShrink: 0 }}>
        <Tooltip title={darkMode ? 'Светлая тема' : 'Тёмная тема'}>
          <IconButton
            onClick={toggleDarkMode}
            size="medium"
            sx={{ color: 'text.secondary' }}
            aria-label={darkMode ? 'Включить светлую тему' : 'Включить тёмную тему'}
          >
            {darkMode ? <Brightness7Icon fontSize="small" /> : <Brightness4Icon fontSize="small" />}
          </IconButton>
        </Tooltip>

        {userEmail && (
          <>
            <Tooltip title="Аккаунт">
              <IconButton
                onClick={handleMenuOpen}
                size="small"
                sx={{ ml: 0.5, display: 'flex', alignItems: 'center', gap: 1, borderRadius: 2, px: 1 }}
                aria-label="Меню пользователя"
              >
                <Avatar
                  sx={{
                    width: 34,
                    height: 34,
                    fontSize: '0.8rem',
                    fontWeight: 700,
                    bgcolor: 'primary.main',
                    color: 'primary.contrastText',
                    boxShadow: `0 2px 8px ${alpha(brand.indigo, 0.4)}`,
                    flexShrink: 0,
                  }}
                >
                  {initials}
                </Avatar>
                {displayName && (
                  <Typography
                    variant="body2"
                    sx={{
                      display: { xs: 'none', lg: 'block' },
                      color: 'text.primary',
                      fontWeight: 600,
                      maxWidth: 160,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {displayName}
                  </Typography>
                )}
              </IconButton>
            </Tooltip>

            <Menu
              anchorEl={anchorEl}
              open={Boolean(anchorEl)}
              onClose={handleMenuClose}
              transformOrigin={{ horizontal: 'right', vertical: 'top' }}
              anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
              PaperProps={{
                elevation: 4,
                sx: {
                  mt: 1,
                  minWidth: 220,
                  borderRadius: 2,
                  border: (t) =>
                    `1px solid ${t.palette.mode === 'dark' ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)'}`,
                },
              }}
            >
              <Box sx={{ px: 2, py: 1.5 }}>
                {username && (
                  <Typography variant="subtitle2" noWrap sx={{ fontWeight: 600 }}>
                    {username}
                  </Typography>
                )}
                <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
                  {userEmail}
                </Typography>
              </Box>
              <Divider />
              <MenuItem onClick={handleLogout} sx={{ gap: 1.5, py: 1.25 }}>
                <ListItemIcon sx={{ minWidth: 'auto' }}>
                  <LogoutIcon fontSize="small" color="error" />
                </ListItemIcon>
                <ListItemText primary="Выйти" primaryTypographyProps={{ color: 'error.main', fontWeight: 600 }} />
              </MenuItem>
            </Menu>
          </>
        )}
      </Box>
    </Toolbar>
  );
};

export default Header;
