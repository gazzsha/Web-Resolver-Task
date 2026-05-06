import { useState } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import {
  Box,
  Typography,
  Button,
  Grid,
  Paper,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Alert,
  CircularProgress,
} from '@mui/material';
import Editor from '@monaco-editor/react';
import SendIcon from '@mui/icons-material/Send';
import { submissionService } from '@/services/api';
import { toast } from 'react-toastify';

const Submission = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const prefilled = location.state as { prefilledCode?: string; language?: 'java' | 'kotlin' | 'python' } | null;
  const initialLang: 'java' | 'kotlin' | 'python' = prefilled?.language ?? 'java';
  const [code, setCode] = useState<string>(prefilled?.prefilledCode ?? getDefaultCode(initialLang));
  const [language, setLanguage] = useState<'java' | 'kotlin' | 'python'>(initialLang);
  const [submitting, setSubmitting] = useState(false);

  function getDefaultCode(lang: string): string {
    switch (lang) {
      case 'java':
        return `import java.util.Scanner;

public class Solution {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        // Read input from stdin
        int a = scanner.nextInt();
        int b = scanner.nextInt();
        
        // Solve the problem
        int result = sum(a, b);
        
        // Print result to stdout
        System.out.println(result);
    }
    
    public static int sum(int a, int b) {
        return a + b;
    }
}`;
      case 'kotlin':
        return `import java.util.Scanner

fun main() {
    val scanner = Scanner(System.in)
    
    // Read input from stdin
    val a = scanner.nextInt()
    val b = scanner.nextInt()
    
    // Solve the problem
    val result = sum(a, b)
    
    // Print result to stdout
    println(result)
}

fun sum(a: Int, b: Int): Int {
    return a + b
}`;
      case 'python':
        return `# Read input from stdin
a, b = map(int, input().split())

# Solve the problem
result = sum(a, b)

# Print result to stdout
print(result)

def sum(a, b):
    return a + b`;
      default:
        return '';
    }
  }

  const handleLanguageChange = (newLang: 'java' | 'kotlin' | 'python') => {
    setLanguage(newLang);
    setCode(getDefaultCode(newLang));
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      const result = await submissionService.submit({
        testId: id || '',
        code,
        language,
      });
      toast.success('Submission successful! Waiting for results...');
      // Navigate to results page with taskId (returned as id)
      navigate(`/results/${result.id}`);
    } catch (error: any) {
      console.error('Submission error:', error);
      toast.error(error.response?.data?.message || 'Submission failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom sx={{ mb: 4 }}>
        Submit Solution
      </Typography>

      <Grid container spacing={3}>
        {/* Code Editor */}
        <Grid item xs={12} md={8}>
          <Paper sx={{ height: 'calc(100vh - 200px)', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
              <FormControl size="small" sx={{ minWidth: 150 }}>
                <InputLabel>Language</InputLabel>
                <Select
                  value={language}
                  label="Language"
                  onChange={(e) => handleLanguageChange(e.target.value as any)}
                >
                  <MenuItem value="java">Java</MenuItem>
                  <MenuItem value="kotlin">Kotlin</MenuItem>
                  <MenuItem value="python">Python</MenuItem>
                </Select>
              </FormControl>
            </Box>
            <Box sx={{ flexGrow: 1 }}>
              <Editor
                height="100%"
                defaultLanguage={language}
                language={language}
                value={code}
                onChange={(value) => setCode(value || '')}
                theme="vs-dark"
                options={{
                  minimap: { enabled: false },
                  fontSize: 14,
                  automaticLayout: true,
                  scrollBeyondLastLine: false,
                  padding: { top: 16 },
                }}
              />
            </Box>
          </Paper>
        </Grid>

        {/* Action Panel */}
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Task Instructions
            </Typography>
            <Alert severity="info" sx={{ mb: 3 }}>
              Write a complete program that reads from stdin and writes to stdout. 
              Your code must include a main method.
            </Alert>

            <Typography variant="subtitle2" gutterBottom>
              Requirements:
            </Typography>
            <Box component="ul" sx={{ pl: 2, mb: 3 }}>
              <li>Include all necessary imports</li>
              <li>Must have a main method</li>
              <li>Read input from stdin (Scanner, BufferedReader, etc.)</li>
              <li>Print output to stdout (System.out.println)</li>
              <li>Class name should be "Solution"</li>
            </Box>

            <Typography variant="subtitle2" gutterBottom>
              Constraints:
            </Typography>
            <Box component="ul" sx={{ pl: 2, mb: 3 }}>
              <li>Time limit: 10 seconds</li>
              <li>Memory limit: 256 MB</li>
              <li>All test cases must pass</li>
            </Box>

            <Button
              variant="contained"
              size="large"
              fullWidth
              endIcon={<SendIcon />}
              onClick={handleSubmit}
              disabled={submitting || !code.trim()}
              sx={{ mb: 2 }}
            >
              {submitting ? <CircularProgress size={24} /> : 'Submit Solution'}
            </Button>

            <Button variant="outlined" size="large" fullWidth onClick={() => navigate(-1)}>
              Back to Tasks
            </Button>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default Submission;
