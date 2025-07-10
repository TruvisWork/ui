import React, { useState, useEffect } from 'react';
import './ColumnMetadata.css';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import DeleteOutlineOutlinedIcon from '@mui/icons-material/DeleteOutlineOutlined';
import SaveIcon from '@mui/icons-material/Save';
import { Autocomplete, TextField as MuiTextField, CircularProgress, Alert } from '@mui/material';

// API configuration constants
const API_BASE_URL = 'http://10.91.12.22:8000';

// Internal API request handler
const makeApiRequest = async (endpoint, options = {}) => {
  const config = {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  };

  try {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
    
    // Handle authentication errors
    if (response.status === 401) {
      console.error('Authentication failed - redirecting to login');
      sessionStorage.clear();
      window.location.href = '/login';
      return null;
    }
    
    return response;
  } catch (error) {
    console.error(`API request failed for ${endpoint}:`, error);
    throw error;
  }
};

const ColumnMetadata = () => {
  // UI State
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState('');

  // Form Data State
  const [description, setDescription] = useState('');
  const [inputValue, setInputValue] = useState('');
  const [inputValue2, setInputValue2] = useState('');
  const [sampleValues, setSampleValues] = useState([]);
  const [relatedTerms, setRelatedTerms] = useState([]);
  const [newQueryValue, setNewQueryValue] = useState('');
  const [isFilterable, setIsFilterable] = useState(false);
  const [isAggregatable, setIsAggregatable] = useState(false);
  
  // Dropdown & Selection State
  const [selectedTable, setSelectedTable] = useState(null);
  const [selectedColumn, setSelectedColumn] = useState(null);
  const [tableOptions, setTableOptions] = useState([]);
  const [columnOptions, setColumnOptions] = useState([]);
  
  // Complex Data State
  const [queries, setQueries] = useState([]);
  const [auditHistory, setAuditHistory] = useState([]);
  
  // State for Reset functionality
  const [initialData, setInitialData] = useState(null);

  // Remove sessionStorage and credentials: 'include' usage in API requests
  const API_CONFIG = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // Fetch column-specific audit history when column changes
  useEffect(() => {
    const fetchAuditHistory = async () => {
      if (!selectedTable || !selectedColumn) {
        setAuditHistory([]);
        return;
      }
      
      try {
        // Fetch column-specific audit history
        const response = await makeApiRequest('/get-audit-table', {
          method: 'POST',
          body: JSON.stringify({ 
            table_name: selectedTable,
            column_name: selectedColumn 
          }),
        });
        
        if (!response || !response.ok) {
          if (response?.status === 404) {
            // No audit history found - this is ok
            setAuditHistory([]);
            return;
          }
          throw new Error(`HTTP error! status: ${response?.status}`);
        }
        
        const data = await response.json();
        const formattedHistory = (data.result || []).map(item => ({
          updatedBy: item.user_id,
          updatedAt: new Date(item.event_time).toLocaleString(),
        }));
        setAuditHistory(formattedHistory);
      } catch (err) {
        console.error('Error fetching audit history:', err);
        // Don't show error for audit history - it's not critical
        setAuditHistory([]);
      }
    };
    fetchAuditHistory();
  }, [selectedTable, selectedColumn]);

  // Fetch table options
  useEffect(() => {
    const fetchTableOptions = async () => {
      try {
        const response = await makeApiRequest('/get-tables', API_CONFIG);
        if (!response || !response.ok) throw new Error(`HTTP error! status: ${response.status}`);
        const data = await response.json();
        setTableOptions(data.result || []);
      } catch (err) {
        console.error('Error fetching table options:', err);
        setError('Could not load tables.');
      }
    };
    fetchTableOptions();
  }, []);

  // Fetch column options when table changes
  useEffect(() => {
    const fetchColumnOptions = async () => {
      if (!selectedTable) {
        setColumnOptions([]);
        setSelectedColumn(null);
        return;
      }
      try {
        const response = await makeApiRequest('/get-columns', {
          method: 'POST', body: JSON.stringify({ table_name: selectedTable })
        });
        if (!response || !response.ok) throw new Error(`HTTP error! status: ${response.status}`);
        const data = await response.json();
        setColumnOptions(data.result || []);
      } catch (err) {
        console.error('Error fetching column options:', err);
        setError('Could not load columns for the selected table.');
      }
    };
    fetchColumnOptions();
  }, [selectedTable]);

  // Fetch metadata when column changes
  useEffect(() => {
    const fetchColumnMetadata = async () => {
      if (!selectedTable || !selectedColumn) {
        setInitialData(null); // Clear form if no column is selected
        return;
      }
      
      setIsLoading(true);
      setError(null);
      try {
        const data = await makeApiRequest('/get-column-metadata', {
          method: 'POST',
          body: JSON.stringify({
            market: 'ALL',
          })
        });
        if (!data) return; // Auth error handled in makeApiRequest
        if (!data.ok) {
            // If metadata not found, reset form to a clean state
            if (data.status === 404) {
                const blankData = {
                    description: '', is_filterable: false, is_aggregatable: false,
                    sample_values: [], related_business_terms: [], sample_usage: [],
                };
                setInitialData(blankData);
            } else {
                throw new Error(`HTTP error! status: ${data.status}`);
            }
        } else {
            const data = await data.json();
            setInitialData(data.result);
        }
      } catch (err) {
        console.error('Error fetching column metadata:', err);
        setError(`Failed to load metadata for ${selectedColumn}.`);
        setInitialData(null);
      } finally {
        setIsLoading(false);
      }
    };
    fetchColumnMetadata();
  }, [selectedTable, selectedColumn]);

  // Effect to update form fields when initialData changes
  useEffect(() => {
    if (initialData) {
      setDescription(initialData.description || '');
      setIsFilterable(initialData.is_filterable || false);
      setIsAggregatable(initialData.is_aggregatable || false);
      setSampleValues(initialData.sample_values || []);
      setRelatedTerms(initialData.related_business_terms || []);
      setQueries((initialData.sample_usage || []).map((item, index) => ({
        id: index + 1,
        query: item.sql,
        description: item.description || '',
        isEditing: false,
        tempQuery: item.sql,
      })));
    } else {
      // Reset form if initialData is null
      setDescription('');
      setIsFilterable(false);
      setIsAggregatable(false);
      setSampleValues([]);
      setRelatedTerms([]);
      setQueries([]);
    }
  }, [initialData]);

  const handleFilterableChange = (event) => setIsFilterable(event.target.checked);
  const handleAggregatableChange = (event) => setIsAggregatable(event.target.checked);
  
  const handleAddTag = () => {
    if (inputValue.trim()) {
      setSampleValues([...sampleValues, inputValue.trim()]);
      setInputValue('');
    }
  };
  const handleRemoveTag = (idx) => setSampleValues(sampleValues.filter((_, i) => i !== idx));
  const handleRealtedAddTag = () => {
    if (inputValue2.trim()) {
      setRelatedTerms([...relatedTerms, inputValue2.trim()]);
      setInputValue2('');
    }
  };
  const handleRealtedRemoveTag = (idx) => setRelatedTerms(relatedTerms.filter((_, i) => i !== idx));

  const handleAddNewQuery = () => {
    if (newQueryValue.trim()) {
      const newId = queries.length ? Math.max(...queries.map(q => q.id)) + 1 : 1;
      setQueries([...queries, { id: newId, query: newQueryValue.trim(), isEditing: false, tempQuery: '', description: '' }]);
      setNewQueryValue('');
    }
  };
  const handleStartEditingQuery = (id) => setQueries(queries.map(q => q.id === id ? { ...q, isEditing: true, tempQuery: q.query } : q));
  const handleSaveEditedQuery = (id) => setQueries(queries.map(q => q.id === id ? { ...q, query: q.tempQuery, isEditing: false } : q));
  const handleRemoveQuery = (id) => setQueries(queries.filter(q => q.id !== id));
  const handleQueryTextChange = (id, val) => setQueries(queries.map(q => q.id === id ? { ...q, tempQuery: val } : q));

  const handleReset = () => {
    if (!initialData) return;
    setInitialData({ ...initialData }); 
    setSuccessMessage('Form has been reset to its last saved state.');
    setTimeout(() => setSuccessMessage(''), 4000);
  };

  const handleSave = async () => {
    if (!selectedTable || !selectedColumn) {
      setError('Please select a table and a column before saving.');
      return;
    }
    setIsLoading(true);
    setError(null);
    setSuccessMessage('');
    
    // Correct payload structure. The id_key is generated on the backend.
    const payload = {
      market: 'ALL',
      table_name: selectedTable,
      column_name: selectedColumn,
      obj: {
        description,
        is_filterable: isFilterable,
        is_aggregatable: isAggregatable,
        sample_values: sampleValues,
        related_business_terms: relatedTerms,
        sample_usage: queries.map(q => ({ sql: q.query, description: q.description })),
      }
    };

    try {
      const response = await makeApiRequest('/update-columns', {
        method: 'POST',
        body: JSON.stringify(payload),
      });

      if (!response || !response.ok) {
        const errData = response ? await response.json() : { detail: "Network error" };
        throw new Error(errData.detail || 'Failed to save data.');
      }

      const data = await response.json();
      setSuccessMessage(data.message || 'Data saved successfully!');
      
      // Update initialData with saved values for reset functionality
      const savedData = {
        description,
        is_filterable: isFilterable,
        is_aggregatable: isAggregatable,
        sample_values: sampleValues,
        related_business_terms: relatedTerms,
        sample_usage: queries.map(q => ({ sql: q.query, description: q.description })),
      };
      setInitialData(savedData);
      
      // Manually trigger a refresh of the audit history
      if (selectedTable && selectedColumn) {
          const auditResponse = await makeApiRequest('/get-audit-table', {
              method: 'POST',
              body: JSON.stringify({ table_name: selectedTable, column_name: selectedColumn }),
          });
          if (auditResponse && auditResponse.ok) {
              const auditData = await auditResponse.json();
              const formattedHistory = (auditData.result || []).map(item => ({
                  updatedBy: item.user_id,
                  updatedAt: new Date(item.event_time).toLocaleString(),
              }));
              setAuditHistory(formattedHistory);
          }
      }
      
    } catch (err) {
      setError(`${err.message}`);
    } finally {
      setIsLoading(false);
      setTimeout(() => setSuccessMessage(''), 5000);
    }
  };

  return (
    <div className="curved-section">
      <div className="column-metadata-container">

        {/* Top Buttons and Notifications */}
        <div className="top-controls">
            <div className="top-buttons">
                <button className="top-save-btn" onClick={handleSave} disabled={isLoading || !selectedColumn}>
                    {isLoading ? <CircularProgress size={20} color="inherit" /> : 'Save'}
                </button>
                <button className="top-reset-btn" onClick={handleReset} disabled={isLoading || !initialData}>
                    Reset
                </button>
            </div>
            {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
            {successMessage && <Alert severity="success" onClose={() => setSuccessMessage('')}>{successMessage}</Alert>}
        </div>

        {/* Select Table & Column */}
        <div className="dropdown-section">
          <div style={{ display: 'flex', gap: '16px', marginBottom: '20px' }}>
            <div style={{ flex: 1 }}>
              <h3>Select Table</h3>
              <Autocomplete
                options={tableOptions}
                getOptionLabel={(option) => option || ''}
                value={selectedTable}
                onChange={(_, v) => setSelectedTable(v)}
                renderInput={params => <MuiTextField {...params} placeholder="Select Table" className="status-dropdown" />}
              />
            </div>
            <div style={{ flex: 1 }}>
              <h3>Select Column</h3>
              <Autocomplete
                options={columnOptions}
                getOptionLabel={(option) => option || ''}
                value={selectedColumn}
                onChange={(_, v) => setSelectedColumn(v)}
                renderInput={params => <MuiTextField {...params} placeholder="Select Column" className="status-dropdown" />}
                disabled={!selectedTable}
              />
            </div>
          </div>
        </div>
        
        {isLoading && <div style={{ textAlign: 'center', margin: '20px' }}><CircularProgress /></div>}

        {/* The rest of the form is only shown when a column is selected and not loading */}
        {!isLoading && selectedColumn && initialData && (
            <>
                {/* Description */}
                <div className="description-section">
                <h2 className="description-heading">Enter Description</h2>
                <textarea
                    className="description-textarea"
                    placeholder="Type your description here..."
                    value={description}
                    onChange={e => setDescription(e.target.value)}
                />
                </div>
                
                {/* Checkboxes */}
                <div className="checkbox-section">
                <h2 className="checkbox-description-heading">Column Behavior</h2>
                <div className="checkbox-container">
                    <label><input type="checkbox" checked={isFilterable} onChange={handleFilterableChange} /> Is Filterable</label>
                    <label><input type="checkbox" checked={isAggregatable} onChange={handleAggregatableChange} /> Is Aggregatable</label>
                </div>
                </div>

                {/* Domain Specific Context */}
                <div className="domain-specific-context-section">
                    <h2 className="domain-specific-context">Domain Specific Context</h2>
                    <table className="status-table-Tag">
                        <thead><tr className="table-header"><th>Sample Values</th></tr></thead>
                        <tbody><tr><td>
                        <div className="InputSection">
                            <div className="tags-list">
                            {sampleValues.map((tag, idx) => (
                                <span key={idx} className="tag-item">{tag}<button className="remove-tag" onClick={() => handleRemoveTag(idx)}>×</button></span>
                            ))}
                            </div>
                            <div className="tags-input-group">
                            <input type="text" className="tag-input" placeholder="Enter Sample Values" value={inputValue} onChange={e => setInputValue(e.target.value)} onKeyPress={e => e.key === 'Enter' && (e.preventDefault(), handleAddTag())} />
                            <button className="add-tag-btn" onClick={handleAddTag}>Add</button>
                            </div>
                        </div>
                        </td></tr></tbody>
                    </table>
                    <table className="status-table-Tag">
                        <thead><tr className="table-header"><th>Related Business Terms</th></tr></thead>
                        <tbody><tr><td>
                        <div className="InputSection">
                            <div className="tags-list">
                            {relatedTerms.map((tag, idx) => (
                                <span key={idx} className="tag-item">{tag}<button className="remove-tag" onClick={() => handleRealtedRemoveTag(idx)}>×</button></span>
                            ))}
                            </div>
                            <div className="tags-input-group">
                            <input type="text" className="tag-input" placeholder="Enter Related Business Terms" value={inputValue2} onChange={e => setInputValue2(e.target.value)} onKeyPress={e => e.key === 'Enter' && (e.preventDefault(), handleRealtedAddTag())} />
                            <button className="add-tag-btn" onClick={handleRealtedAddTag}>Add</button>
                            </div>
                        </div>
                        </td></tr></tbody>
                    </table>
                </div>

                {/* Sample Query */}
                <div className="sample-usage-container">
                <h2 className="sample-usage-heading">Sample Query</h2>
                <table className="sample-usage-table">
                    <thead><tr><th>Sample Usage Query</th><th>Action</th></tr></thead>
                    <tbody>
                    {queries.map(q => (
                        <tr key={q.id}>
                        <td>
                            {q.isEditing ? (<textarea className="editable-query" value={q.tempQuery} onChange={e => handleQueryTextChange(q.id, e.target.value)}/>) : (q.query)}
                        </td>
                        <td>
                            {q.isEditing ? (<button className="save-btn-sample" onClick={() => handleSaveEditedQuery(q.id)}><SaveIcon fontSize="small" /></button>) : (
                            <>
                                <button className="edit-btn-sample" onClick={() => handleStartEditingQuery(q.id)}><EditOutlinedIcon fontSize="small" /></button>
                                <button className="delete-btn-sample" onClick={() => handleRemoveQuery(q.id)}><DeleteOutlineOutlinedIcon fontSize="small" /></button>
                            </>
                            )}
                        </td>
                        </tr>
                    ))}
                    <tr><td colSpan={2}>
                        <div className="add-query-section">
                        <input type="text" className="new-query-input" placeholder="Enter new query..." value={newQueryValue} onChange={e => setNewQueryValue(e.target.value)} />
                        <button className="save-btn-sample-input" onClick={handleAddNewQuery}>Save</button>
                        </div>
                    </td></tr>
                    </tbody>
                </table>
                </div>

                {/* Audit History */}
                <div className="audit-history-section">
                <h2>Audit History</h2>
                <table className="audit-history-table">
                    <thead><tr><th>Updated By</th><th>Updated Date and Time</th></tr></thead>
                    <tbody>
                    {auditHistory.length > 0 ? (
                        auditHistory.map((e, i) => (
                            <tr key={i}>
                                <td>{e.updatedBy}</td>
                                <td>{e.updatedAt}</td>
                            </tr>
                        ))
                    ) : (
                        <tr><td colSpan="2">No audit history available for this column.</td></tr>
                    )}
                    </tbody>
                </table>
                </div>
            </>
        )}
      </div>
    </div>
  );
};

export default ColumnMetadata;