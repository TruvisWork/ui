import React, { useState, useEffect } from "react";
import { Container, AppBar, Tabs, Tab, Box, Select, MenuItem, FormControl, InputLabel } from "@mui/material";
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Insight from "./components/Insight";
import Optimize from "./components/Optimize";
import Recommendation from "./components/Recommendation";
import DataCatalogEditor from "./components/DataCatalogEditor";
import QueryDetailsPage from './components/QueryDetailsPage';

const MainLayout = () => {
  const [tabIndex, setTabIndex] = useState(0);
  const [projectId, setProjectId] = useState('');
  const [selectedProject, setSelectedProject] = useState('hsbc-12010598-fdrasp-dev');
  const projectOptions = [
    'hsbc-12010598-fdrasp-dev',
    'hsbc-10415649-cbdoceurope-dev.uae_cb_hub_views_all_dev',
  ];
  useEffect(() => {
    // Get project ID if needed
    const storedProjectId = sessionStorage.getItem('projectId');
    if (storedProjectId) {
      setProjectId(storedProjectId);
    }
  }, []);
  const handleTabChange = (event, newIndex) => {
    setTabIndex(newIndex);
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
          {/* Project Selection Dropdown */}
          <Box sx={{ width: 300, ml: 2 }}>
            <FormControl fullWidth variant="outlined" size="small">
              <InputLabel id="project-select-label" sx={{ color: 'white' }}>Project</InputLabel>
              <Select
                labelId="project-select-label"
                id="project-select"
                value={selectedProject}
                label="Project"
                onChange={(e) => setSelectedProject(e.target.value)}
                sx={{ color: 'white', backgroundColor: '#db1110', borderColor: 'white' }}
              >
                {projectOptions.map((option) => (
                  <MenuItem key={option} value={option}>{option}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Box>
          {/* Project ID Display */}
          {projectId && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'white', fontSize: '1rem', fontWeight: '600' }}>
              <span>Project ID:</span>
              <span style={{ fontWeight: 700 }}>{projectId}</span>
            </Box>
          )}
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
        <Route path="/" element={<MainLayout />} />
        <Route path="/query-details/:ruleId/:recommendation/:ruleTitle" element={<QueryDetailsPage />} />
        <Route path="/*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
};

export default App;