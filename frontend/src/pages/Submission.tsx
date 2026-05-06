import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Box, useTheme, useMediaQuery } from '@mui/material';
import type { OnMount } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import { submissionService, taskService } from '@/services/api';
import type { Task } from '@/types';
import { toast } from 'react-toastify';
import TaskPanel from '@/components/submission/TaskPanel';
import EditorPane from '@/components/submission/EditorPane';

type Language = 'java' | 'kotlin' | 'python';

function getDefaultCode(lang: Language): string {
  switch (lang) {
    case 'java':
      return `import java.util.Scanner;

public class Solution {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Читаем данные из stdin
        int a = scanner.nextInt();
        int b = scanner.nextInt();

        // Решение
        System.out.println(a + b);
    }
}`;
    case 'kotlin':
      return `import java.util.Scanner

fun main() {
    val scanner = Scanner(System.in)

    // Читаем данные из stdin
    val a = scanner.nextInt()
    val b = scanner.nextInt()

    // Решение
    println(a + b)
}`;
    case 'python':
      return `# Читаем данные из stdin
a, b = map(int, input().split())

# Решение
print(a + b)`;
    default:
      return '';
  }
}

const Submission = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  // md breakpoint — below this we stack vertically (mobile layout)
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));

  const prefilled = location.state as { prefilledCode?: string; language?: Language } | null;
  const initialLang: Language = prefilled?.language ?? 'java';

  const [code, setCode] = useState<string>(
    prefilled?.prefilledCode ?? getDefaultCode(initialLang)
  );
  const [language, setLanguage] = useState<Language>(initialLang);
  const [submitting, setSubmitting] = useState(false);
  const [task, setTask] = useState<Task | null>(null);
  const [taskLoading, setTaskLoading] = useState(true);

  // Keep a ref to the monaco editor instance for keyboard shortcut registration
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
  // Keep submit handler in ref so the keyboard shortcut always calls the current version
  const submitHandlerRef = useRef<() => void>(() => undefined);

  useEffect(() => {
    if (!id) {
      setTaskLoading(false);
      return;
    }
    setTaskLoading(true);
    taskService
      .getById(id)
      .then(setTask)
      .catch(() => setTask(null))
      .finally(() => setTaskLoading(false));
  }, [id]);

  const handleLanguageChange = useCallback((newLang: Language) => {
    setLanguage(newLang);
    // Only apply template when there is no prefilled code (fresh session)
    // and when the user has not already customised the code beyond the current template.
    setCode(getDefaultCode(newLang));
  }, []);

  const handleResetCode = useCallback(() => {
    setCode(getDefaultCode(language));
  }, [language]);

  const handleSubmit = useCallback(async () => {
    if (!code.trim() || submitting) return;
    setSubmitting(true);
    try {
      const result = await submissionService.submit({
        testId: id ?? '',
        code,
        language,
      });
      toast.success('Решение отправлено! Ожидайте результатов...');
      navigate(`/results/${result.id}`);
    } catch (error: unknown) {
      console.error('Submission error:', error);
      const axiosError = error as { response?: { data?: { message?: string } } };
      toast.error(axiosError.response?.data?.message ?? 'Ошибка при отправке решения');
    } finally {
      setSubmitting(false);
    }
  }, [code, id, language, navigate, submitting]);

  // Keep the ref in sync so the monaco command always uses the latest closure
  submitHandlerRef.current = handleSubmit;

  // Register ⌘+Enter / Ctrl+Enter shortcut inside Monaco
  const handleEditorMount: OnMount = useCallback((editor) => {
    editorRef.current = editor;
    editor.addCommand(
      // KeyMod.CtrlCmd is platform-aware (Cmd on Mac, Ctrl on Win/Linux)
      monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter,
      () => submitHandlerRef.current()
    );
  }, []);

  // ─────────────────────────────────────────────────────────────────
  //  Layout: full-viewport split-pane (desktop) / stacked (mobile)
  //
  //  The outer box escapes Layout's padding via negative margins so
  //  the editor background extends to the viewport edge.
  //  Height is viewport minus the global AppBar (64px).
  // ─────────────────────────────────────────────────────────────────

  return (
    <Box
      sx={{
        // Escape Layout's padding (xs: 16px, sm: 24px each side)
        mx: { xs: -2, sm: -3 },
        mt: { xs: -2, sm: -3 },
        mb: { xs: -2, sm: -3 },
        // Compensate width for the horizontal margins we removed
        width: { xs: 'calc(100% + 32px)', sm: 'calc(100% + 48px)' },
        // Full viewport minus AppBar height
        height: { xs: 'auto', md: 'calc(100vh - 64px)' },
        // On mobile we let content grow naturally; on desktop clip overflow
        overflow: { xs: 'visible', md: 'hidden' },
        display: 'flex',
        flexDirection: { xs: 'column', md: 'row' },
      }}
    >
      {/* ── LEFT PANE: Task description ─────────────────────────── */}
      <Box
        sx={{
          // Desktop: fixed 38% width, never shrinks below 280px
          flex: { xs: '0 0 auto', md: '0 0 38%' },
          minWidth: { md: 280 },
          maxWidth: { md: 480 },
          // Mobile: collapsible max-height so description doesn't eat all screen
          maxHeight: { xs: 'none', md: '100%' },
          height: { md: '100%' },
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <TaskPanel
          task={task}
          loading={taskLoading}
          onBack={() => navigate(-1)}
          onResetCode={handleResetCode}
        />
      </Box>

      {/* ── RIGHT PANE: Editor ───────────────────────────────────── */}
      <Box
        sx={{
          flex: 1,
          minWidth: 0,
          // On desktop the pane fills remaining height; on mobile give editor breathing room
          height: { xs: 'auto', md: '100%' },
          minHeight: { xs: 0, md: 0 },
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        {isDesktop ? (
          // Desktop: EditorPane manages its own full height
          <EditorPane
            code={code}
            language={language}
            submitting={submitting}
            onCodeChange={setCode}
            onLanguageChange={handleLanguageChange}
            onSubmit={handleSubmit}
            onEditorMount={handleEditorMount}
          />
        ) : (
          // Mobile: wrap in a box that constrains the editor height floor
          <Box
            sx={{
              display: 'flex',
              flexDirection: 'column',
              // Give enough height so Monaco is usable without the split-pane
              minHeight: '65vh',
              flex: '0 0 auto',
            }}
          >
            <EditorPane
              code={code}
              language={language}
              submitting={submitting}
              onCodeChange={setCode}
              onLanguageChange={handleLanguageChange}
              onSubmit={handleSubmit}
              onEditorMount={handleEditorMount}
            />
          </Box>
        )}
      </Box>
    </Box>
  );
};

export default Submission;
