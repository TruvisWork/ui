import React, {useEffect, useRef, useState} from "react";
import {
  Box,
  Button,
  Container,
  CssBaseline,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Drawer,
  Grid,
  IconButton,
  InputAdornment,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Paper,
  Select,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from "@mui/material";
import {LoadingButton} from "@mui/lab";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import AccessTimeIcon from "@mui/icons-material/AccessTime";
import ContentCopyOutlinedIcon from "@mui/icons-material/ContentCopyOutlined";
import CloseIcon from "@mui/icons-material/Close";
import DownloadForOfflineOutlinedIcon from "@mui/icons-material/DownloadForOfflineOutlined";

const BusinessUserTab = () => {
  const [prompt, setPrompt] = useState("");
  const [generateLoading, setGenerateLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [optimizedQuery, setOptimizedQuery] = useState("");
  const [data, setData] = useState([]);
  const [insights, setInsights] = useState([]);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(5);
  const [executionData, setExecutionData] = useState(null);
  const [dryRunLoading, setDryRunLoading] = useState(false);
  const [errorDialogOpen, setErrorDialogOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [errorPrompts, setErrorPrompts] = useState([]);
  const [selectedDataCategory, setSelectedDataCategory] = useState("Market Data");
  const [selectedDataSubCategory, setSelectedDataSubCategory] = useState("Payments");
  const [insightLoading, setInsightLoading] = useState(false);
  const [resultsLoading, setResultsLoading] = useState(false);
  const originalHashRef = useRef("");
  const [hasQueryChanged, setHasQueryChanged] = useState(false);
  // Success notification
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [snackbarSeverity, setSnackbarSeverity] = useState("success");
  
  // Track if query has been executed

  const [selectedUnit, setSelectedUnit] = useState('bytes');

  const [isQueryExecuted, setIsQueryExecuted] = useState(false);
  const [isQueryEditable, setIsQueryEditable] = useState(false);
  
  // Cost and timeout warnings
  const [isTooExpensive, setIsTooExpensive] = useState(false); // This will now be a blocking condition
  const [isTimeoutRisk, setIsTimeoutRisk] = useState(false);
  const [estimatedCost, setEstimatedCost] = useState(0);
  const [estimatedTime, setEstimatedTime] = useState(0);
  const [costEstimationError, setCostEstimationError] = useState(null);
  const [originalQuery, setOriginalQuery] = useState("");
  const [editedQuery, setEditedQuery] = useState("");
  const [editedHashRef, setEditedHashRef] = useState("");

  const [timedOut, setTimedOut] = useState(false);
  const textFieldRef = useRef(null);

  //for prompts to show
  const [prompts, setPrompts] = useState([]);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [lastScrollY, setLastScrollY] = useState(0);
  const [expandedPrompt, setExpandedPrompt] = useState(null);

  const toggleDrawer = () => {
    setDrawerOpen(prevOpen => {
      // Update lastScrollY when closing to prevent immediate reopen
      if (prevOpen) {
        setLastScrollY(window.scrollY);
      }
      return !prevOpen;
    });
  };

  const handleQueryChange = (e) => {
    const newQuery = e.target.value;
    setOptimizedQuery(newQuery);

    const currentHash = hashCode(newQuery);
    const originalHash = originalHashRef.current;

    // Always update the edited query and its hash
    setEditedQuery(newQuery);
    const newHash = currentHash;
    setEditedHashRef(newHash);
    setHasQueryChanged(currentHash !== originalHash);

    console.log('Query edited:', {
      original: originalQuery,
      edited: newQuery,
      originalHash,
      currentHash: newHash,
      hasChanged: currentHash !== originalHash
    });
  };

  // Consistent hash function that returns the same value for the same input
  const hashCode = (str) => {
    if (!str) return 0;
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32bit integer
    }
    return hash;
  };



  const handlePromptClick = (promptText) => {
    setPrompt(promptText);
  };

  const selectedMarket = sessionStorage.getItem('selectedMarket');
  // API configuration constants
  const API_BASE_URL = 'http://localhost:8000';
  const API_CONFIG = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // Enhanced API request handler with better error handling
  const makeApiRequest = async (endpoint, options = {}) => {


    const config = {
      ...API_CONFIG,
      ...options,
      credentials: 'include', // Ensure cookies are sent with the request
      headers: {
        ...API_CONFIG.headers,
        ...options.headers,
      },
    };

    try {
      const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
      return response;
    } catch (error) {
      console.error(`API request failed for ${endpoint}:`, error);
      throw new Error(`Network error: Unable to connect to server`);
    }
  };

  const parseErrorMessage = (error) => {
    // If error is an object with a message, return it
    if (error && typeof error === "object") {
      return {
        message: error.message || "An error occurred.",
        suggestions: error.suggestions || [],
      };
    }
    // If error is a string, return as message
    return { message: String(error), suggestions: [] };
  }


  useEffect(() => {
    const market = sessionStorage.getItem('selectedMarket');
    setSelectedMarket(market);
  }, []);

  // Handle new prompt - resets all states
  const handleNewPrompt = () => {
    setPrompt("");
    setOptimizedQuery("");
    setData([]);
    setInsights([]);
    setExecutionData(null);
    setIsQueryExecuted(false);
    setIsQueryEditable(false);
    setIsTooExpensive(false);
    setIsTimeoutRisk(false);
    setEstimatedCost(0);
    setEstimatedTime(0);
    setCostEstimationError(null);
    // Focus back to prompt input
    if (textFieldRef.current) {
      textFieldRef.current.focus();
    }
  };

  // Handle copy to clipboard with feedback
  const handleCopyQuery = async () => {
    try {
      await navigator.clipboard.writeText(optimizedQuery);
      setSnackbarMessage("Query copied to clipboard!");
      setSnackbarSeverity("success");
      setSnackbarOpen(true);
    } catch (err) {
      console.error('Failed to copy text: ', err);
      setSnackbarMessage("Failed to copy query");
      setSnackbarSeverity("error");
      setSnackbarOpen(true);
    }
  };

  // Enhanced dry run with better error handling
  const handleDryRun = async (sqlQuery) => {
    setDryRunLoading(true);
    setExecutionData(null);
    setIsTooExpensive(false);
    setIsTimeoutRisk(false);
    setCostEstimationError(null);

    try {
      const response = await makeApiRequest('/estimate', {
        method: 'POST',
        body: JSON.stringify({
          query: sqlQuery,
          market: selectedMarket,
        }),
      });

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error.detail || "Cost estimation failed with status ${response.status}");
      }

      const result = await response.json();
      setExecutionData(result);
      
      // Calculate cost/time thresholds
      const cost = result.result?.estimated_cost_usd || 0;
      const bytes = result.result?.bytes_processed || 0;
      
      // Heuristic: 1GB/s processing speed
      const estimatedSeconds = bytes / (1024 * 1024 * 1024); 
      setEstimatedCost(cost);
      setEstimatedTime(estimatedSeconds);
      
      // Set blocking flag for cost and warning flag for timeout
      setIsTooExpensive(cost > 10);
      setIsTimeoutRisk(estimatedSeconds > 30);
      
      // Show success message
      setSnackbarMessage("Cost estimation completed successfully!");
      setSnackbarSeverity("success");
      setSnackbarOpen(true);
      
    } catch (error) {
      console.error("Error during dry run:", error);
      const parsedError = parseErrorMessage(error);
      setCostEstimationError(parsedError);
      setExecutionData(null);
    } finally {
      setDryRunLoading(false);
    }
  };

  useEffect(() => {
    handleLatestPrompts();

    const handleScroll = () => {
      const currentScrollY = window.scrollY;
      // Close drawer if scrolling down and it's open
      if (currentScrollY > lastScrollY && drawerOpen) {
        setDrawerOpen(false);
      }
      setLastScrollY(currentScrollY);
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, [drawerOpen, lastScrollY]);

//method to fetch latest prompts from API
const handleLatestPrompts = async () => {
  console.log("Fetching latest prompts for market:", selectedMarket);
  try {
    const response = await makeApiRequest('/latest_prompts', {
        method: 'POST',
        body: JSON.stringify({
          market: 'US',
        }),
      });

    const apiData = await response.json();
    console.log("API Response:", apiData);

    if (!response.ok) {
      const errorMsg = apiData.detail || "Failed to fetch latest prompts";
      throw new Error(errorMsg);
    }

    // Update prompts state with API data
    setPrompts(apiData.prompts || []);

  } catch (err) {
    console.error("Error fetching latest prompts:", err);
    // Fallback to empty list on error
    setPrompts([]);
    }
  };

  const handleInsightData = async () => {
    setResultsLoading(true);
    setInsightLoading(true);
    setTimedOut(false);

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 30000);

    try {
      // First check if query was edited
      if (hasQueryChanged) {
        try {
          await makeApiRequest('/log-prompt-query', {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              prompt: prompt,// You may want to add prompt tracking later
              query: originalQuery,
              edited_query: editedQuery,
              market: selectedMarket,
            })

          }
        );
        setHasQueryChanged(false);
        setEditedQuery("");
        setOriginalQuery("");
        } catch (logError) {
          console.error("Failed to log query edit:", logError);
        }
      }
      else{
        try {
          await makeApiRequest('/log-prompt-query', {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              user_id: "abc",
              prompt: prompt,// prompt
              query: originalQuery,
              edited_query: "",
              market: selectedMarket,
            })
          });
          setHasQueryChanged(false);  //reset so that it dont always take the previous value
          setEditedQuery("");
          setOriginalQuery("");
        } catch (logError) {
          console.error("Failed to log query edit:", logError);
        }
      }
    } catch (error) {
      console.error("Error logging query edit:", error);
    }

    try {
      const response = await makeApiRequest('/execute_query', {
        method: 'POST',
        body: JSON.stringify({
          query: optimizedQuery,
          market: selectedMarket,
        }),
      });



      clearTimeout(timeoutId);
      
      const apiData = await response.json();

      if (!response.ok) {
        // If backend returns error, show textual_summary if available
        const errorMsg =
          (apiData.textual_summary && apiData.textual_summary.length > 0)
            ? apiData.textual_summary.join(" ")
            : "API request failed";
        throw new Error(errorMsg);
      }
      
      setData(apiData.result || []);
      setInsights(apiData.textual_summary || []);
      
      setIsQueryExecuted(true);
      // setIsQueryEditable(false);
      
      // // Show success message
      // setSnackbarMessage("Query executed successfully!");
      // setSnackbarSeverity("success");
      // setSnackbarOpen(true);
      
    } catch (err) {
      if (err.name === 'AbortError') {
        setTimedOut(true);
        setErrorMessage("Query execution timed out after 30 seconds.");
      } else {
        const parsedError = parseErrorMessage(err);
        setErrorMessage(err.message || "Error executing query.");
        setErrorDialogOpen(true);
        setErrorMessage(parsedError.message);
        setErrorPrompts(parsedError.suggestions || []);
      }
      setErrorDialogOpen(true);
      setIsQueryExecuted(true);
    } finally {
      clearTimeout(timeoutId);
      setResultsLoading(false);
      setInsightLoading(false);
    }
  };

  // Generate Optimized Query with enhanced error handling
  const handleOptimizeData = async () => {
    // Reset states when generating new query
    setQuery("");
    setOptimizedQuery("");
    setData([]);
    setInsights([]);
    setExecutionData(null);
    setIsQueryEditable(true);
    setIsQueryExecuted(false); // RESET execution state for new query
    setIsTooExpensive(false);
    setIsTimeoutRisk(false);
    setEstimatedCost(0);
    setEstimatedTime(0);
    setCostEstimationError(null);

    setGenerateLoading(true);

    try {
      const response = await makeApiRequest('/generate_query', {
        method: "POST", 
        body: JSON.stringify({
          query: prompt,
          llm_type: "openai",
          market: selectedMarket,
        }),
      });
      console.log("selectedMarket 2:", selectedMarket);

      const apiData = await response.json();
      console.log("apiData:", apiData);
      if (!response.ok) {
        // If backend returns error, show textual_summary if available
        const errorMsg =
          (apiData.textual_summary && apiData.textual_summary.length > 0)
            ? apiData.textual_summary.join(" ")
            : "API request failed";
        throw new Error(errorMsg);
      }


      setOptimizedQuery(apiData.sql_query_generated || "No query generated.");
      setOriginalQuery(apiData.sql_query_generated || "No query generated.");
      const hash = hashCode(apiData.sql_query_generated || "No query generated.");
      originalHashRef.current = hash;
      console.log("Optimized Query:", apiData.sql_query_generated);
      console.log("Original Hash Ref:", hash);
      setIsQueryEditable(true);
      if (!apiData.sql_query_generated && apiData.textual_summary && apiData.textual_summary.length > 0) {
        setOptimizedQuery(apiData.textual_summary.join(" "));
      } else {
        setOptimizedQuery(apiData.sql_query_generated || "No query generated.");
      }
      // Show success message
      setSnackbarMessage("Query generated successfully!");
      setSnackbarSeverity("success");
      setSnackbarOpen(true);
      
    } catch (error) {
      console.error("Error fetching insights:", error);
      setOptimizedQuery(""); // Clear the query box on error
      setErrorMessage(error.message || "Error generating query.");
      setErrorDialogOpen(true);
    } finally {
      setGenerateLoading(false);
    }
  };

  // Handle query text change - only allow if editable and not executed
  // const handleQueryChange = (e) => {
  //   if (isQueryEditable && !isQueryExecuted) {
  //     setOptimizedQuery(e.target.value);
  //     // Reset cost estimates when query changes
  //     setExecutionData(null);
  //     setIsTooExpensive(false);
  //     setIsTimeoutRisk(false);
  //     setEstimatedCost(0);
  //     setEstimatedTime(0);
  //     setCostEstimationError(null);
  //   }
  // };

  // Pagination Logic
  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const emptyRows = page > 0 ? Math.max(0, (1 + page) * rowsPerPage - data.length) : 0;
  const displayRows = data.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  // Disable execute button if query executed, cost estimation failed, or cost is too high
  const isExecuteDisabled = isQueryExecuted || costEstimationError || isTooExpensive;

  // Generate tooltip text for disabled button
  const getExecuteTooltip = () => {
    if (isQueryExecuted) {
      return "Query has already been executed. Generate a new query to execute again.";
    }
    if (timedOut) {
      return "Query execution timed out. Generate a new query to try again.";
    }
    // --- Blocking Conditions ---
    if (isTooExpensive) {
      return `Execution blocked: Estimated cost $${estimatedCost.toFixed(2)} exceeds the $10.00 limit.`;
    }
    if (costEstimationError) {
      return "Cannot execute - cost estimation failed. Please regenerate the query.";
    }
    // --- Non-blocking Warnings ---
    if (isTimeoutRisk) {
      return `Warning: Long runtime (${estimatedTime.toFixed(1)}s). Click to proceed anyway.`;
    }
    return "Execute query";
  };

  return (
    <>
      {/* Fixed Toggle Button - Using fixed positioning */}
      <Container
        maxWidth={false}
        disableGutters
        sx={{
          width: "100vw",
          minHeight: "100vh",
          bgcolor: "#f4f6f8",
          display: "flex",
          flexDirection: "row",
          pl: {
            xs: drawerOpen ? '370px' : 3,
            sm: drawerOpen ? '420px' : 4,
            md: drawerOpen ? '420px' : 6,
            lg: drawerOpen ? '420px' : 8
          },
          pr: { xs: 2, sm: 4, md: 6, lg: 8 },
          py: { xs: 2, sm: 3, md: 4 },
          position: 'relative',
          overflowX: 'hidden',
          transition: 'all 0.3s ease',
        }}
      >
        <CssBaseline />
        <Box sx={{ flex: 1, maxWidth: '1200px' }}>

      {/* Prompt Input Section */}
      <Box sx={{ 
        mt: { xs: 3, sm: 4, md: 4 },
        maxWidth: "1200px",
        width: "100%",
        mx: "auto"
      }}>
        <Box>
          <Grid container spacing={{ xs: 2, sm: 3 }}>
            <Grid item xs={12} >
              <TextField
                inputRef={textFieldRef}
                autoFocus
                fullWidth
                label="Enter your query"
                placeholder="e.g. Show the count of assignee who have updated their email in the last 1 month."
                variant="outlined"
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                multiline
                minRows={3}
                maxRows={5}
                sx={{
                  bgcolor: "#ffffff",
                  borderRadius: 3,
                  boxShadow: "0 4px 12px rgba(0, 0, 0, 0.1)",
                  transition: "box-shadow 0.2s ease-in-out",
                  "&:hover": {
                    boxShadow: "0 6px 20px rgba(0, 0, 0, 0.15)",
                  },
                  "& .MuiOutlinedInput-root": {
                    borderRadius: 3,
                    fontSize: "1rem",
                    lineHeight: 1.5,
                  },
                  "& .MuiInputLabel-root": {
                    fontSize: "1.1rem",
                    fontWeight: 500,
                  },
                }}
                InputProps={{
                  endAdornment: prompt && (
                    <InputAdornment position="end">
                      <IconButton
                        onClick={handleNewPrompt}
                        size="small"
                        sx={{
                          color: "#666",
                          transition: "all 0.2s ease-in-out",
                          '&:hover': {
                            color: "#333",
                            backgroundColor: "rgba(0, 0, 0, 0.08)",
                            transform: "scale(1.1)",
                          }
                        }}
                      >
                        <CloseIcon fontSize="small" />
                      </IconButton>
                    </InputAdornment>
                  )
                }}
              />
            </Grid>

            <Grid item xs={12} sx={{
              display: 'flex',
              justifyContent: 'flex-end',
              alignItems: 'center',
              mt: 1,
              gap: 2,
              pr: { xs: 0, sm: 1, md: 2 }
            }}>
              <Button
                variant="outlined"
                size="small"
                startIcon={<AccessTimeIcon />}
                onClick={toggleDrawer}
                sx={{
                  height: 40,
                  width: 'auto',
                  minWidth: 'auto',
                  padding: '25px 20px',
                  fontWeight: 600,
                  textTransform: 'none',
                  borderRadius: 3,
                  fontSize: '1rem',
                  backgroundColor: 'white',
                  border: '1px solid #000000',
                  color: '#000000',
                  transition: 'all 0.2s ease-in-out',
                  '&:hover': {
                    backgroundColor: 'rgba(0, 0, 0, 0.04)',
                    borderColor: '#000000',
                  },
                  '& .MuiButton-startIcon': {
                    marginRight: '8px',
                    marginLeft: '-4px',
                  },
                  boxShadow: 'none',
                }}
              >
                History
              </Button>
              <LoadingButton
                loading={generateLoading}
                loadingPosition="start"
                startIcon={<PlayArrowIcon />}
                variant="contained"
                onClick={handleOptimizeData}
                sx={{

                  height: 56,
                  width: "100%",
                  maxWidth: { xs: "100%", sm: 250, md: 200 },
                  fontWeight: "bold",
                  textTransform: "none",
                  boxShadow: "0 4px 12px rgba(25, 118, 210, 0.3)",
                  borderRadius: 3,
                  fontSize: "1rem",
                  transition: "all 0.2s ease-in-out",
                  "&:hover": {
                    boxShadow: "0 6px 20px rgba(25, 118, 210, 0.4)",
                    transform: "translateY(-2px)",
                  },
                }}
              >
                GENERATE
              </LoadingButton>
            </Grid>
          </Grid>
        </Box>
      </Box>



      {/* Optimized Query Display */}
      {isQueryEditable && !generateLoading && (
        <Box sx={{
          mt: { xs: 10, sm: 4, md: 5 },
          maxWidth: "1200px",
          width: "100%",
          mx: "auto"
        }}>
          <Grid container spacing={{ xs: 2, sm: 3 }} sx={{ justifyContent: 'flex-start' }}>
            <Grid item xs={10} sx={{ maxWidth: '85%', ml: '100px' }}>
              <Box
                sx={{
                  borderRadius: 3,
                  boxShadow: "0 6px 24px rgba(0, 0, 0, 0.12)",
                  overflow: "hidden",
                  bgcolor: "background.paper",
                  border: "1px solid rgba(0, 0, 0, 0.08)",
                  width: '100%',
                }}
              >
                {/* Query display content */}
              </Box>
            </Grid>
          </Grid>
        </Box>
      )}

      {/* Recent Prompts Sidebar */}
      <Drawer
        variant="persistent"
        anchor="left"
        open={drawerOpen}
        sx={{
          width: 350,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: 350,
            boxSizing: 'border-box',
            borderRight: '1px solid rgba(0, 0, 0, 0.12)',
            boxShadow: '4px 0 10px -5px rgba(0,0,0,0.2)',
            height: 'calc(100vh - 64px)',
            top: '64px',
            right: 'auto',
            left: 0,
            borderRadius: '0 16px 0 0',
            overflow: 'hidden',
            backgroundColor: 'rgb(239, 239, 239)',
          },
        }}
      >
        <Box sx={{ p: 2, overflow: 'auto', height: '100%' }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6" component="div">
              Recent Prompts
            </Typography>
            <IconButton
              onClick={toggleDrawer}
              size="small"
              sx={{
                backgroundColor: 'action.hover',
                color: 'text.secondary',
                '&:hover': {
                  backgroundColor: 'action.selected',
                },
                width: 32,
                height: 32,
                transition: 'all 0.2s ease-in-out',
              }}
            >
              <CloseIcon fontSize="small" />
            </IconButton>
          </Box>
          <Divider sx={{ mb: 2 }} />

          {prompts.length > 0 ? (
            <List>
              {prompts.slice(0, 5).map((promptObj, index) => (
                <React.Fragment key={index}>
                  <ListItemButton
                    onClick={() => handlePromptClick(promptObj.prompt)}
                    sx={{
                      borderRadius: 1,
                      mb: 0.5,
                      '&:hover': {
                        backgroundColor: 'rgba(0, 0, 0, 0.04)',
                      },
                    }}
                  >
                    <Tooltip
                        title={promptObj.prompt}
                        placement="top-start"
                        arrow
                        enterDelay={150}
                        enterNextDelay={150}
                      >
                    <ListItemText
                      primary={
                        <Typography noWrap variant="body2">
                          {promptObj.prompt.length > 50
                            ? `${promptObj.prompt.substring(0, 50)}...`
                            : promptObj.prompt}
                        </Typography>
                      }
                    />
                  </Tooltip>
                  </ListItemButton>
                </React.Fragment>
              ))}
            </List>
          ) : (
            <Box sx={{ p: 2, textAlign: 'center', color: 'text.secondary' }}>
              <Typography variant="body2">No recent prompts</Typography>
            </Box>
          )}
        </Box>
      </Drawer>

      {/* Toggle Button moved to the root level */}

      {/* Optimized Query Display */}
      {isQueryEditable && !generateLoading && (
        <Box sx={{
          mt: { xs: 3, sm: 4, md: 5 },
          maxWidth: "1200px",
          width: "100%",
          mx: "auto"
        }}>
          <Grid container spacing={2}>
            <Grid item xs={12} sx={{
              pl: {
                xs: drawerOpen ? '0' : 0,
                sm: drawerOpen ? '0' : 0,
                md: drawerOpen ? '0' : 0,
                lg: drawerOpen ? '0' : 0
              }
            }}>
              <Box
                sx={{
                  borderRadius: 3,
                  boxShadow: "0 6px 24px rgba(0, 0, 0, 0.12)",
                  overflow: "hidden",
                  bgcolor: "background.paper",
                  border: "1px solid rgba(0, 0, 0, 0.08)",
                }}
              >
                <Box
                  sx={{

                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    px: { xs: 2, sm: 3 },
                    py: 2,
                    bgcolor: "#222222",
                  }}
                >
                  <Typography variant="subtitle1" sx={{ 
                    color: "#fff", 
                    fontWeight: "bold",
                    fontSize: "1.1rem",
                    letterSpacing: "0.02em"
                  }}>
                    Generated Query
                  </Typography>
                  <Box sx={{ display: "flex", gap: 1 }}>
                    <IconButton 
                      size="small" 
                      onClick={handleCopyQuery}
                      sx={{

                        transition: "all 0.2s ease-in-out",
                        '&:hover': {
                          backgroundColor: 'rgba(255, 255, 255, 0.15)',
                          transform: "scale(1.1)",
                        }
                      }}
                    >
                      <ContentCopyOutlinedIcon sx={{ fontSize: 20, color: "white" }} />
                    </IconButton>

                    <IconButton
                      size="small"
                      onClick={() => {
                        const blob = new Blob([optimizedQuery], { type: 'text/sql' });
                        const url = window.URL.createObjectURL(blob);
                        const a = document.createElement('a');
                        a.href = url;
                        a.download = 'query.sql';
                        document.body.appendChild(a);
                        a.click();
                        document.body.removeChild(a);
                        window.URL.revokeObjectURL(url);
                      }}
                      sx={{
                        transition: "all 0.2s ease-in-out",
                        '&:hover': {
                          backgroundColor: 'rgba(255, 255, 255, 0.15)',
                          transform: "scale(1.1)",
                        }
                      }}
                    >
                      <DownloadForOfflineOutlinedIcon sx={{ fontSize: 20, color: "white" }} />
                    </IconButton>
                  </Box>
                </Box>
                <Box sx={{ 
                  p: { xs: 2, sm: 3 }, 
                  backgroundColor: "#defaec" 
                }}>
                  <TextField
                    fullWidth
                    multiline
                    variant="outlined"
                    value={optimizedQuery}
                    onChange={handleQueryChange}
                    disabled={isQueryExecuted || !isQueryEditable}
                    sx={{ 
                      backgroundColor: isQueryExecuted ? "#f9f9f9" : "white",
                      borderRadius: 2,
                      "& .Mui-disabled": {
                        backgroundColor: "#f9f9f9"
                      },
                      "& .MuiOutlinedInput-root": {
                        borderRadius: 2,
                      }
                    }}
                    InputProps={{
                      style: { 
                        fontFamily: "monospace", 
                        fontWeight: "bold",
                        color: isQueryExecuted ? "#666" : "inherit",
                        fontSize: "0.95rem",
                        lineHeight: 1.6,
                      },
                    }}
                  />
                </Box>
              </Box>
            </Grid>
          </Grid>
        </Box>
      )}

      {/* Estimated Cost Button */}
      {isQueryEditable  && !generateLoading && (
        <Box sx={{ 
          mt: { xs: 2, sm: 3 },
          maxWidth: "1200px",
          width: "100%",
          mx: "auto",
          display: "flex",
          justifyContent: "flex-end"
        }}>
          <LoadingButton
            loading={dryRunLoading}
            startIcon={<AccessTimeIcon />}
            onClick={() => handleDryRun(optimizedQuery)}
            variant="contained"
            color={dryRunLoading ? "secondary" : "primary"}
            sx={{
              fontSize: "0.9rem",
              px: { xs: 3, sm: 4 },
              py: 1.5,
              textTransform: "none",
              borderRadius: 3,
              fontWeight: 600,
              boxShadow: "0 3px 10px rgba(0, 0, 0, 0.2)",
              transition: "all 0.2s ease-in-out",
              "&:hover": {
                transform: "translateY(-1px)",
                boxShadow: "0 5px 15px rgba(0, 0, 0, 0.25)",
              },
            }}
          >
            Estimated Cost
          </LoadingButton>
        </Box>
      )}

      {/* Execution Cost Table with Warning Indicators */}
      {executionData && (
        <Box sx={{ 
          mt: { xs: 3, sm: 4 },
          maxWidth: "1200px",
          width: "100%",
          mx: "auto"
        }}>
          <TableContainer 
            component={Paper} 
            sx={{ 
              boxShadow: "0 6px 24px rgba(0, 0, 0, 0.12)", 
              borderRadius: 3,
              overflow: "hidden",
              border: "1px solid rgba(0, 0, 0, 0.08)",
            }}
          >
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: "#222222" }}>
                  {["Estimated Cost", "Base Cost", "Price Per TB"].map((header, i) => (
                    <TableCell 
                      key={i} 
                      sx={{ 
                        fontWeight: "bold", 
                        fontSize: "0.95rem", 
                        color: "#fff",
                        py: 2.5,
                        px: { xs: 2, sm: 3 },
                        letterSpacing: "0.02em",
                      }}
                    >
                      {header}
                    </TableCell>
                  ))}
                  {/* Special header cell with dropdown */}
                  <TableCell 
                    sx={{ 
                      fontWeight: "bold", 
                      fontSize: "0.95rem", 
                      color: "#fff",
                      py: 2.5,
                      px: { xs: 2, sm: 3 },
                    }}
                  >
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Typography variant="inherit" sx={{ 
                        fontSize: "0.95rem", 
                        fontWeight: "bold",
                        letterSpacing: "0.02em"
                      }}>
                        Data Processed
                      </Typography>
                      <Select
                        value={selectedUnit}
                        onChange={(e) => setSelectedUnit(e.target.value)}
                        size="small"
                        variant="outlined"
                        sx={{ 
                          minWidth: 85,
                          height: 32,
                          bgcolor: '#222222',
                          color: '#fff',
                          fontSize: '0.8rem',
                          borderRadius: 2,
                          '& .MuiOutlinedInput-notchedOutline': {
                            borderColor: 'rgba(255, 255, 255, 0.3)',
                          },
                          '&:hover .MuiOutlinedInput-notchedOutline': {
                            borderColor: 'rgba(255, 255, 255, 0.5)',
                          },
                          '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                            borderColor: 'rgba(255, 255, 255, 0.7)',
                          },
                          '& .MuiSelect-icon': {
                            color: '#fff',
                          },
                        }}
                        MenuProps={{
                          PaperProps: {
                            sx: {
                              bgcolor: '#222222',
                              borderRadius: 2,
                              boxShadow: "0 4px 12px rgba(0, 0, 0, 0.3)",
                              '& .MuiMenuItem-root': {
                                color: '#fff',
                                fontSize: '0.8rem',
                                py: 1,
                                '&:hover': {
                                  bgcolor: '#4b5563',
                                },
                              },
                            },
                          },
                        }}
                      >
                        <MenuItem value="bytes">Bytes</MenuItem>
                        <MenuItem value="gb">GB</MenuItem>
                        <MenuItem value="tb">TB</MenuItem>
                      </Select>
                    </Box>
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                <TableRow sx={{ 
                  '&:hover': { 
                    bgcolor: '#f8fafc',
                    transition: "background-color 0.2s ease-in-out"
                  } 
                }}>
                  <TableCell sx={{ 
                    py: 2, 
                    px: { xs: 2, sm: 3 },
                    fontSize: "0.9rem",
                    fontWeight: 500,
                  }}>
                    ${`${executionData.result.estimated_cost_usd?.toFixed(6)}`}
                  </TableCell>
                  <TableCell sx={{ 
                    py: 2, 
                    px: { xs: 2, sm: 3 },
                    fontSize: "0.9rem",
                    fontWeight: 500,
                  }}>
                    ${`${executionData.result.base_cost_usd}`}
                  </TableCell>
                  <TableCell sx={{ 
                    py: 2, 
                    px: { xs: 2, sm: 3 },
                    fontSize: "0.9rem",
                    fontWeight: 500,
                  }}>
                   ${`${executionData.result.price_per_tb_usd}`}
                  </TableCell>
                  <TableCell sx={{ 
                    py: 2, 
                    px: { xs: 2, sm: 3 },
                    fontSize: "0.9rem",
                    fontWeight: 500,
                  }}>
                    {selectedUnit === 'bytes' && executionData.result.bytes_processed}
                    {selectedUnit === 'gb' && executionData.result.gigabytes_processed}
                    {selectedUnit === 'tb' && executionData.result.terabytes_processed}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
      )}
      {/* Show Results Button */}
      {isQueryEditable && !generateLoading && (
        <Box sx={{ 
          mt: { xs: 2, sm: 3 },
          maxWidth: "1200px",
          width: "100%",
          mx: "auto",
          display: "flex",
          justifyContent: "flex-end"
        }}>
          <LoadingButton 
            loading={insightLoading}
            loadingPosition="start"
            startIcon={<PlayArrowIcon />}
            variant="contained"
            color="primary"
            onClick={handleInsightData}
            sx={{
              fontSize: "0.9rem",
              px: { xs: 3, sm: 4 },
              py: 1.5,
              textTransform: "none",
              borderRadius: 3,
              fontWeight: 600,
              boxShadow: "0 3px 10px rgba(0, 0, 0, 0.2)",
              transition: "all 0.2s ease-in-out",
              "&:hover": {
                transform: "translateY(-1px)",
                boxShadow: "0 5px 15px rgba(0, 0, 0, 0.25)",
              },
            }}
          >
            Show Results
          </LoadingButton>
        </Box>
      )}

      {/* Error Dialog */}
      <Dialog 
        open={errorDialogOpen} 
        onClose={() => setErrorDialogOpen(false)}
        PaperProps={{
          sx: {
            borderRadius: 3,
            boxShadow: "0 8px 32px rgba(0, 0, 0, 0.2)",
            maxWidth: "500px",
          }
        }}
      >
        <DialogTitle sx={{ 
          fontWeight: "bold",
          fontSize: "1.2rem",
          pb: 1
        }}>
          Error
        </DialogTitle>
        <DialogContent sx={{ pt: 2 }}>
          <Typography variant="body1" color="error" sx={{ mb: 2 }}>
            {errorMessage}
          </Typography>
          {errorPrompts.length > 0 && (
            <>
              <Typography variant="h6" sx={{ mt: 3, mb: 2, fontWeight: 600 }}>
                Sample Analytical Prompts:
              </Typography>
              <Box component="ul" sx={{ pl: 2, m: 0 }}>
                {errorPrompts.map((prompt, index) => (
                  <Box key={index} sx={{ mb: 1.5 }}>
                    <Typography variant="body2" sx={{ lineHeight: 1.6 }}>
                      {prompt}
                    </Typography>
                  </Box>
                ))}
              </Box>
            </>
          )}
        </DialogContent>
        <DialogActions sx={{ p: 3, pt: 1 }}>
          <Button 
            onClick={() => setErrorDialogOpen(false)} 
            color="primary"
            variant="contained"
            sx={{
              textTransform: "none",
              borderRadius: 2,
              px: 3,
              py: 1,
            }}
          >
            Close
          </Button>
        </DialogActions>
      </Dialog>

      {/* Results Section */}
      {data.length > 0 && (
        <Box sx={{ 
          mt: { xs: 3, sm: 4 },
          maxWidth: "1200px",
          width: "100%",
          mx: "auto"
        }}>
          <Box
            sx={{
              borderRadius: 3,
              boxShadow: "0 6px 24px rgba(0, 0, 0, 0.12)",
              overflow: "hidden",
              bgcolor: "background.paper",
              border: "1px solid rgba(0, 0, 0, 0.08)",
            }}
          >
            {/* Results Header */}
            <Box
              sx={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                px: { xs: 2, sm: 3 },
                py: 2,
                bgcolor: "#222222",
                flexWrap: "wrap",
                rowGap: 1,
              }}
            >
              {/* Title Section */}
              <Typography variant="subtitle1" sx={{
                color: "#fff",
                fontWeight: "bold",
                fontSize: "1.1rem",
                letterSpacing: "0.02em"
              }}>
                Results
              </Typography>

              {/* Actions + Fetched Info */}
              <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
                {/* Fetched Rows Text */}
                <Typography variant="body2" sx={{
                  color: "#ccc",
                  fontSize: "0.9rem",
                  whiteSpace: "nowrap"
                }}>
                  Rows fetched: {data.length}
                </Typography>

                {/* Copy Button */}
                <IconButton
                  size="small"
                  onClick={() => {
                    const headers = Object.keys(data[0]);
                    const csvContent = [
                      headers.join(','),
                      ...data.map(row => Object.values(row).join(','))
                    ].join('\n');
                    navigator.clipboard.writeText(csvContent);
                  }}
                  sx={{
                    transition: "all 0.2s ease-in-out",
                    '&:hover': {
                      backgroundColor: 'rgba(255, 255, 255, 0.15)',
                      transform: "scale(1.1)",
                    },
                  }}
                >
                  <ContentCopyOutlinedIcon sx={{ fontSize: 20, color: "white" }} />
                </IconButton>

                {/* Download Button */}
                <IconButton
                  size="small"
                  onClick={() => {
                    const headers = Object.keys(data[0]);
                    const csvContent = [
                      headers.join(','),
                      ...data.map(row => Object.values(row).join(','))
                    ].join('\n');

                    const blob = new Blob([csvContent], { type: 'text/csv' });
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = 'results.csv';
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    window.URL.revokeObjectURL(url);
                  }}
                  sx={{
                    transition: "all 0.2s ease-in-out",
                    '&:hover': {
                      backgroundColor: 'rgba(255, 255, 255, 0.15)',
                      transform: "scale(1.1)",
                    },
                  }}
                >
                  <DownloadForOfflineOutlinedIcon sx={{ fontSize: 20, color: "white" }} />
                </IconButton>
              </Box>
            </Box>


            {/* Results Table */}
            <Box sx={{ p: 0 }}>
              <TableContainer 
                sx={{ 
                  maxHeight: "60vh",
                  overflowX: "auto",
                  overflowY: "auto",
                  '&::-webkit-scrollbar': {
                    width: '8px',
                    height: '8px',
                  },
                  '&::-webkit-scrollbar-track': {
                    backgroundColor: '#f1f1f1',
                    borderRadius: '4px',
                  },
                  '&::-webkit-scrollbar-thumb': {
                    backgroundColor: '#c1c1c1',
                    borderRadius: '4px',
                    '&:hover': {
                      backgroundColor: '#a8a8a8',
                    },
                  },
                }}>
                <Table stickyHeader sx={{ minWidth: 650 }}>
                  <TableHead>
                    <TableRow>
                      {Object.keys(data[0]).map((key) => (
                        <TableCell 
                          key={key}
                          sx={{
                            fontWeight: "bold",
                            fontSize: "0.95rem",
                            bgcolor: "#333333",
                            color: "#fff",
                            borderBottom: "2px solid #444",
                            py: 2.5,
                            px: { xs: 2, sm: 3 },
                            position: "sticky",
                            top: 0,
                            zIndex: 10,
                            whiteSpace: "nowrap",
                            minWidth: "120px",
                            letterSpacing: "0.02em",
                          }}
                        >
                          {key}
                        </TableCell>
                      ))}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.map((row, idx) => (
                      <TableRow 
                        key={idx}
                        sx={{
                          '&:nth-of-type(odd)': {
                            bgcolor: 'rgba(0, 0, 0, 0.02)',
                          },
                          '&:hover': {
                            bgcolor: '#f1f5f9',
                            transition: "background-color 0.2s ease-in-out"
                          }
                        }}
                      >
                        {Object.values(row).map((val, i) => (
                          <TableCell 
                            key={i}
                            sx={{ 
                              py: 1.5, 
                              px: { xs: 2, sm: 3 },
                              fontSize: "0.9rem",
                              borderBottom: "1px solid #f1f5f9",
                              whiteSpace: "nowrap",
                              minWidth: "120px",
                            }}
                          >
                            {typeof val === "boolean" ? (val ? "True" : "False") : val}
                          </TableCell>
                        ))}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Box>

            {/* Results Footer */}
            <Box sx={{ 
              px: { xs: 2, sm: 3 }, 
              py: 1.5, 
              bgcolor: "#f8fafc", 
              borderTop: "1px solid #e2e8f0"
            }}>
              <Typography variant="body2" sx={{ 
                color: "#64748b", 
                fontSize: "0.85rem",
                textAlign: "center"
              }}>
                Showing {data.length} rows
              </Typography>
            </Box>
          </Box>
        </Box>
      )}
        </Box>
      </Container>
    </>
  );
};

export default BusinessUserTab;