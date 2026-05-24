import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Box, useTheme, useMediaQuery } from '@mui/material';
import type { OnMount } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import { submissionService, taskService, meService } from '@/services/api';
import type { Task, SubmissionResult, SubmissionSummary } from '@/types';
import { toast } from 'react-toastify';
import { useAppStore } from '@/store/appStore';
import TaskPanel from '@/components/submission/TaskPanel';
import EditorPane from '@/components/submission/EditorPane';
import SubmissionsListPanel from '@/components/submission/SubmissionsListPanel';
import SubmissionDetailDrawer from '@/components/submission/SubmissionDetailDrawer';
import Splitter from '@/components/submission/Splitter';
import { useSubmissionPolling } from '@/hooks/useSubmissionPolling';

// P0-1: Kotlin исключён из MVP (см. EditorPane.tsx).
type Language = 'java' | 'python';
type SubmitState = 'idle' | 'submitting' | 'polling' | 'result';

// Resizable splitter bounds — single source of truth for both
// localStorage validation and the <Splitter /> component props.
// Defaults bias toward the editor (more code space, narrower task panel and
// compact submissions strip) — per Boss feedback: emphasis on code input.
const TASK_PANEL_WIDTH = { min: 240, max: 520, default: 300 } as const;
const LIST_PANEL_HEIGHT = { min: 100, max: 420, default: 160 } as const;

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
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));

  // ── Focus mode: collapse Layout sidebar on enter, restore on leave.
  // Submission is an IDE-like surface — every horizontal pixel matters for the editor.
  const setSidebarOpen = useAppStore((s) => s.setSidebarOpen);
  useEffect(() => {
    const wasOpen = useAppStore.getState().sidebarOpen;
    setSidebarOpen(false);
    return () => {
      // Restore previous state on unmount
      setSidebarOpen(wasOpen);
    };
  }, [setSidebarOpen]);

  // Resizable panel sizes — persisted in localStorage. Bounds use TASK_PANEL_WIDTH/LIST_PANEL_HEIGHT
  // constants so the <Splitter /> component and the validator agree.
  const [taskPanelWidth, setTaskPanelWidth] = useState<number>(() => {
    const v = Number(localStorage.getItem('submit:taskPanelWidth'));
    return Number.isFinite(v) && v >= TASK_PANEL_WIDTH.min && v <= TASK_PANEL_WIDTH.max
      ? v
      : TASK_PANEL_WIDTH.default;
  });
  const [listPanelHeight, setListPanelHeight] = useState<number>(() => {
    const v = Number(localStorage.getItem('submit:listPanelHeight'));
    return Number.isFinite(v) && v >= LIST_PANEL_HEIGHT.min && v <= LIST_PANEL_HEIGHT.max
      ? v
      : LIST_PANEL_HEIGHT.default;
  });

  const prefilled = location.state as { prefilledCode?: string; language?: Language } | null;
  const initialLang: Language = prefilled?.language ?? 'java';

  const [code, setCode] = useState<string>(
    prefilled?.prefilledCode ?? getDefaultCode(initialLang)
  );
  const [language, setLanguage] = useState<Language>(initialLang);
  const [task, setTask] = useState<Task | null>(null);
  const [taskLoading, setTaskLoading] = useState(true);

  // 4-state machine
  const [submitState, setSubmitState] = useState<SubmitState>('idle');
  const [currentSubmissionId, setCurrentSubmissionId] = useState<string | null>(null);

  // Submissions list for this task
  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([]);

  // Drawer state
  const [selectedSubmissionId, setSelectedSubmissionId] = useState<string | null>(null);
  // Ref for returning focus to the row that opened the drawer
  const returnFocusRef = useRef<HTMLElement | null>(null);

  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
  const submitHandlerRef = useRef<() => void>(() => undefined);

  // ── Load task ──
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

  // ── Load existing submissions for this task ──
  const loadSubmissions = useCallback(() => {
    if (!id) return;
    meService.getSubmissions().then((all) => {
      const filtered = all
        .filter((s) => s.taskId === id)
        .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      setSubmissions(filtered);
    }).catch(() => {
      // Non-critical — submissions list may be empty
    });
  }, [id]);

  useEffect(() => {
    loadSubmissions();
  }, [loadSubmissions]);

  // ── Polling hook ──
  const pollingResult = useSubmissionPolling(
    submitState === 'polling' ? currentSubmissionId : null
  );

  // Patch in-place when polling resolves to terminal status OR times out / errors out
  useEffect(() => {
    if (!currentSubmissionId || submitState !== 'polling') return;

    const TERMINAL = new Set<SubmissionSummary['status']>(['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'ERROR']);

    if (pollingResult.status && TERMINAL.has(pollingResult.status)) {
      setSubmitState('result');
      setSubmissions((prev) =>
        prev.map((s) =>
          s.id === currentSubmissionId
            ? {
                ...s,
                status: pollingResult.status!,
                passedTests: pollingResult.passedTests,
                totalTests: pollingResult.totalTests,
              }
            : s
        )
      );
      return;
    }

    // Polling exhausted (120s timeout or fatal HTTP error other than 404).
    // Mark the optimistic row as ERROR and exit polling state — UI must not hang on PENDING.
    if (pollingResult.timedOut) {
      setSubmitState('result');
      setSubmissions((prev) =>
        prev.map((s) =>
          s.id === currentSubmissionId ? { ...s, status: 'ERROR' as const } : s
        )
      );
      toast.error('Не удалось получить результат. Попробуйте отправить решение ещё раз.');
    }
  }, [pollingResult.status, pollingResult.passedTests, pollingResult.totalTests, pollingResult.timedOut, currentSubmissionId, submitState]);

  const handleLanguageChange = useCallback((newLang: Language) => {
    setLanguage(newLang);
    setCode(getDefaultCode(newLang));
  }, []);

  const handleResetCode = useCallback(() => {
    setCode(getDefaultCode(language));
  }, [language]);

  const handleSubmit = useCallback(async () => {
    if (!code.trim() || submitState === 'submitting' || submitState === 'polling') return;
    setSubmitState('submitting');
    try {
      const result = await submissionService.submit({
        testId: id ?? '',
        code,
        language,
      });
      toast.success('Решение отправлено! Ожидайте результатов...');
      try {
        sessionStorage.setItem(
          `submission:${result.id}`,
          JSON.stringify({ code, language })
        );
      } catch (_e) {
        // SessionStorage may be unavailable in private mode — non-critical
      }

      // Optimistic row at top
      const optimisticRow: SubmissionSummary = {
        id: result.id,
        taskId: id ?? '',
        taskTitle: task?.title ?? null,
        status: 'PENDING',
        language,
        passedTests: null,
        totalTests: null,
        createdAt: new Date().toISOString(),
      };
      setSubmissions((prev) => [optimisticRow, ...prev]);
      setCurrentSubmissionId(result.id);
      setSubmitState('polling');
    } catch (error: unknown) {
      console.error('Submission error:', error);
      const axiosError = error as { response?: { data?: { message?: string } } };
      toast.error(axiosError.response?.data?.message ?? 'Ошибка при отправке решения');
      setSubmitState('idle');
    }
  }, [code, id, language, submitState, task]);

  // Keep the ref in sync via effect (not during render — React 18 strict mode safe).
  useEffect(() => {
    submitHandlerRef.current = handleSubmit;
  }, [handleSubmit]);

  const handleEditorMount: OnMount = useCallback((editor) => {
    editorRef.current = editor;
    editor.addCommand(
      monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter,
      () => submitHandlerRef.current()
    );
  }, []);

  const handleResultReceived = useCallback((_result: SubmissionResult) => {
    setSubmitState('result');
  }, []);

  // Suppress unused warning — handleResultReceived is used as onResultReceived on mobile ResultsView
  void handleResultReceived;

  // Persist splitter sizes to localStorage
  useEffect(() => {
    localStorage.setItem('submit:taskPanelWidth', String(taskPanelWidth));
  }, [taskPanelWidth]);

  useEffect(() => {
    localStorage.setItem('submit:listPanelHeight', String(listPanelHeight));
  }, [listPanelHeight]);

  return (
    <>
      <Box
        sx={{
          // IDE-like full-viewport fixed layout on desktop.
          // On md+ we ignore the page wrapper's padding entirely via fixed positioning.
          // On xs/sm we keep the static flow and compensate paddings via negative margins.
          position: { xs: 'static', md: 'fixed' },
          top: { md: 64 },
          left: { md: 0 },
          right: { md: 0 },
          bottom: { md: 0 },
          mx: { xs: -2, sm: -3, md: 0 },
          mt: { xs: -2, sm: -3, md: 0 },
          mb: { xs: -2, sm: -3, md: 0 },
          width: { xs: 'calc(100% + 32px)', sm: 'calc(100% + 48px)', md: 'auto' },
          height: { xs: 'auto', md: 'auto' },
          overflow: { xs: 'visible', md: 'hidden' },
          display: 'flex',
          flexDirection: { xs: 'column', md: 'row' },
          bgcolor: 'background.default',
          zIndex: 1,
        }}
      >
        {/* ── LEFT PANE: Task description ──────────────────────────── */}
        <Box
          sx={{
            flex: { xs: '0 0 auto', md: `0 0 ${taskPanelWidth}px` },
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

        {/* ── Vertical Splitter: between task panel and right column ── */}
        {isDesktop && (
          <Splitter
            orientation="vertical"
            size={taskPanelWidth}
            min={TASK_PANEL_WIDTH.min}
            max={TASK_PANEL_WIDTH.max}
            onChange={setTaskPanelWidth}
            ariaLabel="Изменить ширину панели задачи"
          />
        )}

        {/* ── RIGHT COLUMN: Editor + Submissions list ──────────────── */}
        <Box
          sx={{
            flex: 1,
            minWidth: 0,
            height: { xs: 'auto', md: '100%' },
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          {/* EditorPane — grows to fill */}
          <Box
            sx={{
              flex: 1,
              minHeight: { xs: '65vh', md: 0 },
              display: 'flex',
              flexDirection: 'column',
              overflow: 'hidden',
            }}
          >
            <EditorPane
              code={code}
              language={language}
              submitState={submitState}
              onCodeChange={setCode}
              onLanguageChange={handleLanguageChange}
              onSubmit={handleSubmit}
              onEditorMount={isDesktop ? handleEditorMount : undefined}
              onNavigateToTasks={() => navigate('/tasks')}
            />
          </Box>

          {/* ── Horizontal Splitter: between editor and submissions list ── */}
          {isDesktop && (
            <Splitter
              orientation="horizontal"
              size={listPanelHeight}
              min={LIST_PANEL_HEIGHT.min}
              max={LIST_PANEL_HEIGHT.max}
              onChange={setListPanelHeight}
              ariaLabel="Изменить высоту списка попыток"
            />
          )}

          {/* SubmissionsListPanel — resizable height at bottom */}
          {isDesktop && (
            <Box sx={{ flex: `0 0 ${listPanelHeight}px`, minHeight: 0, overflow: 'hidden' }}>
              <SubmissionsListPanel
                taskId={id ?? ''}
                submissions={submissions}
                onSelect={(sid) => {
                  // Capture focused row so closing the drawer can restore focus to it (WCAG 2.4.3)
                  if (document.activeElement instanceof HTMLElement) {
                    returnFocusRef.current = document.activeElement;
                  }
                  setSelectedSubmissionId(sid);
                }}
                currentPollingId={submitState === 'polling' ? currentSubmissionId : null}
                onRefresh={loadSubmissions}
              />
            </Box>
          )}
        </Box>
      </Box>

      {/* ── Drawer — outside layout flow ─────────────────────────── */}
      <SubmissionDetailDrawer
        submissionId={selectedSubmissionId}
        open={selectedSubmissionId !== null}
        onClose={() => setSelectedSubmissionId(null)}
        returnFocusRef={returnFocusRef}
      />
    </>
  );
};

export default Submission;
