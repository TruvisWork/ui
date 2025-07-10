import React, { useState, useRef, theme } from "react";
import {
  TextField,
  Button,
  Box,
  Typography,
  Paper,
  CircularProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Grid,
  Container,
  CssBaseline,
  AppBar,
  Toolbar,
  IconButton,
  Link,
  FormControl,
  InputLabel, 
  Select, 
  MenuItem,
  Dialog, DialogTitle, DialogContent, DialogActions,
  RadioGroup, FormControlLabel, Radio, FormLabel
} from "@mui/material";

import MenuIcon from '@mui/icons-material/Menu';
import { LoadingButton } from '@mui/lab';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import PedalBikeIcon from '@mui/icons-material/PedalBike';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import ListItemIcon from '@mui/material/ListItemIcon';
import StarIcon from '@mui/icons-material/Star';
import SendSharpIcon from '@mui/icons-material/SendSharp';
import DownloadForOfflineOutlinedIcon from '@mui/icons-material/DownloadForOfflineOutlined';
import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined';

const BusinessUserTab = () => {
  const [prompt, setPrompt] = useState("");
  const [generateLoading, setGenerateLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [optimizedQuery, setOptimizedQuery] = useState("");
  const [data, setData] = useState([]);
  const [insights, setInsights] = useState([]);
  const [inference, setInference] = useState("");
  const [nextPrompts, setNextPrompts] = useState([]);
    const [optimizeLoading, setOptimizeLoading] = useState(false);
    const [optimizeTimeout, setOptimizeTimeout] = useState(null);
  const [insightLoading, setInsightLoading] = useState(false);
  const [insightTimeout, setInsightTimeout] = useState(null);
  const textFieldRef = useRef(null);
  //const rowsPerPage = 5; // Number of rows per page
  const [selectedModel, setSelectedModel] = useState('openai');
  const [selectedDataCategory, setSelectedDataCategory] = useState('Market Data');
  const [selectedDataSubCategory, setSelectedDataSubCategory] = useState('Payments');
  const [selectedDatabaseType, setSelectedDatabaseType] = useState('BigQuery');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(5);

  const [optimizedQueryBeforeClick, setOptimizedQueryBeforeClick] = useState("");
  const [dataBeforeClick, setDataBeforeClick] = useState([]);
  const [insightsBeforeClick, setInsightsBeforeClick] = useState([]);
  const [nextPromptsBeforeClick, setNextPromptsBeforeClick] = useState([]);

  const [errorDialogOpen, setErrorDialogOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [errorPrompts, setErrorPrompts] = useState([]);
  const [selectedValue, setSelectedValue] = useState('');
  const [subscription, setSubscription] = useState('');
  const [generatedQuery, setGeneratedQuery] = useState("");

  const models = [
    { value: 'openai', label: 'openai' },
    { value: 'palm2', label: 'palm2' },
    { value: 'llama', label: 'llama' },
    { value: 'gemini-pro', label: 'gemini-pro' },
    { value: 'gemini-1.5-pro', label: 'gemini-1.5-pro' }
  ];

  const dataCategory = [
      { value: 'Market Data', label: 'Market Data' },
      { value: 'Finance Data', label: 'Finance Data' }
    ];

  const dataSubCategory = [
      { value: 'Payments', label: 'Payments' },
      { value: 'Cyber', label: 'Cyber' },
      { value: 'Cards', label: 'Cards' },
      { value: 'Frauds', label: 'Frauds' }
    ];

  const databaseType = [
      { value: 'BigQuery', label: 'BigQuery' },
      { value: 'SAS', label: 'SAS' }
    ];

  const handleChange = (event) => {
    const value = event.target.value;
    setSelectedModel(value);
    onModelSelect(value); // Callback to parent component
  };

    const handleDataCategoryChange = (event) => {
      const value = event.target.value;
      setSelectedDataCategory(value);
      onModelSelect(value); // Callback to parent component
    };

    const handleDataSubCategoryChange = (event) => {
      const value = event.target.value;
      setSelectedDataSubCategory(value);
      onModelSelect(value); // Callback to parent component
    };

    const handleDatabaseTypeChange = (event) => {
      const value = event.target.value;
      setSelectedDatabaseType(value);
      onModelSelect(value); // Callback to parent component
    };

  const handleFetchData = async () => {
    // Reset all data
    setQuery("");
    setOptimizedQuery("");
    setData([]);
    setDataBeforeClick([]);
    setInsights([]);
    setInsightsBeforeClick([]);
    setNextPrompts([]);
    setNextPromptsBeforeClick([]);


    setGenerateLoading(true);

    try {
      const response = await fetch("http://localhost:8082/generate_query", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          query: prompt, // Sending prompt as 'query'
          llm_type: "openai"
        }),
      });

      const apiData = await response.json();

      if (response.status === 400) {
        setErrorMessage(apiData.textual_summary?.[0] || "An error occurred.");
        setErrorPrompts(apiData.followup_prompts || []);
        setErrorDialogOpen(true);
        return;
      }

      if(!response.ok) {
        throw new Error('API request failed with status ${response.status}')
      }
      // Set the state with API response
      setQuery(apiData.sql_query_generated || "No query generated.");

    } catch (error) {
      console.error("Error fetching insights:", error);
      setQuery("Error generating query.");
    } finally {
      setGenerateLoading(false);
    }
  };

  const handleOptimizeData = async () => {
    setOptimizeLoading(true);
    try {
      const response = await fetch("http://localhost:8082/optimise_query", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          query: query, // Sending prompt as 'query'
          llm_type: "openai"
        }),
      });

      if(!response.ok) {
        throw new Error('API request failed with status ${response.status}')
      }

      const apiData = await response.json();

      // Set the state with API response
      // setQuery(apiData.sql_query_generated || "No query generated.");
      setOptimizedQuery(apiData.sql_query_generated || "No query generated.");
//         setData(apiData.result || []);
//         setInsights(apiData.textual_summary || []);
//         setNextPrompts(apiData.followup_prompts || []);

    } catch (error) {
      console.error("Error fetching insights:", error);
      setQuery("Error generating query.");
      setOptimizedQuery("Error generating query.");
//         setData([]);
//         setInsights("Fail to generate insights.");
//         setNextPrompts([]);
    } finally {
      setOptimizeLoading(false);
    }
  };

  const handleInsightData = async () => {
    setInsightLoading(true);
    try {
      const response = await fetch("http://localhost:8082/generate_insights_from_nl_query", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          query: prompt, // Sending prompt as 'query'
          bq_query: optimizedQuery,
          llm_type: "openai"
        }),
      });

      if(!response.ok) {
        throw new Error('API request failed with status ${response.status}')
      }

      const apiData = await response.json();

      // Set the state with API response
      // setQuery(apiData.sql_query_generated || "No query generated.");
      setOptimizedQuery(apiData.sql_query_optimised || "No query generated.");
      setData(apiData.result || []);
      setInsights(apiData.textual_summary || []);
      setNextPrompts(apiData.followup_prompts || []);

    } catch (error) {
      console.error("Error fetching insights:", error);
      setQuery("Error generating query.");
//       setOptimizedQuery("Error generating query.");
      setData([]);
      setInsights("Fail to generate insights.");
      setNextPrompts([]);
    } finally {
      setInsightLoading(false);
    }
  };

  const handleGenerateClick = () => {
    setGenerateLoading(true);
    // Simulate an async operation
    setTimeout(() => {
      setGenerateLoading(false);
    }, 2000); // 2 seconds delay
  };

  const handleInsightClick = () => {
    // setInsightLoading(true);
    // Simulate an async operation
    setTimeout(() => {
      setInsightLoading(true);
    }, 2000); // 2 seconds delay
  };

  const handleLinkClick = (text) => {
      // Populate the TextField with the clicked link
      setPrompt(text);
      setTimeout(() => {
        if (textFieldRef.current) {
          textFieldRef.current.focus();

          // Select all text in the TextField
          textFieldRef.current.select();
        }
      }, 100);
  };

  const handleTextChange = (e) => {
    setTextContent(e.target.value);
  };

  // Handle page change
  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  // Handle rows per page change
  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0); // Reset to the first page
  };

  // Slice the data based on the current page and rows per page
  const emptyRows = page > 0 ? Math.max(0, (1 + page) * rowsPerPage - data.length) : 0;
  const displayRows = data.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  return (
   <Container maxWidth={false} disableGutters sx={{ width: "99vw", margin: 0, padding: 0,  minHeight: "100vh", display: "flex", flexDirection: "column" }}> {/* Center the content */}
     <CssBaseline />
      <Box  sx={{ mt: 2, mb: 2, width: '100%', alignItems: 'center' }}>
        <Grid container spacing={2} sx={{ mt: 2 }}>
                <Grid item xs={12} sm={7} sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', textAlign: 'center' }}>
                  <TextField
                    inputRef={textFieldRef}
                    autoFocus
                    fullWidth
                    label="How can I help you ?"
                    variant="outlined"
                    value={prompt}
                    onChange={(e) => setPrompt(e.target.value)}
                    sx={{ backgroundColor: "white",  }}
                  />
                </Grid>
                <Grid item xs={12} sm={5} sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', textAlign: 'center' }}>
                  <FormControl size="small" sx={{ m: 1, width: '120px' }} disabled>
                    <InputLabel id="model-select-label">LLM</InputLabel>
                    <Select
                      labelId="model-select-label"
                      id="model-select"
                      value={selectedModel}
                      label="AI Model"
                      onChange={handleChange}
                    >
                      {models.map((model) => (
                        <MenuItem key={model.value} value={model.value}>
                          {model.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>

                  <FormControl size="small" sx={{ m: 1, width: '150px' }}>
                    <InputLabel id="data-category-select-label">Domain</InputLabel>
                    <Select
                      labelId="data-category-select-label"
                      id="data-category-select"
                      value={selectedDataCategory}
                      label="Domain"
                      onChange={handleDataCategoryChange}
                    >
                      {dataCategory.map((data) => (
                        <MenuItem key={data.value} value={data.value}>
                          {data.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>

                  <FormControl size="small" sx={{ m: 1, width: '150px' }}>
                    <InputLabel id="data-sub-category-select-label">Sub-Domain</InputLabel>
                    <Select
                      labelId="data-sub-category-select-label"
                      id="data-sub-category-select"
                      value={selectedDataSubCategory}
                      label="Sub-Domain"
                      onChange={handleDataSubCategoryChange}
                    >
                      {dataSubCategory.map((data) => (
                        <MenuItem key={data.value} value={data.value}>
                          {data.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>

                    <FormControl size="small" sx={{ m: 1, width: '150px' }}>
                      <InputLabel id="database-type-select-label">Database</InputLabel>
                      <Select
                        labelId="database-type-select-label"
                        id="database-type-select"
                        value={selectedDatabaseType}
                        label="Database-Type"
                        onChange={handleDatabaseTypeChange}
                      >
                        {databaseType.map((data) => (
                          <MenuItem key={data.value} value={data.value}>
                            {data.label}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>

                </Grid>
                <Grid item xs={12} sm={1} sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', textAlign: 'center' }}>
                  <LoadingButton
                    loading={generateLoading}
                    loadingPosition="start"
                    startIcon={<PlayArrowIcon />}
                    variant="contained"
                    color="primary"
                    onClick={handleFetchData}
                    sx={{ mb: 2, alignItems: 'center', typography: 'caption' }}
                  >
                    Generate
                  </LoadingButton>
                </Grid>
              </Grid>
      </Box>


{/*       {loading && <CircularProgress sx={{ mt: 2 }} />} */}

      {query && (
        <Grid container spacing={2} sx={{ mt: 1, padding: 1, margin: 0, width: '100vw', border: '1px solid #ccc'}}>
          <Grid item xs={12} sm={6} sx={{ alignSelf: 'flex-start'}}>
            <Box
                  sx={{
                    width: '100%',

                    borderRadius: 1,
                    overflow: 'hidden',
                    boxShadow: 1,
                    bgcolor: 'background.paper',
                  }}
                >
                  {/* Header */}
                  <Box
                    sx={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      px: 2,
                      py: 1,
                      borderBottom: 1,
                      borderColor: 'divider',
                      backgroundColor: '#eaecec', // Background color for the header
                      color: 'black',
                    }}
                  >
                    <Typography
                      variant="body2"
                      sx={{
                        color: 'text.secondary',
                        fontWeight: 'bold',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 1,
                      }}
                    >
                      Generated Query
                    </Typography>
                    <Box sx={{ display: 'flex', gap: 1 }}>
                      <IconButton size="small">
                        <ContentCopyOutlinedIcon sx={{ fontSize: 20 }} />
                      </IconButton>
                      <IconButton size="small">
                        <DownloadForOfflineOutlinedIcon sx={{ fontSize: 20 }} />
                      </IconButton>
                    </Box>
                  </Box>

                  {/* SQL Content */}
                  <Box
                    sx={{
                      p: 2,
                      //backgroundColor: (theme) => theme.palette.primary.dark, // Dark blue background
                      backgroundColor: '#bbdffc',
                      color: 'black',
                      fontFamily: 'monospace',
                      fontWeight: 'bold',
                      fontSize: '0.875rem',
                      whiteSpace: 'pre-wrap',
                      overflowX: 'auto',
                    }}
                  >
                  <TextField
                      fullWidth
                      multiline
                      label="Editable Text"
                      variant="outlined"
                      value={query}
                      onChange={(e) => setQuery(e.target.value)}
                      sx={{ mb: 2, backgroundColor: "white", borderRadius: 1 }}
                      InputProps={{
                        style: {
                          fontFamily: 'monospace',
                          fontWeight: 'bold',
                          fontSize: '0.875rem',
                        },
                      }}
                    />
{/*                     {query} */}
                  </Box>
                </Box>
                <Box sx={{ p:2, display: 'flex', justifyContent: 'flex-end' }}>
                <LoadingButton loading={optimizeLoading} loadingPosition="start" startIcon={<PlayArrowIcon />} variant="contained" color="primary" onClick={handleOptimizeData} sx={{ alignSelf: 'flex-end', typography: 'caption'}}>
                                                Optimize
                </LoadingButton>
                </Box>
          </Grid>


{/*           { optimizedQuery && insights.length > 0 && ( */}
            { optimizedQuery && (
              <Grid item xs={12} sm={6} sx={{ alignSelf: 'flex-start'}}>
                <Box
                      sx={{
                        width: '100%',
                        maxWidth: '900px',
                        borderRadius: 1,
                        overflow: 'hidden',
                        boxShadow: 1,
                        bgcolor: 'background.paper',
                      }}
                    >
                      {/* Header */}
                      <Box
                        sx={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          px: 2,
                          py: 1,
                          borderBottom: 1,
                          borderColor: 'divider',
                          backgroundColor: '#eaecec', // Background color for the header
                          color: 'black',
                        }}
                      >
                        <Typography
                          variant="body2"
                          sx={{
                            color: 'text.secondary',
                            fontWeight: 'bold',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 1,
                          }}
                        >
                          Optimized Query
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 1 }}>
                          <IconButton size="small">
                            <ContentCopyOutlinedIcon sx={{ fontSize: 20 }} />
                          </IconButton>
                          <IconButton size="small">
                            <DownloadForOfflineOutlinedIcon sx={{ fontSize: 20 }} />
                          </IconButton>
                        </Box>
                      </Box>

                      {/* SQL Content */}
                      <Box
                        sx={{
                          p: 2,
                          //backgroundColor: (theme) => theme.palette.primary.dark, // Dark blue background
                          backgroundColor: '#defaec',
                          color: 'black',
                          fontFamily: 'monospace',
                          fontWeight: 'bold',
                          fontSize: '0.875rem',
                          whiteSpace: 'pre-wrap',
                          overflowX: 'auto',
                        }}
                      >
                      <TextField
                        fullWidth
                        multiline
                        label="Editable Text"
                        variant="outlined"
                        value={optimizedQuery}
                        onChange={(e) => setOptimizedQuery(e.target.value)}
                        sx={{ mb: 2, backgroundColor: "white", borderRadius: 1 }}
                        InputProps={{
                          style: {
                            fontFamily: 'monospace',
                            fontWeight: 'bold',
                            fontSize: '0.875rem',
                          },
                        }}
                      />
{/*                         {optimizedQuery} */}
                      </Box>
                    </Box>
                    <Box sx={{ p:2, display: 'flex', justifyContent: 'flex-end' }}>
                    <LoadingButton loading={insightLoading} loadingPosition="start" startIcon={<PlayArrowIcon />} variant="contained" color="primary" onClick={handleInsightData} sx={{ alignSelf: 'flex-end', typography: 'caption'}}>
                                                    Get Insights
                    </LoadingButton>
                    </Box>
              </Grid>
          )}
      </Grid>

      )}

    {  insights.length > 0 && (

      <Grid container bgcolor='white' spacing={2} sx={{ mt: 1, padding: 1, margin: 0, width: '100vw', border: '1px solid #ccc', height: '700px'}}>
                <Grid item xs={12} sm={6}>
                      {data.length > 0 && (
                          <Box
                              sx={{
                                width: '100%',
                                maxWidth: '900px',
                                borderRadius: 1,
                                overflow: 'hidden',
                                boxShadow: 1,
                                bgcolor: 'background.paper',
                              }}
                          >
                              {/* Header */}
                          <Box
                            sx={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'center',
                              px: 2,
                              py: 1,
                              borderBottom: 1,
                              borderColor: 'divider',
                              backgroundColor: '#eaecec', // Background color for the header
                              color: 'black',
                            }}
                          >
                            <Typography
                              variant="body2"
                              sx={{
                                color: 'text.secondary',
                                fontWeight: 'bold',
                                display: 'flex',
                                alignItems: 'center',
                                gap: 1,
                              }}
                            >
                              Retrieved Data
                            </Typography>
                            <Box sx={{ display: 'flex', gap: 1 }}>
                              <IconButton size="small">
                                <ContentCopyOutlinedIcon sx={{ fontSize: 20 }} />
                              </IconButton>
                              <IconButton size="small">
                                <DownloadForOfflineOutlinedIcon sx={{ fontSize: 20 }} />
                              </IconButton>
                            </Box>
                          </Box>
                              <Box sx={{
                                           padding: '20px', // Adjust the padding value as needed
      //                                      border: '1px solid #ccc', // Optional: Adds a border for better visualization
      //                                      borderRadius: '4px', // Optional: Rounds the corners
                                           backgroundColor: 'white', // Optional: Adds a background color
                                           display: 'flex', // Optional: Aligns content inside the box
                                           justifyContent: 'center', // Optional: Centers content horizontally
                                           alignItems: 'center', // Optional: Centers content vertically
      //                                      width: '700px', // Optional: Sets the width of the box
      //                                      height: '200px', // Optional: Sets the height of the box
                                         }}
                                     >
                                <TableContainer  sx={{ border: '1px solid #ccc', borderRadius: '4px', overflow: 'auto' }}>
                                  <Table stickyHeader>
                                    <TableHead sx={{
                                       '& .MuiTableCell-head': {
                                         backgroundColor: '#f5f5f5', // Background color for the header
                                         color: 'black', // Text color for the header
                                       },
                                     }}>
                                      <TableRow>
                                        {Object.keys(data[0]).map((key) => (
                                          <TableCell key={key} sx={{ fontWeight: "bold", textTransform: "capitalize", borderRight: '1px solid #ccc' }}>
                                            {key.replace(/([A-Z])/g, " $1").trim()} {/* Formats camelCase to readable text */}

                                          </TableCell>
                                        ))}

                                      </TableRow>
                                    </TableHead>

                                    <TableBody sx={{  overflowY: 'auto' }} >
                                    {displayRows.map((row, index) => (
                                      <TableRow key={index}>
                                          {Object.keys(row).map((key) => (
                                            <TableCell sx={{ borderRight: '1px solid #ccc' }} key={key}>{row[key]}</TableCell>
                                          ))}
                                      </TableRow>
                                    ))}
                                    {emptyRows > 0 && (
                                      <TableRow style={{ height: 53 * emptyRows }}>
                                        <TableCell colSpan={5} />
                                      </TableRow>
                                    )}
                                  </TableBody>


      {/*                               <TableBody sx={{ border: '1px solid #ccc' }}> */}
      {/*                                 {data.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage).map((row, index) => ( */}
      {/*                                   <TableRow key={index}> */}
      {/*                                     {Object.keys(row).map((key) => ( */}
      {/*                                       <TableCell sx={{ borderRight: '1px solid #ccc' }} key={key}>{row[key]}</TableCell> */}
      {/*                                     ))} */}
      {/*                                   </TableRow> */}
      {/*                                 ))} */}
      {/*                               </TableBody> */}
                                  </Table>
                                </TableContainer>



                                </Box>
                                    <TablePagination
                                          rowsPerPageOptions={[5, 10, 25, { label: 'All', value: -1 }]}
                                          component="div"
                                          count={data.length}
                                          rowsPerPage={rowsPerPage}
                                          page={page}
                                          onPageChange={handleChangePage}
                                          onRowsPerPageChange={handleChangeRowsPerPage}
                                    />
      {/*                           <Box sx={{ display: "flex", justifyContent: "center", mt: 2 }}> */}
      {/*                             {Array.from({ length: Math.ceil(data.length / rowsPerPage) }, (_, index) => ( */}
      {/*                               <Button */}
      {/*                                 key={index} */}
      {/*                                 variant={page === index ? "contained" : "outlined"} */}
      {/*                                 onClick={() => setPage(index)} */}
      {/*                                 sx={{ mx: 0.5 }} */}
      {/*                               > */}
      {/*                                 {index + 1} */}
      {/*                               </Button> */}
      {/*                             ))} */}
      {/*                           </Box> */}
                             </Box>
                      )}
      {/*                 {data.length > 0 && ( */}
      {/*                 <Button variant="contained" color="primary" onClick={handleInsightData} sx={{ mt: 2 }}> */}
      {/*                   Get Insights */}
      {/*                 </Button> */}
      {/*               )} */}
                </Grid>

                <Grid item xs={12} sm={5.9} sx={{ alignSelf: 'flex-start'}}>
                    <Box
                      sx={{
                        width: '100%',
                        mb: 2,
                        borderRadius: 1,
                        overflow: 'hidden',
                        boxShadow: 1,
                        bgcolor: 'background.paper',
                      }}
                    >
                      {/* Header */}
                      <Box
                        sx={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          px: 2,
                          py: 1,
                          borderBottom: 1,
                          borderColor: 'divider',
                          backgroundColor: '#eaecec', // Background color for the header
                          color: 'black',
                        }}
                      >
                        <Typography
                          variant="body2"
                          sx={{
                            color: 'text.secondary',
                            fontWeight: 'bold',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 1,
                          }}
                        >
                          Insights
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 1 }}>
                          <IconButton size="small">
                            <ContentCopyOutlinedIcon sx={{ fontSize: 20 }} />
                          </IconButton>
                          <IconButton size="small">
                            <DownloadForOfflineOutlinedIcon sx={{ fontSize: 20 }} />
                          </IconButton>
                        </Box>
                      </Box>

                      {/* SQL Content */}
                      <Box
                        sx={{
                          p: 2,

                          color: 'black',
                          //fontFamily: 'monospace',
                          fontSize: '0.875rem',
                          whiteSpace: 'pre-wrap',
                          overflowX: 'auto',
                        }}
                      >
                        {insights.map((p, index) => (
                            <List sx={{ padding: 0 }}>
                               <ListItem sx={{ py: 0.5 }}>
                                   <ListItemIcon >
                                   <SendSharpIcon sx={{ fontSize: 'large' }} />
                                   </ListItemIcon>
                                    <Typography variant="body2" component="span">
                                         {p}
                                    </Typography>

                               </ListItem>
                            </List>
                        ))}
                      </Box>
                    </Box>

                    { nextPrompts.length > 0 && (
                    <Box
                      sx={{
                        width: '100%',
                        mb: 2,
                        borderRadius: 1,
                        overflow: 'hidden',
                        boxShadow: 1,
                        bgcolor: 'background.paper',
                      }}
                    >
                      {/* Header */}
                      <Box
                        sx={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          px: 2,
                          py: 1,
                          borderBottom: 1,
                          borderColor: 'divider',
                          backgroundColor: '#eaecec', // Background color for the header
                          color: 'black',
                        }}
                      >
                        <Typography
                          variant="body2"
                          sx={{
                            color: 'text.secondary',
                            fontWeight: 'bold',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 1,
                          }}
                        >
                          Suggested Next Prompts:
                        </Typography>
                        <Box variant="subtitle2" sx={{ display: 'flex', gap: 1 }}>
{/*                           <IconButton size="small"> */}
{/*                             <ContentCopyOutlinedIcon sx={{ fontSize: 20 }} /> */}
{/*                           </IconButton> */}
{/*                           <IconButton size="small"> */}
{/*                             <PedalBikeIcon sx={{ fontSize: 18 }} /> */}
{/*                           </IconButton> */}
                        </Box>
                      </Box>

                      {/* SQL Content */}
                      <Box
                        sx={{
                          p: 2,

                          color: 'black',
                          //fontFamily: 'monospace',
                          fontSize: '0.875rem',
                          whiteSpace: 'pre-wrap',
                          overflowX: 'auto',
                        }}
                      >

                          { nextPrompts.map((p, index) => (

                              <List sx={{ padding: 0 }}>
                                 <ListItem sx={{ py: 0.5 }}>
                                     <ListItemIcon >

                                             <SendSharpIcon sx={{ fontSize: 'large' }} />
                                     </ListItemIcon>
                                      <Typography key={index}  variant="body1" component="span">
                                           <Link
                                               align="left"
                                               component="button"
                                               color="primary"
                                               underline="hover"
                                               onClick={() => handleLinkClick(p)}
                                               sx={{
                                                 typography: 'body1',

                                                 cursor: 'pointer'
                                               }}
                                             >
                                           {p}
                                        </Link>
                                      </Typography>

                                 </ListItem>
                              </List>

                          ))}

                      </Box>

                    </Box>
                  )}
                </Grid>

      </Grid>

      )}
      <Dialog open={errorDialogOpen} onClose={() => setErrorDialogOpen(false)}>
        <DialogTitle fontWeight="bold">Query Error</DialogTitle>
        <DialogContent>
          <Typography variant="body1" color="error">{errorMessage}</Typography>
            {errorPrompts.length > 0 && (
              <>
                <Typography variant="h6" sx={{ mt: 2 }}>Sample Analytical Prompts:</Typography>
                <Box component="ul" sx={{ pl: 2 }}> {/* Ensures bullet points are visible */}
                  {errorPrompts.map((prompt, index) => (
                    <Box key={index} sx={{ mb: 1 }}>
                      <Typography variant="body2">{prompt}</Typography>
                    </Box>
                  ))}
                </Box>
              </>
            )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setErrorDialogOpen(false)} color="primary">
            Close
          </Button>
        </DialogActions>
      </Dialog>


    </Container>
  );
};

export default BusinessUserTab;
