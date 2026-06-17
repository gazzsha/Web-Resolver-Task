import { useParams } from 'react-router-dom';
import { Box, Alert } from '@mui/material';
import ResultsView from '@/components/results/ResultsView';

const Results = () => {
  const { id } = useParams<{ id: string }>();

  if (!id) {
    return (
      <Box sx={{ maxWidth: 640, mx: 'auto', mt: 6 }}>
        <Alert severity="error">Идентификатор результата не указан.</Alert>
      </Box>
    );
  }

  return <ResultsView submissionId={id} embedded={false} />;
};

export default Results;
