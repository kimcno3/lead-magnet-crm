import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/tokens.css'
import './styles/base.css'
import './styles/components.css'
import App from './App.tsx'
import { applyTheme, readTheme } from './preferences'

// 첫 렌더 전에 저장된 테마를 적용해 깜빡임을 줄인다
applyTheme(readTheme())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
