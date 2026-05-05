# Web Resolver Frontend

Modern React-based frontend for the Web Resolver Task platform - a programming task resolution system with AI-powered code analysis.

## 🚀 Technology Stack

- **React 18** - UI framework with TypeScript
- **Vite** - Fast build tool and dev server
- **Material-UI (MUI) v5** - Beautiful, responsive UI components
- **Monaco Editor** - VS Code's code editor component
- **React Router v6** - Client-side routing
- **Zustand** - Lightweight state management
- **Axios** - HTTP client
- **Recharts** - Data visualization
- **React Toastify** - Notifications

## 📋 Features

### ✨ User Interface
- **Modern Design** - Clean, intuitive interface based on Material Design
- **Dark/Light Mode** - Toggle between themes for comfortable coding
- **Responsive Layout** - Works on desktop, tablet, and mobile devices
- **Sidebar Navigation** - Easy access to all sections

### 💻 Code Editor
- **Monaco Editor** - Same editor as VS Code
- **Syntax Highlighting** - Support for Java, Kotlin, Python
- **Auto-completion** - Intelligent code suggestions
- **Line Numbers** - Easy code navigation

### 📊 Dashboard
- **Statistics Overview** - Track your progress
- **Activity Charts** - Visualize submission history
- **Achievements** - Gamified learning experience

### 📝 Task Management
- **Task List** - Browse available problems
- **Search & Filter** - Find tasks by difficulty or category
- **Detailed Descriptions** - Clear problem statements with examples
- **Difficulty Levels** - Easy, Medium, Hard tasks

### 🎯 Submission & Results
- **Instant Feedback** - Real-time test results
- **Detailed Metrics** - Execution time, memory usage
- **Test Case Breakdown** - See which tests passed/failed
- **AI Analysis** - Get code quality insights and recommendations

## 🛠️ Installation

### Prerequisites
- Node.js 18+ 
- npm or yarn

### Setup

1. **Navigate to frontend directory:**
   ```bash
   cd frontend
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Start development server:**
   ```bash
   npm run dev
   ```

   The application will be available at `http://localhost:3000`

4. **Build for production:**
   ```bash
   npm run build
   ```

5. **Preview production build:**
   ```bash
   npm run preview
   ```

## 📁 Project Structure

```
frontend/
├── src/
│   ├── components/          # Reusable UI components
│   │   ├── common/          # Common components (Layout, Header, Sidebar)
│   │   ├── editor/          # Code editor components
│   │   ├── task/            # Task-related components
│   │   ├── results/         # Results display components
│   │   └── ai/              # AI analysis components
│   ├── pages/               # Page components
│   │   ├── Dashboard.tsx    # Main dashboard with statistics
│   │   ├── TaskList.tsx     # Browse and search tasks
│   │   ├── TaskDetail.tsx   # Individual task view
│   │   ├── Submission.tsx   # Code editor and submission
│   │   └── Results.tsx      # Submission results and analysis
│   ├── services/            # API services
│   │   └── api.ts           # Axios configuration and API calls
│   ├── store/               # State management
│   │   └── appStore.ts      # Zustand store
│   ├── theme/               # MUI theme configuration
│   │   └── theme.ts         # Light and dark themes
│   ├── types/               # TypeScript type definitions
│   │   └── index.ts         # All type interfaces
│   ├── utils/               # Utility functions
│   ├── App.tsx              # Main application component
│   └── main.tsx             # Application entry point
├── public/                  # Static assets
├── index.html               # HTML template
├── package.json             # Dependencies and scripts
├── tsconfig.json            # TypeScript configuration
├── vite.config.ts           # Vite configuration
└── README.md                # This file
```

## 🎨 Customization

### Theme Colors

Edit `src/theme/theme.ts` to customize colors:

```typescript
const colors = {
  primary: {
    main: '#1976d2',  // Change primary color
    // ...
  },
  // ...
};
```

### API Configuration

Update API base URL in `src/services/api.ts`:

```typescript
const api = axios.create({
  baseURL: '/api/v1',  // Change API endpoint
  // ...
});
```

## 🔌 API Integration

The frontend connects to the backend API through the following services:

- **Task Service** - Fetch task lists and details
- **Submission Service** - Submit solutions and get results
- **AI Service** - Retrieve AI code analysis
- **Statistics Service** - Get user statistics

Make sure the backend is running on `http://localhost:8080` (configured in `vite.config.ts` proxy).

## 📱 Responsive Design

The application is fully responsive:

- **Desktop (>960px)** - Full sidebar, multi-column layouts
- **Tablet (600px - 960px)** - Collapsible sidebar, adaptive grids
- **Mobile (<600px)** - Hidden sidebar, single-column layouts

## 🚦 Available Scripts

| Command | Description |
|---------|-------------|
| `npm run dev` | Start development server |
| `npm run build` | Build for production |
| `npm run preview` | Preview production build |
| `npm run lint` | Run ESLint |

## 🎯 Key Features Implementation

### Code Editor
Located in `Submission.tsx`, uses Monaco Editor with:
- Language selection (Java/Kotlin/Python)
- Dark theme for comfortable coding
- Auto-layout and responsive sizing

### Results Visualization
The `Results.tsx` component displays:
- Overall submission status
- Pass/fail ratio with progress bar
- Individual test case results (expandable)
- Performance metrics (time, memory)
- AI code quality analysis

### Dashboard
The `Dashboard.tsx` shows:
- Statistics cards (tasks, submissions, acceptance rate)
- Weekly activity chart (Recharts)
- Recent achievements

## 🔐 Security

- API token stored in localStorage
- Request/response interceptors for error handling
- CORS configured via Vite proxy

## 📝 Code Style

- **TypeScript** - Strict type checking enabled
- **ESLint** - Code quality and consistency
- **Prettier** - Code formatting (configure as needed)

## 🤝 Contributing

1. Create a feature branch
2. Make your changes
3. Run linting: `npm run lint`
4. Submit a pull request

## 📄 License

Part of the Web Resolver Task platform - Diploma Project 2024

## 📞 Support

For issues or questions, please refer to the main project documentation.
