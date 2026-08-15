import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// Leaflet's stylesheet first, so our own rules below can override its defaults.
// Imported from a component instead, it lands later in the bundle and wins on
// source order — which is how the map container kept Leaflet's light grey.
import 'leaflet/dist/leaflet.css'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
