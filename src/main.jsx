import React from 'react';
import ReactDOM from 'react-dom/client'; // Import from 'react-dom/client'
import { ThemeProvider, createTheme } from '@mui/material/styles';
import App from './App';
import './index.css'; // Optional: If you have global styles
import theme from './theme.jsx';

// Create a theme instance
// const theme = createTheme();

// Create a root and render the App component
const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <ThemeProvider theme={theme}>
    <App />
  </ThemeProvider>
);