import { Box, Typography, Button } from '@mui/material';
import { useNavigate } from 'react-router-dom';

const NotFoundPage = () => {
  const navigate = useNavigate();

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '60vh',
        textAlign: 'center',
        gap: 2,
      }}
    >
      <Typography
        variant="h1"
        component="div"
        aria-hidden="true"
        sx={{ fontFamily: '"JetBrains Mono", monospace', fontWeight: 700, color: 'text.secondary', fontSize: '6rem' }}
      >
        404
      </Typography>
      <Typography variant="h4" component="h1" sx={{ fontWeight: 700 }}>
        Страница не найдена
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 400 }}>
        Запрошенная страница не существует. Проверьте адрес или вернитесь на главную.
      </Typography>
      <Button variant="contained" size="large" onClick={() => navigate('/')}>
        На главную
      </Button>
    </Box>
  );
};

export default NotFoundPage;
