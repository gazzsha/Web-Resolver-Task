import { useRef, useState } from 'react';
import { Navigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import DownloadIcon from '@mui/icons-material/Download';
import { adminService, type TaskImportResult } from '@/services/api';
import { useAuthStore } from '@/store/authStore';

const CSV_TEMPLATE = [
  'title,difficulty,category,description,return_type,arguments_json,tests_json',
  '"Sum","Easy","Math","Сумма двух целых","Integer",' +
    '"[{""position"":0,""type"":""Integer""},{""position"":1,""type"":""Integer""}]",' +
    '"[{""input"":""1 2"",""expectedOutput"":""3""},{""input"":""5 7"",""expectedOutput"":""12""}]"',
].join('\n');

const AdminImport = () => {
  const user = useAuthStore((s) => s.user);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<TaskImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  // UX-only redirect — never a security boundary. The actual access control is
  // enforced server-side by @PreAuthorize("hasRole('TEACHER')") on
  // TasksController.importTasksFromCsv. A student editing local state could
  // reach this page; the backend will still reject the request with 403.
  if (user && user.role !== 'TEACHER') {
    return <Navigate to="/" replace />;
  }

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0] ?? null;
    setFile(f);
    setResult(null);
    setError(null);
  };

  const downloadTemplate = () => {
    const blob = new Blob([CSV_TEMPLATE], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'tasks-template.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleImport = async () => {
    if (!file) return;
    setSubmitting(true);
    setError(null);
    try {
      const res = await adminService.importTasksFromCsv(file);
      setResult(res);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Не удалось импортировать файл';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box sx={{ width: '100%', maxWidth: 960 }}>
      <Typography variant="h4" component="h1" sx={{ fontWeight: 700, mb: 1 }}>
        Импорт задач из CSV
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        Загрузка задач преподавателем. Доступно только пользователям с ролью TEACHER.
      </Typography>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
              Формат файла
            </Typography>
            <Typography variant="body2" color="text.secondary">
              CSV с заголовком: <code>title,difficulty,category,description,return_type,arguments_json,tests_json</code>.
              Дубликаты по полю <code>title</code> пропускаются. Поле <code>category</code> необязательное.
            </Typography>
            <Box>
              <Button
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={downloadTemplate}
                aria-label="Скачать шаблон CSV"
              >
                Скачать шаблон
              </Button>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack spacing={2}>
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv,text/csv"
              onChange={handleFile}
              style={{ display: 'none' }}
              data-testid="csv-file-input"
            />
            <Stack direction="row" spacing={2} alignItems="center">
              <Button
                variant="outlined"
                startIcon={<UploadFileIcon />}
                onClick={() => fileInputRef.current?.click()}
                disabled={submitting}
              >
                Выбрать CSV-файл
              </Button>
              {file && (
                <Chip
                  label={`${file.name} · ${(file.size / 1024).toFixed(1)} KB`}
                  onDelete={() => {
                    setFile(null);
                    setResult(null);
                  }}
                  data-testid="selected-file-chip"
                />
              )}
            </Stack>
            <Box>
              <Button
                variant="contained"
                disabled={!file || submitting}
                onClick={handleImport}
                startIcon={submitting ? <CircularProgress size={16} color="inherit" /> : <UploadFileIcon />}
                data-testid="import-submit-button"
              >
                {submitting ? 'Импортируется…' : 'Импортировать'}
              </Button>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }} data-testid="import-error-alert">
          {error}
        </Alert>
      )}

      {result && (
        <Card data-testid="import-result-card">
          <CardContent>
            <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
              Результат
            </Typography>
            <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
              <Chip
                color="success"
                label={`Импортировано: ${result.importedCount}`}
                data-testid="import-success-chip"
              />
              <Chip
                color={result.skippedCount > 0 ? 'warning' : 'default'}
                label={`Пропущено: ${result.skippedCount}`}
              />
              <Chip
                color={result.errors.length > 0 ? 'error' : 'default'}
                label={`Ошибок: ${result.errors.length}`}
              />
            </Stack>

            {result.errors.length > 0 && (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell width={80}>Строка</TableCell>
                      <TableCell>Сообщение</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {result.errors.map((err) => (
                      <TableRow key={`${err.line}-${err.message}`}>
                        <TableCell>{err.line}</TableCell>
                        <TableCell>{err.message}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </CardContent>
        </Card>
      )}
    </Box>
  );
};

export default AdminImport;
