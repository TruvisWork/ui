import React, { useState, useEffect } from "react";
import { Container, AppBar, Tabs, Tab, Box } from "@mui/material";
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Insight from "./components/Insight";
import Optimize from "./components/Optimize";
import Recommendation from "./components/Recommendation";
import DataCatalogEditor from "./components/DataCatalogEditor";
import Auth from './components/Auth';
import QueryDetailsPage from './components/QueryDetailsPage';


const ProtectedRoute = ({ children }) => {
  const selectedMarket = sessionStorage.getItem('selectedMarket');
  
  if (!selectedMarket) {
    return <Navigate to="/login" replace />;
  }
  
  return children;
};

const MainLayout = () => {
  const [tabIndex, setTabIndex] = useState(0);
  const [selectedMarket, setSelectedMarket] = useState('');
  const [userName, setUserName] = useState('');
  const [projectId, setProjectId] = useState('');

  useEffect(() => {
    // Get selected market and user info
    const market = sessionStorage.getItem('selectedMarket');
    const storedUserName = sessionStorage.getItem('userName');
    const storedProjectId = sessionStorage.getItem('projectId');
    if (market) {
      setSelectedMarket(market);
    }
    if (storedUserName) {
      setUserName(storedUserName);
    }
    if (storedProjectId) {
      setProjectId(storedProjectId);
    }
  }, []);

  const handleTabChange = (event, newIndex) => {
    setTabIndex(newIndex);
  };

  const handleLogout = () => {
    sessionStorage.removeItem('allowedMarkets');
    sessionStorage.removeItem('selectedMarket');
    sessionStorage.removeItem('username');
    fetch('http://localhost:8000/logout', {
      method: 'POST',
      credentials: 'include', // to include the cookie
    })
      .catch(error => {
        console.error('Logout failed', error);
      })
      .finally(() => {
        window.location.href = '/login';
      });
  };

  const handleChangeMarket = () => {
    // Set flag to indicate market change request
    sessionStorage.setItem('changeMarketRequest', 'true');
    window.location.href = '/login';
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
          
          {/* Top Right Section - Market and Actions */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 3 }}>
            {/* Project ID Display */}
            {projectId && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'white', fontSize: '1rem', fontWeight: '600' }}>
                <span>Project ID:</span>
                <span style={{ fontWeight: 700 }}>{projectId}</span>
              </Box>
            )}
            {/* Selected Market Display */}
            <Box sx={{ 
              display: 'flex', 
              alignItems: 'center',
              gap: 1,
              color: 'white',
              fontSize: '1rem',
              fontWeight: '600'
            }}>
              <span>Market:</span>
              {selectedMarket || 'No Market Selected'}
            </Box>

            {/* Change Market Button */}
            <button
              onClick={handleChangeMarket}
              style={{
                background: 'rgba(255, 255, 255, 0.1)',
                color: 'white',
                border: '1px solid rgba(255, 255, 255, 0.3)',
                borderRadius: '8px',
                padding: '8px 16px',
                cursor: 'pointer',
                fontSize: '0.85rem',
                fontWeight: '500',
                transition: 'all 0.3s ease',
              }}
              onMouseOver={(e) => {
                e.target.style.background = 'rgba(255, 255, 255, 0.2)';
              }}
              onMouseOut={(e) => {
                e.target.style.background = 'rgba(255, 255, 255, 0.1)';
              }}
            >
              Change Market
            </button>
            
            {/* Logout Button */}
            <button
              onClick={handleLogout}
              style={{
                background: 'rgba(255, 255, 255, 0.2)',
                color: 'white',
                border: '1px solid rgba(255, 255, 255, 0.3)',
                borderRadius: '8px',
                padding: '8px 16px',
                cursor: 'pointer',
                fontSize: '0.9rem',
                fontWeight: '500',
                transition: 'all 0.3s ease',
              }}
              onMouseOver={(e) => {
                e.target.style.background = 'rgba(255, 255, 255, 0.3)';
              }}
              onMouseOut={(e) => {
                e.target.style.background = 'rgba(255, 255, 255, 0.2)';
              }}
            >
              Logout
            </button>
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
        {tabIndex === 2 && <Recommendation />}
        {tabIndex === 3 && <DataCatalogEditor />}
      </Box>
    </Container>
  );
};

const App = () => {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<Auth />} />
        <Route path="/" element={
          <ProtectedRoute>
            <MainLayout />
          </ProtectedRoute>
        } />
        <Route path="/query-details/:ruleId/:recommendation" element={<QueryDetailsPage />} />
        <Route path="/*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
};

export default App;