import React, { useState } from 'react';
import { Box, Drawer, AppBar, Toolbar, CssBaseline, useMediaQuery, useTheme, IconButton } from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import { useAppStore } from '@/store/appStore';
import Header from './Header';
import Sidebar from './Sidebar';

const DRAWER_WIDTH = 256;

interface LayoutProps {
  children: React.ReactNode;
}

const Layout: React.FC<LayoutProps> = ({ children }) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const { sidebarOpen } = useAppStore();
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleMobileToggle = () => setMobileOpen((prev) => !prev);

  // Desktop: permanent drawer toggled by sidebarOpen
  // Mobile: temporary drawer toggled by mobileOpen
  const desktopOpen = !isMobile && sidebarOpen;
  const effectiveDrawerWidth = desktopOpen ? DRAWER_WIDTH : 0;

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <CssBaseline />

      {/* AppBar */}
      <AppBar
        position="fixed"
        elevation={0}
        sx={{
          zIndex: (t) => t.zIndex.drawer + 1,
          // adaptive width on desktop
          width: { md: `calc(100% - ${effectiveDrawerWidth}px)` },
          ml: { md: `${effectiveDrawerWidth}px` },
          transition: theme.transitions.create(['width', 'margin'], {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.leavingScreen,
          }),
        }}
      >
        <Header
          mobileMenuButton={
            isMobile ? (
              <IconButton
                size="large"
                edge="start"
                color="inherit"
                onClick={handleMobileToggle}
                sx={{ mr: 1, color: 'text.primary' }}
                aria-label="открыть меню"
              >
                <MenuIcon />
              </IconButton>
            ) : null
          }
        />
      </AppBar>

      {/* Mobile Drawer */}
      {isMobile && (
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleMobileToggle}
          ModalProps={{ keepMounted: true }}
          sx={{
            '& .MuiDrawer-paper': {
              width: DRAWER_WIDTH,
              boxSizing: 'border-box',
            },
          }}
        >
          <Toolbar />
          <Sidebar onNavigate={() => setMobileOpen(false)} />
        </Drawer>
      )}

      {/* Desktop Drawer */}
      {!isMobile && (
        <Drawer
          variant="permanent"
          open={desktopOpen}
          sx={{
            width: effectiveDrawerWidth,
            flexShrink: 0,
            '& .MuiDrawer-paper': {
              width: DRAWER_WIDTH,
              boxSizing: 'border-box',
              transform: desktopOpen ? 'none' : `translateX(-${DRAWER_WIDTH}px)`,
              visibility: desktopOpen ? 'visible' : 'hidden',
              overflowX: 'hidden',
              transition: theme.transitions.create(['transform', 'visibility', 'width'], {
                easing: theme.transitions.easing.sharp,
                duration: theme.transitions.duration.standard,
              }),
            },
          }}
        >
          <Toolbar />
          <Sidebar />
        </Drawer>
      )}

      {/* Main content */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          minWidth: 0,
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          transition: theme.transitions.create(['margin', 'width'], {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.standard,
          }),
        }}
      >
        <Toolbar />
        <Box
          sx={{
            flexGrow: 1,
            p: { xs: 2, sm: 3 },
            width: '100%',
            maxWidth: '100%',
          }}
        >
          {children}
        </Box>
      </Box>
    </Box>
  );
};

export default Layout;
