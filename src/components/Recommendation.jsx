import React, { useState, useEffect } from 'react';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import {
  Container,
  Typography,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Paper,
  Snackbar,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert as MuiAlert,
  Grid,
  Box,
  AppBar,
  Toolbar,
  IconButton,
  CssBaseline,
  CircularProgress,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import { styled } from '@mui/material/styles';
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
import { subDays, format } from 'date-fns';

// API Configuration for recommendation
const API_BASE_URL = 'http://10.91.12.22:8000';
const API_CONFIG = {
  headers: {
    'Content-Type': 'application/json',
  },
};

// API request handler
const makeApiRequest = async (endpoint, options = {}) => {
  const config = {
    ...API_CONFIG,
    ...options,
    headers: {
      ...API_CONFIG.headers,
      ...options.headers,
    },
  };

  try {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
    }
    return await response.json();
  } catch (error) {
    console.error(`API request failed for ${endpoint}:`, error);
    throw error;
  }
};

// Styled components
const SummaryBox = styled(Box)(({ theme }) => ({
  margin: theme.spacing(3),
  padding: theme.spacing(2),
  backgroundColor: '#e3f2fd',
  borderRadius: '8px',
  textAlign: 'center', // Center text in summary box
}));

const TimeRangeBox = styled(Box)(({ theme }) => ({
  margin: theme.spacing(3),
  padding: theme.spacing(3.5),
  backgroundColor: '#defaec',
  borderRadius: '8px',
  textAlign: 'center', // Center text in summary box
}));

const CategoriesContainer = styled(Box)({
  display: 'flex',
  justifyContent: 'space-between', // Adjusts spacing between boxes
});

const StyledButton = styled(Button)(({ theme }) => ({
  backgroundColor: "theme.palette.primary.main",
  color: '#fff',
  '&:hover': {
    backgroundColor: '#6c72e8',
  },
}));

const Recommendation = (props) => {
  const [viewReportLoading, setViewReportLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(5);
  const [recommendationsReport, setRecommendationsReport] = useState([]);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [currentRecommendation, setCurrentRecommendation] = useState(null);
  // State for time range
  const [startTime, setStartTime] = useState(subDays(new Date(), 1));
  const [endTime, setEndTime] = useState(new Date());
  const [loading, setLoading] = useState(false);
  const [schemaAnalyzed, setSchemaAnalyzed] = useState(0);
  const [queriesAnalyzed, setQueriesAnalyzed] = useState(0);
  const [recommendationCount, setRecommendationCount] = useState(0);
  const [error, setError] = useState(null);
  const [showTable, setShowTable] = useState(false);
  const [projectId, setProjectId] = useState('');
  const [ruleDataDialogOpen, setRuleDataDialogOpen] = useState(false);
  const [ruleData, setRuleData] = useState(null);
  const [fetchingRuleData, setFetchingRuleData] = useState(false);
  const [ruleDataPage, setRuleDataPage] = useState(0);
  const [ruleDataRowsPerPage, setRuleDataRowsPerPage] = useState(10);
  const [totalRuleDataCount, setTotalRuleDataCount] = useState(0);
  const [currentRuleId, setCurrentRuleId] = useState(null);
  const [currentRuleRecommendation, setCurrentRuleRecommendation] = useState('');

  const fetchRecommendations = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await makeApiRequest('/get-recommendations', {
        method: 'POST',
        body: JSON.stringify({ market: 'US', project_name: props.selectedProject })
      });
      console.log('API Response:', data); // Debug log
      const projectIdFromApi = data.project_id // Default to example if not provided
      console.log('Project ID:', data.project_id); // Debug log
      console.log('Setting Project ID:', projectIdFromApi); // Debug log
      setProjectId(projectIdFromApi);
      if (projectIdFromApi) {
        sessionStorage.setItem('projectId', projectIdFromApi);
      }
      
      const transformedData = data.data.map((item) => ({
        rule_title: item.rule_title,
        recommendation: item.recommendation,
        optimizationCategory: item.optimization_category,
        referenceQuery: item.reference_query,
        queryCount: item.query_count,
        referenceQueryId: item.reference_query_id,
        ruleId: item.rule_id,
        queryChange: item.query_or_code_change_required === 'YES' ? 'Yes' : (item.query_or_code_change_required === 'NO' ? 'No' : item.query_or_code_change_required),
        schemaChange: item.schema_change_required ? "Yes" : "No",
        sampleQuery: item.sample_query || "NA",
        recommendedQuery: item.recommended_query || "NA",
      }));
      
      setRecommendationsReport(transformedData);
      setRecommendationCount(transformedData.length);
    } catch (err) {
      console.error("Error fetching recommendations:", err);
      setError("Failed to load recommendations. Please try again later.");
      setSnackbarMessage(err.message || "Failed to load recommendations. Please try again later.");
      setSnackbarOpen(true);
    } finally {
      setLoading(false);
    }
  };

  const fetchSummaryCounts = async () => {
    try {
      const market = 'US';
      const [schemaRes, queriesRes] = await Promise.all([
        makeApiRequest('/get-total-schemas', {
          method: 'POST',
          body: JSON.stringify({ market: 'US' })
        }),
        makeApiRequest('/get-total-query-scanned', {
          method: 'POST',
          body: JSON.stringify({ market: 'US' })
        })
      ]);
      setSchemaAnalyzed(schemaRes.data.total_schemas || 0);
      setQueriesAnalyzed(queriesRes.data?.total_query_scanned|| 0);
    } catch (err) {
      console.error('Error fetching summary counts:', err);
      setSnackbarMessage('Failed to load summary data');
      setSnackbarOpen(true);
    }
  };

  useEffect(() => {
    fetchRecommendations();
    fetchSummaryCounts();
  }, []);

  // Helper to convert timestamp to "x days/months ago"
function formatLastUsed(timestamp) {
  if (!timestamp) return "N/A"; // Handle null or undefined timestamps

  const now = new Date();
  const lastUsedDate = new Date(timestamp);
  const diffTime = Math.abs(now - lastUsedDate);
  const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));

  if (diffDays < 1) return "Today";
  if (diffDays === 1) return "1 day ago";
  if (diffDays < 30) return `${diffDays} days ago`;

  const months = Math.floor(diffDays / 30);
  return `${months} Month${months > 1 ? "s" : ""} ago`;
}

  useEffect(() => {
    // Set the end time to the current time when the component mounts
    const now = new Date();
    setStartTime(subDays(new Date(), 1));
  }, []);

  const handleApplyRecommendation = (rec) => {
    setCurrentRecommendation(rec);
    setDialogOpen(true);
    setLoading(true); // Set loading to true when the button is clicked
    // Simulate processing delay (e.g., 2 seconds)
    setTimeout(() => {
      setLoading(false); // Stop loading after delay
    }, 2000);
  };

  const handleCloseSnackbar = () => {
    setSnackbarOpen(false);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
  };

  const handleConfirmApply = () => {
    const recommendedQuery = `File Saved at: optimised/${currentRecommendation.files}`;
    setSnackbarMessage(recommendedQuery);
    setSnackbarOpen(true);
    handleCloseDialog();
  };

  const handleViewReportClick = async () => {
    setViewReportLoading(true);
    try {
      await fetchRecommendations();
      setSnackbarMessage('Recommendations refreshed successfully');
      setSnackbarOpen(true);
    } catch (err) {
      setSnackbarMessage('Failed to refresh recommendations');
      setSnackbarOpen(true);
    } finally {
      setViewReportLoading(false);
    }
  };

  const handleViewReportData = () => {
    setShowTable(!showTable);
  };

  // Fetch data on component mount
  useEffect(() => {
    fetchSummaryCounts();
  }, []); // Empty dependency array ensures this runs only once on mount


  // Handle page change
  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  // Handle rows per page change
  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0); // Reset to the first page
  };

  const fetchRuleDetails = async (ruleId, pageNum) => {
    setFetchingRuleData(true);
    setRuleData(null);
    try {
      const response = await makeApiRequest(`/rule`, {
        method: 'POST',
        body: JSON.stringify({
          rule_id: String(ruleId),
          market: 'US',
          project_name: props.selectedProject,
          page: pageNum + 1,
          page_size: ruleDataRowsPerPage
        }),
      });
      setRuleData(Array.isArray(response.data) ? response.data : (response.data ? [response.data] : []));
      setTotalRuleDataCount(response.total_count || (Array.isArray(response.data) ? response.data.length : (response.data ? 1 : 0)));
      setRuleDataDialogOpen(true);
    } catch (err) {
      console.error('Error fetching rule data:', err);
      setSnackbarMessage(err.message || 'Failed to fetch rule details.');
      setSnackbarOpen(true);
    } finally {
      setFetchingRuleData(false);
    }
  };

  const handleQueryCountClick = async (ruleId, recommendation) => {
    setCurrentRuleRecommendation(recommendation);
    setCurrentRuleId(ruleId);
    setRuleDataPage(0);
    setRuleDataRowsPerPage(10);
    await fetchRuleDetails(ruleId, 0);
  };

  // Handle page change for rule data dialog
  const handleChangeRuleDataPage = (event, newPage) => {
    setRuleDataPage(newPage);
  };

  // Handle rows per page change for rule data dialog
  const handleChangeRuleDataRowsPerPage = (event) => {
    setRuleDataRowsPerPage(parseInt(event.target.value, 10));
    setRuleDataPage(0); // Reset to the first page
  };

  useEffect(() => {
    if (ruleDataDialogOpen && currentRuleId !== null) {
      // Only fetch if the dialog is open and a rule ID is set
      fetchRuleDetails(currentRuleId, ruleDataPage);
    }
  }, [ruleDataPage, ruleDataRowsPerPage, ruleDataDialogOpen, currentRuleId]);

  // Slice the data based on the current page and rows per page
  const emptyRows = page > 0 ? Math.max(0, (1 + page) * rowsPerPage - recommendationsReport.length) : 0;
  const displayRows = recommendationsReport.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  const displayRuleDataRows = ruleData ? ruleData.slice(ruleDataPage * ruleDataRowsPerPage, ruleDataPage * ruleDataRowsPerPage + ruleDataRowsPerPage) : [];

  return (
    <Container
      maxWidth={false}
      disableGutters
      sx={{
        width: "99vw",
        margin: 0,
        padding: 0,
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
      }}
    >
      <CssBaseline />
      
      <Grid container spacing={2} sx={{ mt: 0, padding: 0, margin: 0, width: '100vw' }}>
        {/* Analysis Summary Section */}
        <Grid item xs={12} sm={6.8} sx={{ alignSelf: 'flex-start' }}>
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
                backgroundColor: '#000000', // Background color for the header
                color: 'black',
              }}
            >
              <Typography
                variant="body2"
                sx={{
                  color: '#ffffff',
                  fontWeight: 'bold',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                }}
              >
                Analysis Summary
              </Typography>
            </Box>
            {/* SQL Content */}
            <Box
              sx={{
                p: 2,
                color: 'black',
                fontFamily: 'monospace',
                fontWeight: 'bold',
                fontSize: '0.875rem',
                whiteSpace: 'pre-wrap',
                overflowX: 'auto',
                height: 210,
              }}
            >
              <Grid container spacing={0} sx={{ mt: 1, padding: 0, margin: 0 }}>
                <Grid item xs={12} sm={4} sx={{ alignSelf: 'flex-start' }}>
                  <SummaryBox>
                    <Typography variant="h6">Schema Analyzed</Typography>
                    <Typography variant="h3">{schemaAnalyzed}</Typography>
                  </SummaryBox>
                </Grid>
                <Grid item xs={12} sm={4} sx={{ alignSelf: 'flex-start' }}>
                  <SummaryBox>
                    <Typography variant="h6">Queries Analyzed</Typography>
                    <Typography variant="h3">{queriesAnalyzed}</Typography>
                  </SummaryBox>
                </Grid>
                <Grid item xs={12} sm={4} sx={{ alignSelf: 'flex-start' }}>
                  <SummaryBox>
                    <Typography variant="h6">Recommendations</Typography>
                    <Typography variant="h3">{recommendationCount}</Typography>
                  </SummaryBox>
                </Grid>
              </Grid>
            </Box>
          </Box>
        </Grid>

        {/* Time Range Section */}
        <Grid item xs={12} sm={5} sx={{ alignSelf: 'flex-start' }}>
          <Box
            sx={{
              mb: 2,
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
                backgroundColor: '#000000', // Background color for the header
                color: 'black',
              }}
            >
              <Typography
                variant="body2"
                sx={{
                  color: 'white',
                  fontWeight: 'bold',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                }}
              >
                Time Range
              </Typography>
            </Box>
            {/* SQL Content */}
            <Box
              sx={{
                p: 2,
                color: 'black',
                fontFamily: 'monospace',
                fontWeight: 'bold',
                fontSize: '0.875rem',
                whiteSpace: 'pre-wrap',
                overflowX: 'auto',
                height: 210,
              }}
            >
             <Grid container spacing={0} sx={{ mt: 1, padding: 0, margin: 0 }}>
  <Grid item xs={12} sm={6} sx={{ alignSelf: 'flex-start' }}>
    <TimeRangeBox>
      <Typography variant="h6">Start Time</Typography>
      <Typography variant="h5" sx={{ fontWeight: 'bold' }}>
        2025-06-25 00:00:00
      </Typography>
    </TimeRangeBox>
  </Grid>
  <Grid item xs={12} sm={6} sx={{ alignSelf: 'flex-start' }}>
    <TimeRangeBox>
      <Typography variant="h6">End Time</Typography>
      <Typography variant="h5" sx={{ fontWeight: 'bold' }}>
        2025-06-26 23:59:59
      </Typography>
    </TimeRangeBox>
  </Grid>
</Grid>

            </Box>
          </Box>
          <Box sx={{ mb: 2, display: 'flex', justifyContent: 'flex-end' }}>
            <LoadingButton
              loading={viewReportLoading}
              loadingPosition="start"
              startIcon={<PlayArrowIcon />}
              variant="contained"
              color="primary"
              onClick={() => {
                setShowTable(true);
                handleViewReportData();
              }}
              sx={{
                mb: 2,
                alignItems: 'center',
                typography: 'caption',
                
              }}
            >
              View Report
            </LoadingButton>
          </Box>
        </Grid>
      </Grid>

      {/* Recommendation Report Table */}

      {showTable && recommendationsReport.length > 0 && (
  <Grid container spacing={2} sx={{ mt: 1, padding: 1, margin: 0, width: '100vw', border: '1px solid #ccc' }}>
    <Grid item xs={12} sm={11.8} sx={{ alignSelf: 'flex-start' }}>
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
            backgroundColor: '#000000',
            color: 'black',
          }}
        >
          <Typography
            variant="body2"
            sx={{
              color: 'white',
              fontWeight: 'bold',
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
          >
            Recommendation Report
          </Typography>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <IconButton size="small">
              <ContentCopyOutlinedIcon sx={{ fontSize: 20, color: 'white' }} />
            </IconButton>
            <IconButton size="small">
              <DownloadForOfflineOutlinedIcon sx={{ fontSize: 20, color: 'white' }} />
            </IconButton>
          </Box>
        </Box>
        {/* Table with horizontal scroll */}
        
        
        <Box
          sx={{
            padding: '20px',
            backgroundColor: 'white',
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
          }}
        >
          
            <TableContainer sx={{ minWidth: 1200, border: '1px solid #ccc', borderRadius: '4px' }}>
              <Table stickyHeader>
                <TableHead
                  sx={{
                    '& .MuiTableCell-head': {
                      backgroundColor: '#f5f5f5',
                      color: 'black',
                    },
                  }}
                >
                  <TableRow>
                    <TableCell align="center" style={{ fontWeight: 'bold' }}>Rule</TableCell>
                    <TableCell align="center" style={{ fontWeight: 'bold' }}>Optimization Category</TableCell>
                    <TableCell align="center" style={{ fontWeight: 'bold' }}>Recommendation</TableCell>
                    <TableCell align="center" style={{ fontWeight: 'bold' }}>Query Count</TableCell>
                    <TableCell align="center" style={{ fontWeight: 'bold' }}>Query/Code Change Required</TableCell>
                    <TableCell align="center" style={{ fontWeight: 'bold' }}>Schema Change Required</TableCell>
                    {/* <TableCell align="center" style={{ fontWeight: 'bold' }}>Suggestion</TableCell> */}
                  </TableRow>
                </TableHead>
                <TableBody sx={{ overflowY: 'auto' }}>
                  {displayRows.map((rec, index) => (
                    <TableRow
                      key={index}
                      sx={{
                        '& .MuiTableCell-root': {
                          color: rec.improvementCategory === 'Cost-Optimization' ? 'green' : '#333333',
                        },
                      }}
                    >
                      <TableCell align="center">{rec.rule_title}</TableCell>
                      <TableCell align="center">{rec.optimizationCategory}</TableCell>
                      <TableCell align="center">{rec.recommendation}</TableCell>
                      <TableCell align="center">
                        <Button
                          variant="text"
                          onClick={() => window.open(`/query-details/${rec.ruleId}/${encodeURIComponent(rec.recommendation)}/${encodeURIComponent(rec.rule_title)}`, '_blank')}
                          sx={{ textTransform: 'none', color: 'blue', textDecoration: 'underline' }}
                        >
                          {rec.queryCount}
                        </Button>
                      </TableCell>
                     
                      <TableCell align="center">{rec.queryChange}</TableCell>
                      <TableCell align="center">{rec.schemaChange}</TableCell>
                      {/* <TableCell align="center">
                        <StyledButton
                          disabled={rec.query_or_code_change_required === 'NO'}
                          onClick={() => handleApplyRecommendation(rec)}
                          sx={{
                            backgroundColor: rec.query_or_code_change_required === 'NO' ? 'grey' : '#0096FF',
                            minWidth: '36px',
                            height: '36px',
                            borderRadius: '50%',
                            padding: 0,
                            color: '#fff',
                            '&:hover': {
                              backgroundColor: rec.query_or_code_change_required === 'NO' ? 'grey' : '#000000',
                            },
                          }}
                        >
                          <InfoOutlinedIcon fontSize="small" />
                        </StyledButton>
                      </TableCell> */}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
        </Box>

        {/* Pagination */}
        <Box sx={{ display: 'flex', justifyContent: 'right', mt: 2 }}>
          <TablePagination
            rowsPerPageOptions={[5, 10, 25, { label: 'All', value: -1 }]}
            component="div"
            count={recommendationsReport.length}
            rowsPerPage={rowsPerPage}
            page={page}
            onPageChange={handleChangePage}
            onRowsPerPageChange={handleChangeRowsPerPage}
          />
        </Box>
      </Box>
    </Grid>
  </Grid>)}



      {/* Snackbar and Dialog */}
      <Box sx={{ mt: 2, mb: 2, width: '100%', flexGrow: 1, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <Snackbar open={snackbarOpen} autoHideDuration={6000} onClose={handleCloseSnackbar}>
          <MuiAlert onClose={handleCloseSnackbar} severity="info">
            {snackbarMessage}
          </MuiAlert>
        </Snackbar>
        <Dialog open={dialogOpen} onClose={handleCloseDialog} fullWidth>
          <DialogTitle fontWeight="bold">Query Comparator</DialogTitle>
          <DialogContent>
            {loading ? (
              <Box display="flex" flexDirection="column" justifyContent="center" alignItems="center" minHeight="150px">
                <CircularProgress />
                <Typography variant="body1" fontWeight="bold" sx={{ mt: 2 }}>
                  Loading Suggestions...
                </Typography>
              </Box>
            ) : (
              currentRecommendation && (
                <>
                  <Typography variant="body1" color="error" fontWeight="bold">Sample Query:</Typography>
                  <Typography variant="body2" style={{ whiteSpace: 'pre-wrap' }} fontFamily="monospace">
                    {currentRecommendation.sampleQuery}
                  </Typography>
                  <Typography variant="body1" style={{ marginTop: '16px' }} color="success" fontWeight="bold">Recommendation:</Typography>
                  <Typography variant="body2" style={{ whiteSpace: 'pre-wrap' }} fontFamily="monospace">
                    {currentRecommendation.recommendedQuery}
                  </Typography>
                </>
              )
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={handleCloseDialog} color="primary">
              Close
            </Button>
            <Button onClick={handleConfirmApply} color="primary">
              Save
            </Button>
          </DialogActions>
        </Dialog>

      </Box>
    </Container>
  );
};

export default Recommendation;