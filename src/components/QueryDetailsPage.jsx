import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Container, Typography, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Paper, CircularProgress, TablePagination, Box
} from '@mui/material';
import { styled } from '@mui/material/styles';
import hsbcLogo from '../assets/hsbc-logo.png';

const API_BASE_URL = 'http://10.91.12.22:8000';

// Internal API request handler
const makeApiRequest = async (endpoint, options = {}) => {
  const config = {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
  };
  try {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    return await response.json();
  } catch (error) {
    console.error(`API request failed for ${endpoint}:`, error);
    throw error;
  }
};

// Styled components
const HeaderBanner = styled(Box)(({ theme }) => ({
  backgroundColor: '#db0011', // HSBC red
  color: 'white',
  padding: theme.spacing(1),
  paddingRight: theme.spacing(4),
  paddingLeft: theme.spacing(4),
  marginBottom: theme.spacing(2),
  boxShadow: '0 4px 6px rgba(0, 0, 0, 0.1)',
  display: 'flex',
  alignItems: 'center',
}));

const QueryDetailsSection = styled(Box)(({ theme }) => ({
  background: '#fff',
  padding: theme.spacing(3, 4),
  margin: theme.spacing(3, 4, 3, 4),
  border: '1.5px solid #e0e0e0',
  borderRadius: '8px',
  boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'flex-start',
}));

const StyledTableContainer = styled(TableContainer)(({ theme }) => ({
  margin: theme.spacing(2),
  boxShadow: '0 4px 6px rgba(0, 0, 0, 0.1)',
  '& .MuiTable-root': {
    borderCollapse: 'separate',
    borderSpacing: 0,
  },
}));

const StyledTableCell = styled(TableCell)(({ theme, component, isquery, align }) => ({
  ...(component === 'th' && {
    backgroundColor: '#333333',
    color: 'white',
    fontWeight: 'bold',
    fontSize: '1rem',
  }),
  borderBottom: '1px solid rgba(224, 224, 224, 1)',
  paddingTop: theme.spacing(2),
  paddingBottom: theme.spacing(2),
  paddingLeft: isquery ? theme.spacing(4) : theme.spacing(1),
  paddingRight: isquery ? theme.spacing(4) : theme.spacing(1),
  textAlign: align || 'left',
  verticalAlign: 'top',
}));

const StyledTableRow = styled(TableRow)(({ theme }) => ({
  '&:nth-of-type(odd)': {
    backgroundColor: '#fafbfc',
  },
  '&:hover': {
    backgroundColor: '#f0f4f8',
    boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
    zIndex: 1,
  },
  height: 56,
}));

const CodeBlock = styled('pre')(({ theme }) => ({
  background: '#f6f8fa',
  color: '#1a237e',
  fontFamily: 'Fira Mono, Menlo, Monaco, Consolas, monospace',
  fontSize: '0.97rem',
  borderRadius: 6,
  border: '1px solid #e0e0e0',
  padding: theme.spacing(2),
  margin: 0,
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-word',
  lineHeight: 1.5,
}));

const QueryDetailsPage = () => {
  const { ruleId, recommendation, ruleTitle } = useParams();
  const ruleIdNum = Number(ruleId);
  const [ruleData, setRuleData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(5);
  const [totalCount, setTotalCount] = useState(0);
  const [error, setError] = useState(null);
  const [projectName, setProjectName] = useState('hsbc-12010598-fdrasp-dev');

  const API_CONFIG = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const fetchQueryDetails = async () => {
    setLoading(true);
    setError(null);
    try {
      const project_name = 'hsbc-12010598-fdrasp-dev';
      const data = await makeApiRequest('/rule', {
        method: 'POST',
        body: JSON.stringify({
          rule_id: ruleId,
          market: 'US',
          page: page + 1,
          project_name,
          page_size: rowsPerPage,
        })
      });
      setRuleData(Array.isArray(data.data) ? data.data : []);
      setTotalCount(data.total_count || (Array.isArray(data.data) ? data.data.length : 0));
      if (Array.isArray(data.data) && data.data.length > 0 && data.data[0].project_name) {
        setProjectName(data.data[0].project_name);
      } else {
        setProjectName(project_name);
      }
    } catch (err) {
      console.error('Error fetching query details:', err);
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchQueryDetails();
  }, [ruleId, page, rowsPerPage]);

  if (loading) return (
    <Box display="flex" justifyContent="center" alignItems="center" minHeight="100vh">
      <CircularProgress size={60} thickness={4} style={{ color: '#db0011' }} />
    </Box>
  );

  return (
    <Container
      maxWidth={false}
      disableGutters
      sx={{
        backgroundColor: '#f8f8f8',
        minHeight: '100vh',
        minWidth: '100vw',
        padding: 0,
        margin: 0,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'stretch',
        justifyContent: 'flex-start',
      }}
    >
      <HeaderBanner>
        <Typography variant="h5" sx={{ fontWeight: 'bold' }}>
          Recommendations
        </Typography>
        <Box sx={{ flexGrow: 1 }} />
        {projectName && (
          <span style={{ marginRight: 24, fontSize: '1.1rem', color: '#fff' }}>
            <span style={{ fontWeight: 700, letterSpacing: 0.5 }}>Project:</span> {projectName}
          </span>
        )}
        <Box component="img"
          src={hsbcLogo}
          alt="HSBC Logo"
          sx={{ height: 48, width: 'auto' }}
        />
      </HeaderBanner>

      <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, gap: 0, width: '100%' }}>
        <QueryDetailsSection sx={{ flex: 1 }}>
          <Typography variant="h5" sx={{ fontWeight: 700, color: '#222', mb: 1, letterSpacing: 0.5 }}>
            Rule Details
          </Typography>
          <Typography variant="body1" sx={{ color: '#444', mt: 1, fontWeight: 400 }}>
            {decodeURIComponent(ruleTitle)}
          </Typography>
        </QueryDetailsSection>
        <QueryDetailsSection sx={{ flex: 1 }}>
          <Typography variant="h5" sx={{ fontWeight: 700, color: '#222', mb: 1, letterSpacing: 0.5 }}>
            Recommendation
          </Typography>
          <Typography variant="body1" sx={{ color: '#444', mt: 1, fontWeight: 400 }}>
            {decodeURIComponent(recommendation)}
          </Typography>
        </QueryDetailsSection>
      </Box>

      <StyledTableContainer sx={{ mx: 4, width: 'auto', maxWidth: '100%', boxShadow: '0 4px 16px rgba(0,0,0,0.07)' }}>
        <Paper sx={{ width: '100%', boxSizing: 'border-box', background: '#fff', borderRadius: 3 }}>
          <Table>
            <TableHead>
              <TableRow>
                {ruleIdNum === 10 ? (
                  <>
                    <StyledTableCell component="th">Project Name</StyledTableCell>
                    <StyledTableCell component="th">Schema Name</StyledTableCell>
                    <StyledTableCell component="th">Table Name</StyledTableCell>
                    <StyledTableCell component="th">Columns with Data Type</StyledTableCell>
                  </>
                ) : (
                  <>
                    <StyledTableCell component="th" align="center">Sr. No</StyledTableCell>
                    <StyledTableCell component="th">Project ID</StyledTableCell>             
                    <StyledTableCell component="th">Log ID</StyledTableCell>
                    <StyledTableCell component="th" align="center">Statement ID</StyledTableCell>
                    <StyledTableCell component="th" isquery={1}>Query</StyledTableCell>
                  </>
                )}
              </TableRow>
            </TableHead>
            <TableBody>
              {ruleIdNum === 10 ? (
                ruleData.map((row, idx) => (
                  <StyledTableRow key={idx}>
                    <StyledTableCell sx={{ minWidth: 180, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{row.project_name}</StyledTableCell>
                    <StyledTableCell sx={{ minWidth: 120, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{row.schema_name}</StyledTableCell>
                    <StyledTableCell sx={{ minWidth: 120, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{row.table_name}</StyledTableCell>
                    <StyledTableCell sx={{ minWidth: 200, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{row.columns_with_data_type}</StyledTableCell>
                  </StyledTableRow>
                ))
              ) : (
                ruleData.map((row, idx) => (
                  <StyledTableRow key={idx}>
                    <StyledTableCell align="center">{
                      rowsPerPage === -1
                        ? idx + 1
                        : page * rowsPerPage + idx + 1
                    }</StyledTableCell>
                    <StyledTableCell sx={{ minWidth: 180, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{row.project_name}</StyledTableCell>
                    <StyledTableCell sx={{ minWidth: 180, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{row.log_id}</StyledTableCell>
                    <StyledTableCell align="center" sx={{ minWidth: 80, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>1</StyledTableCell>
                    <StyledTableCell isquery={1} sx={{ minWidth: 200, whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxWidth: 450 }}>
                      <CodeBlock>
                        {row.query}
                      </CodeBlock>
                    </StyledTableCell>
                  </StyledTableRow>
                ))
              )}
            </TableBody>
          </Table>
        </Paper>
      </StyledTableContainer>

      <Box sx={{ display: 'flex', justifyContent: 'flex-end', p: 2, alignItems: 'center' }}>
        <TablePagination
          rowsPerPageOptions={[5, 10, 25, { label: 'All', value: -1 }]}
          component="div"
          count={totalCount}
          rowsPerPage={rowsPerPage}
          page={rowsPerPage === -1 ? 0 : page}
          onPageChange={(event, newPage) => setPage(newPage)}
          onRowsPerPageChange={event => {
            const value = parseInt(event.target.value, 10);
            setRowsPerPage(value);
            setPage(0);
          }}
          sx={{
            '.MuiTablePagination-select': {
              borderRadius: '4px',
              border: '1px solid #ddd',
            },
            '.MuiTablePagination-toolbar': {
              alignItems: 'center',
            }
          }}
          labelRowsPerPage="Rows per page:"
          labelDisplayedRows={({ from, to, count }) => null}
          ActionsComponent={props =>
            rowsPerPage === -1 ? null : undefined
          }
        />
        <button
          style={{
            marginLeft: 8,
            marginRight: 16,
            fontSize: '1.3rem',
            background: 'none',
            border: 'none',
            color: page === 0 || rowsPerPage === -1 ? '#bbb' : '#222',
            cursor: page === 0 || rowsPerPage === -1 ? 'not-allowed' : 'pointer',
            padding: 0,
            outline: 'none',
            boxShadow: 'none',
            borderRadius: '50%',
            width: 32,
            height: 32,
            transition: 'background 0.2s, transform 0.2s, color 0.2s',
          }}
          onMouseOver={e => {
            if (!(page === 0 || rowsPerPage === -1)) {
              e.currentTarget.style.background = '#f0f0f0';
              e.currentTarget.style.transform = 'scale(1.15)';
            }
          }}
          onMouseOut={e => {
            e.currentTarget.style.background = 'none';
            e.currentTarget.style.transform = 'none';
          }}
          disabled={page === 0 || rowsPerPage === -1}
          onClick={() => setPage(page - 1)}
        >
          {'<'}
        </button>
        <button
          style={{
            marginLeft: 2,
            fontSize: '1.3rem',
            background: 'none',
            border: 'none',
            color: (rowsPerPage === -1 || (page + 1) * rowsPerPage >= totalCount) ? '#bbb' : '#222',
            cursor: (rowsPerPage === -1 || (page + 1) * rowsPerPage >= totalCount) ? 'not-allowed' : 'pointer',
            padding: 0,
            outline: 'none',
            boxShadow: 'none',
            borderRadius: '50%',
            width: 32,
            height: 32,
            transition: 'background 0.2s, transform 0.2s, color 0.2s',
          }}
          onMouseOver={e => {
            if (!(rowsPerPage === -1 || (page + 1) * rowsPerPage >= totalCount)) {
              e.currentTarget.style.background = '#f0f0f0';
              e.currentTarget.style.transform = 'scale(1.15)';
            }
          }}
          onMouseOut={e => {
            e.currentTarget.style.background = 'none';
            e.currentTarget.style.transform = 'none';
          }}
          disabled={rowsPerPage === -1 || (page + 1) * rowsPerPage >= totalCount}
          onClick={() => setPage(page + 1)}
        >
          {'>'}
        </button>
        <span style={{ marginLeft: 16, fontWeight: 'bold', fontSize: '1rem' }}>
          {rowsPerPage === -1
            ? `All ${totalCount}`
            : `${Math.min(page * rowsPerPage + 1, totalCount)}-${Math.min((page + 1) * rowsPerPage, totalCount)} of ${totalCount}`
          }
        </span>
      </Box>
    </Container>
  );
};

export default QueryDetailsPage; 