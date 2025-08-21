import React, { useState, useEffect } from "react";
import { Container, AppBar, Tabs, Tab, Box, Select, MenuItem, FormControl, InputLabel } from "@mui/material";
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import Insight from "./components/Insight";
import Optimize from "./components/Optimize";
import Recommendation from "./components/Recommendation";
import DataCatalogEditor from "./components/DataCatalogEditor";
import QueryDetailsPage from './components/QueryDetailsPage';
import Auth from './components/Auth';
import SessionManager from './components/SessionManager';

const ProtectedRoute = ({ children }) => {
  const [isAuthenticated, setIsAuthenticated] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const response = await fetch('http://localhost:8000/me', {
        credentials: 'include'
      });

      if (response.ok) {
        const userData = await response.json();
        const selectedMarket = sessionStorage.getItem('selectedMarket');
        
        if (userData.authenticated && selectedMarket) {
          setIsAuthenticated(true);
        } else {
          setIsAuthenticated(false);
        }
      } else {
        setIsAuthenticated(false);
        sessionStorage.clear();
      }
    } catch (error) {
      console.error('Auth check failed:', error);
      setIsAuthenticated(false);
      sessionStorage.clear();
    } finally {
      setIsLoading(false);
    }
  };

  if (isLoading) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        backgroundColor: '#f5f5f5'
      }}>
        <div style={{
          textAlign: 'center',
          padding: '2rem',
          backgroundColor: 'white',
          borderRadius: '8px',
          boxShadow: '0 2px 10px rgba(0,0,0,0.1)'
        }}>
          <div style={{
            width: '40px',
            height: '40px',
            border: '4px solid #f3f3f3',
            borderTop: '4px solid #db1110',
            borderRadius: '50%',
            animation: 'spin 1s linear infinite',
            margin: '0 auto 1rem'
          }}></div>
          <p>Loading...</p>
          <style jsx>{`
            @keyframes spin {
              0% { transform: rotate(0deg); }
              100% { transform: rotate(360deg); }
            }
          `}</style>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/auth" replace />;
  }

  return (
    <SessionManager>
      {children}
    </SessionManager>
  );
};

const MainLayout = () => {
  const [tabIndex, setTabIndex] = useState(0);
  const [projectId, setProjectId] = useState('');
  const [selectedProject, setSelectedProject] = useState('hsbc-12010598-fdrasp-dev');
  const [selectedMarket, setSelectedMarket] = useState('');
  const [username, setUsername] = useState('');

  const projectOptions = [
    'hsbc-12010598-fdrasp-dev',
    'hsbc-10415649-cbdoceurope-dev.uae_cb_hub_views_all_dev',
  ];

  useEffect(() => {
    // Get stored data
    const storedProjectId = sessionStorage.getItem('projectId');
    const storedMarket = sessionStorage.getItem('selectedMarket');
    const storedUsername = sessionStorage.getItem('username');
    
    if (storedProjectId) setProjectId(storedProjectId);
    if (storedMarket) setSelectedMarket(storedMarket);
    if (storedUsername) setUsername(storedUsername);
  }, []);

  const handleTabChange = (event, newIndex) => {
    setTabIndex(newIndex);
  };

  const handleMarketChange = () => {
    // Set flag to indicate market change request
    sessionStorage.setItem('changeMarketRequest', 'true');
    window.location.href = '/auth';
  };

  const handleLogout = async () => {
    try {
      await fetch('http://localhost:8000/logout', {
        method: 'POST',
        credentials: 'include'
      });
    } catch (error) {
      console.error('Logout failed:', error);
    }
    
    sessionStorage.clear();
    window.location.href = '/auth';
  };

  return (
    <Container
      maxWidth={false}
      disableGutters
      sx={{
        width: "100vw",
        margin: 0,
        padding: 0,
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        backgroundColor: "#f5f5f5",
      }}
    >
      <AppBar
        position="static"
        sx={{
          width: "100%",
          backgroundColor: "#db1110",
          boxShadow: "none",
        }}
      >
        <Box
          sx={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            px: 2,
            overflowY: "auto",
          }}
        >
          <Tabs
            value={tabIndex}
            onChange={handleTabChange}
            textColor="inherit"
            indicatorColor="white"
            aria-label="tabs"
            sx={{
              "& .MuiTab-root": {
                fontSize: "1.2rem",
                color: "#ffffff",
                fontWeight: "bold",
                fontFamily: "sans-serif",
                textTransform: "none",
              },
            }}
          >
            <Tab label="Generate Query" />
            <Tab label="Optimize" />
            <Tab label="Recommendation" />
            <Tab label="Data Catalog Editor" />
          </Tabs>
          
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            {/* Project Selection Dropdown */}
            <Box sx={{ width: 300 }}>
              <FormControl fullWidth variant="outlined" size="small">
                <InputLabel id="project-select-label" sx={{ color: 'white' }}>Project</InputLabel>
                <Select
                  labelId="project-select-label"
                  id="project-select"
                  value={selectedProject}
                  label="Project"
                  onChange={(e) => setSelectedProject(e.target.value)}
                  sx={{ 
                    color: 'white', 
                    backgroundColor: '#db1110', 
                    borderColor: 'white',
                    '& .MuiOutlinedInput-notchedOutline': {
                      borderColor: 'rgba(255, 255, 255, 0.5)',
                    },
                    '&:hover .MuiOutlinedInput-notchedOutline': {
                      borderColor: 'white',
                    },
                    '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                      borderColor: 'white',
                    },
                  }}
                >
                  {projectOptions.map((option) => (
                    <MenuItem key={option} value={option}>{option}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Box>

            {/* Market Display and Change Button */}
            {selectedMarket && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <span style={{ color: 'white', fontSize: '1rem' }}>
                  Market: <strong>{selectedMarket}</strong>
                </span>
                <button
                  onClick={handleMarketChange}
                  style={{
                    background: 'rgba(255, 255, 255, 0.2)',
                    color: 'white',
                    border: '1px solid rgba(255, 255, 255, 0.5)',
                    borderRadius: '4px',
                    padding: '0.25rem 0.5rem',
                    fontSize: '0.8rem',
                    cursor: 'pointer'
                  }}
                >
                  Change
                </button>
              </Box>
            )}

            {/* User Info and Logout */}
            {username && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <span style={{ color: 'white', fontSize: '1rem' }}>
                  👤 {username}
                </span>
                <button
                  onClick={handleLogout}
                  style={{
                    background: 'rgba(255, 255, 255, 0.2)',
                    color: 'white',
                    border: '1px solid rgba(255, 255, 255, 0.5)',
                    borderRadius: '4px',
                    padding: '0.25rem 0.75rem',
                    fontSize: '0.9rem',
                    cursor: 'pointer'
                  }}
                >
                  Logout
                </button>
              </Box>
            )}

            {/* Project ID Display */}
            {projectId && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'white', fontSize: '1rem', fontWeight: '600' }}>
                <span>Project ID:</span>
                <span style={{ fontWeight: 700 }}>{projectId}</span>
              </Box>
            )}
          </Box>
        </Box>
      </AppBar>
      
      <Box
        sx={{
          flexGrow: 1,
          p: 3,
          display: "flex",
          backgroundColor: '#f5f5f5',
          justifyContent: "center",
          alignItems: "top",
        }}
      >
        {tabIndex === 0 && <Insight />}
        {tabIndex === 1 && <Optimize />}
        {tabIndex === 2 && <Recommendation selectedProject={selectedProject} />}
        {tabIndex === 3 && <DataCatalogEditor />}
      </Box>
    </Container>
  );
};

const App = () => {
  return (
    <Router>
      <Routes>
        <Route path="/auth" element={<Auth />} />
        <Route 
          path="/" 
          element={
            <ProtectedRoute>
              <MainLayout />
            </ProtectedRoute>
          } 
        />
        <Route 
          path="/query-details/:ruleId/:recommendation/:ruleTitle" 
          element={
            <ProtectedRoute>
              <QueryDetailsPage />
            </ProtectedRoute>
          } 
        />
        <Route path="/*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
};

export default App;
