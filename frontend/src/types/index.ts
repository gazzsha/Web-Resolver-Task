// Task types
export interface Task {
  testId: string;
  title: string;
  description: string;
  difficulty: 'Easy' | 'Medium' | 'Hard';
  category?: string | null;
}

// Submission types
export interface Submission {
  testId: string;
  code: string;
  language: 'java' | 'kotlin' | 'python';
}

export interface SubmissionResult {
  taskId: string;
  testId: string;
  status: 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED' | 'ERROR';
  totalTests: number;
  passedTests: number;
  totalExecutionTimeMs: number;
  memoryUsedKb: number;
  testResults: TestResult[];
  scenarioResults?: ScenarioResult[];
  aiAnalysis?: AIAnalysis;
  createdAt: string;
}

export interface TestResult {
  testId: string;
  status: 'PASSED' | 'FAILED' | 'ERROR' | 'SKIPPED';
  verdict: string;
  output?: string;
  error?: string;
  executionTimeMs: number;
  memoryUsedKb: number;
}

export interface ScenarioResult {
  scenarioId: string;
  status: 'PASSED' | 'FAILED' | 'ERROR' | 'SKIPPED';
  stepResults: StepResult[];
  finalState?: string;
}

export interface StepResult {
  stepNumber: number;
  status: 'PASSED' | 'FAILED' | 'ERROR' | 'SKIPPED';
  actualOutput?: string;
  expectedOutput?: string;
  stateMatches: boolean;
}

// AI Analysis types
export interface AIAnalysis {
  codeQuality: number;
  issues: CodeIssue[];
  recommendations: string[];
  explanation: string;
  complexity: 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH';
}

export interface CodeIssue {
  type: 'BUG' | 'CODE_SMELL' | 'SECURITY' | 'PERFORMANCE' | 'STYLE';
  severity: 'BLOCKER' | 'CRITICAL' | 'MAJOR' | 'MINOR' | 'INFO';
  line?: number;
  message: string;
  suggestion: string;
}

// API Response types
export interface ApiResponse<T> {
  data?: T;
  error?: string;
  success: boolean;
}

// Statistics types
export interface UserStatistics {
  totalSubmissions: number;
  acceptedSubmissions: number;
  tasksSolved: number;
  averageCodeQuality: number;
  recentActivity: ActivityDataPoint[];
  languageDistribution: LanguageDataPoint[];
}

export interface ActivityDataPoint {
  date: string;
  submissions: number;
}

export interface LanguageDataPoint {
  language: string;
  count: number;
}
