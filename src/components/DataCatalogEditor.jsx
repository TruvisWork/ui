import React, {useState, useEffect} from 'react';
import './DataCatalogEditor.css';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import DeleteOutlineOutlinedIcon from '@mui/icons-material/DeleteOutlineOutlined';
import SaveIcon from '@mui/icons-material/Save';
import ColumnMetadata from './ColumnMetadata';
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
 
const DataCatalogEditor = () => { 
  // Existing states
  const [activeTab, setActiveTab] = useState('Table');
  const [newQueryValue, setNewQueryValue] = useState('');
  const [description, setDescription] = useState('');
  const [inputValue, setInputValue] = useState('');
  const [tags, setTags] = useState([]);
  const [filteredColumns, setFilteredColumns] = useState([]);
  const [aggregateColumns, setAggregateColumns] = useState([]);
  const [sortedColumns, setSortedColumns] = useState([]);
  const [keyColumns, setKeyColumns] = useState([]);
  const [filteredSelection, setFilteredSelection] = useState('');
  const [aggregateSelection, setAggregateSelection] = useState('');
  const [sortedSelection, setSortedSelection] = useState('');
  const [keySelection, setKeySelection] = useState('');
  
  const [selectedTable, setSelectedTable] = useState(null);
  const [tableOptionsState, setTableOptions] = useState([])
  
  // Initial data (from API)
  const [initialData, setInitialData] = useState(null);

  // Add loading and error states
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState('');

  const [sampleQueries, setSampleQueries] = useState([]);
  const [auditHistory, setAuditHistory] = useState([]);
  const [allColumns, setAllColumns] = useState([]);

  // Remove sessionStorage and credentials: 'include' usage in API requests
  const API_CONFIG = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // Fetch table options on component mount
  useEffect(() => {
    const fetchTableOptions = async () => {
      try {
        const response = await makeApiRequest('/get-tables', API_CONFIG);

        if (!response) return; // Authentication failed, already handled

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        // Parse the JSON response
        const data = await response.json();
        console.log('API Response:', data);
        setTableOptions(data.result || []);
      } catch (error) {
        console.error('Error fetching table options:', error);
      }
    };
    
    fetchTableOptions();
  }, []);

  // Fetch Meta data when table changes
  useEffect(() => {
    const fetchTableMetaData = async () => {
      if (!selectedTable) {
        // Reset form when no table is selected
        setInitialData(null);
        setDescription('');
        setTags([]);
        setFilteredColumns([]);
        setAggregateColumns([]);
        setSortedColumns([]);
        setKeyColumns([]);
        setSampleQueries([]);
        return;
      }
      
      try {
        const response = await makeApiRequest('/get-table-metadata', API_CONFIG);

        if (response.status === 404) {
          // Handle case where table metadata does not exist yet
          const blankData = {
            description: '', tags: [], filteredColumns: [], aggregateColumns: [],
            sortedColumns: [], keyColumns: [], sampleQueries: []
          };
          setInitialData(blankData);
        } else if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        } else {
          // Parse the JSON response for existing metadata
          const data = await response.json();
          const result = data.result;
          const loadedData = {
            description: result.description || '',
            tags: result.tags || [],
            filteredColumns: result.filter_columns || [],
            aggregateColumns: result.aggregate_columns || [],
            sortedColumns: result.sort_columns || [],
            keyColumns: result.key_columns || [],
            sampleQueries: (result.sample_usage || []).map((item, index) => ({
              id: index + 1, query: item.sql, tempQuery: item.sql,
              description: item.description || '', isEditing: false
            })),
          };
          setInitialData(loadedData);
        }
      } catch (error) {
        console.error('Error fetching table metadata:', error);
        setError(`Failed to load metadata for ${selectedTable}`);
        setInitialData(null);
      }
    };
    
    fetchTableMetaData();
  }, [selectedTable]);

  // Effect to update form state when initialData changes
  useEffect(() => {
    if (initialData) {
      setDescription(initialData.description);
      setTags(initialData.tags);
      setFilteredColumns(initialData.filteredColumns);
      setAggregateColumns(initialData.aggregateColumns);
      setSortedColumns(initialData.sortedColumns);
      setKeyColumns(initialData.keyColumns);
      setSampleQueries(initialData.sampleQueries);
    }
  }, [initialData]);

  // Fetch audit history when table changes
  useEffect(() => {
    const fetchAuditHistory = async () => {
      if (!selectedTable) {
        setAuditHistory([]);
        return;
      }
      try {
        const response = await makeApiRequest('/get-audit-table', API_CONFIG);
        if (!response || !response.ok) throw new Error(`HTTP error! status: ${response?.status}`);
        const data = await response.json();
        const formattedHistory = (data.result || []).map(item => ({
          updatedBy: item.user_id,
          updatedAt: new Date(item.event_time).toLocaleString(),
        }));
        setAuditHistory(formattedHistory);
      } catch (err) {
        console.error('Error fetching audit history:', err);
        // Do not show a critical error for audit history loading failure
      }
    };
    fetchAuditHistory();
  }, [selectedTable]);

  // Fetch columns when table changes
  useEffect(() => {
    const fetchAllColumns = async () => {
      if (!selectedTable) {
        setAllColumns([]);
        return;
      };
      
      try {
        const response = await makeApiRequest('/get-columns', API_CONFIG);

        if (!response) return; // Authentication failed, already handled

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        // Parse the JSON response
        const data = await response.json();
        setAllColumns(data.result || []);
      } catch (error) {
        console.error('Error fetching columns:', error);
      }
    };
    
    fetchAllColumns();
  }, [selectedTable]);

  // Reset button handler
  const handleReset = () => {
    if (!initialData) {
      alert('No initial data available to reset.');
      return;
    }
    setInitialData({...initialData}); // Trigger re-render from initial data
    setSuccessMessage('Form has been reset to its last saved state.');
    setTimeout(() => setSuccessMessage(''), 4000);
  };

  // Generic add/remove helpers
  const addTag = (selection, list, setList, setSelection) => {
    if (!selection || list.includes(selection)) {
        if (list.includes(selection)) alert(`'${selection}' is already in the list.`);
        setSelection('');
        return;
    };
    setList([...list, selection]);
    setSelection('');
  };

  const removeTag = (setList, index) => {
    setList(list => list.filter((_, i) => i !== index));
  };

  const handleEdit = (id) => {
    setSampleQueries((prevQueries) => prevQueries.map((q) => (q.id === id ? {
      ...q,
      isEditing: true,
      tempQuery: q.query
    } : q)));
  };

  const handleTempChange = (id, value) => {
    setSampleQueries((prevQueries) => prevQueries.map((q) => (q.id === id ? {
      ...q,
      tempQuery: value
    } : q)));
  };

  // Fixed main save handler for table metadata
  const handleSave = async () => {
    if (!selectedTable) {
      setError('Please select a table before saving.');
      return;
    }
    
    setIsLoading(true);
    setError(null);
    setSuccessMessage('');
    
    const payload = {
      market: 'ALL',
      table_name: selectedTable,
      obj: {
        description: description,
        filter_columns: filteredColumns,
        aggregate_columns: aggregateColumns,
        sort_columns: sortedColumns,
        key_columns: keyColumns,
        sample_usage: sampleQueries.map(q => ({ 
          sql: q.query, 
          description: q.description || '' 
        })),
        tags: tags
      }
    };
  
    try {
      const response = await makeApiRequest('/update-table', API_CONFIG);
  
      if (!response || !response.ok) {
        const errData = response ? await response.json() : { detail: "Network error" };
        throw new Error(errData.detail || 'Failed to save data.');
      }
  
      const data = await response.json();
      setSuccessMessage(data.message || 'Data saved successfully!');
      
      // Update initial data with current values to make 'reset' work correctly post-save
      const savedData = {
        description, tags, filteredColumns, aggregateColumns,
        sortedColumns, keyColumns,
        sampleQueries: sampleQueries.map(q => ({...q, isEditing: false })),
      };
      setInitialData(savedData);
  
      // Refresh audit history after successful save
      const auditResponse = await makeApiRequest('/get-audit-table', API_CONFIG);
      
      if (auditResponse && auditResponse.ok) {
        const auditData = await auditResponse.json();
        const formattedHistory = (auditData.result || []).map(item => ({
          updatedBy: item.user_id,
          updatedAt: new Date(item.event_time).toLocaleString(),
        }));
        setAuditHistory(formattedHistory);
      }
      
    } catch (err) {
      setError(`${err.message}`);
    } finally {
      setIsLoading(false);
      setTimeout(() => setSuccessMessage(''), 5000);
    }
  };

  // Fixed individual query save handler
  const handleSaveQuery = (id) => {
    setSampleQueries((prevQueries) => 
      prevQueries.map((q) => 
        q.id === id 
          ? { ...q, query: q.tempQuery, isEditing: false }
          : q
      )
    );
  };

  const handleAddNewQuery = () => {
    if (!newQueryValue.trim()) return;
    const newId = sampleQueries.length ? Math.max(...sampleQueries.map(q => q.id)) + 1 : 1;
    setSampleQueries([
      ...sampleQueries,
      { id: newId, query: newQueryValue.trim(), isEditing: false, tempQuery: newQueryValue.trim(), description: '' }
    ]);
    setNewQueryValue('');
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
        e.preventDefault();
        handleAddTag();
    }
  };

  const handleAddTag = () => {
    if (inputValue.trim() && !tags.includes(inputValue.trim())) {
      setTags([...tags, inputValue.trim()]);
      setInputValue('');
    }
  };

  const handleRemoveTag = (index) => {
    setTags(tags.filter((_, i) => i !== index));
  };

  const handleDeleteQuery = (id) => {
    setSampleQueries(sampleQueries.filter((query) => query.id !== id));
  };

  return (
    <div className="tabs-container">
      <div>
        <h2 className="header">Data Catalog Entry - Table Metadata</h2>
      </div>

      <div className="top-notifications">
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
        {successMessage && <Alert severity="success" onClose={() => setSuccessMessage('')}>{successMessage}</Alert>}
      </div>
      
      {/* Tabs */}
      <div className="tabs">
        <button className={`tab ${activeTab === 'Table' ? 'active' : ''}`}
          onClick={() => setActiveTab('Table')}>
          Table Metadata
        </button>
        <button className={`tab ${activeTab === 'Column' ? 'active' : ''}`}
          onClick={() => setActiveTab('Column')}>
          Column Metadata
        </button>
      </div>

      <div className="tab-content">
        {activeTab === 'Table' ? (
          <> {/* Table Metadata Content */}
            <div className="top-buttons">
              <button 
                className="top-save-btn" 
                onClick={handleSave}
                disabled={isLoading || !selectedTable}
              >
                {isLoading ? <CircularProgress size={20} color="inherit" /> : 'Save'}
              </button>
              
              <button className="top-reset-btn" onClick={handleReset} disabled={isLoading || !initialData}>
                Reset
              </button>
            </div>
            <div className="curved-section">
              <div className="dropdown-section">
                <div style={{ display: 'flex', gap: '16px', marginBottom: '20px' }}>
                  <div style={{ flex: 1 }}>
                    <h3>Select Table</h3>
                    <Autocomplete
                      options={tableOptionsState}
                      getOptionLabel={(option) => option || ''}
                      value={selectedTable}
                      onChange={(_, v) => setSelectedTable(v)}
                      renderInput={params => (
                        <MuiTextField
                          {...params}
                          placeholder="Select Table"
                          className="status-dropdown"
                        />
                      )}
                    />
                  </div>
                </div>
                
                <h3>Enter Description</h3>
                <textarea 
                  className="description-textarea"
                  placeholder="Type your description here..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  disabled={!selectedTable}
                />
              </div>
            </div>

            {/* Tags Section */}
            <div className="curved-section">
              <div className="tags-section">
                <table className="status-table-Tag">
                  <thead>
                    <tr className="table-header">
                      <th>Tags</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr><td>
                    <div className="InputSection">
                      <div className="tags-list">
                        {tags.map((tag, index) => (
                          <span key={index} className="tag-item">
                            {tag}
                            <button onClick={() => handleRemoveTag(index)} className="remove-tag">
                              ×
                            </button>
                          </span>
                        ))} 
                      </div>
                      <div className="tags-input-group">
                        <input type="text"
                          onChange={(e) => setInputValue(e.target.value)}
                          value={inputValue}
                          onKeyPress={handleKeyPress}
                          placeholder="Add Tag"
                          className="tag-input"
                          disabled={!selectedTable}/>
                        <button onClick={handleAddTag} className="add-tag-btn" disabled={!selectedTable}>
                          Add
                        </button>
                      </div>
                    </div>
                    </td></tr>
                  </tbody>
                </table>
                {/* Commonly Used Sections */}
                <div className="dual-sections-container">
                  {/* Filtered Columns */}
                  <table className="Commonly-Table">
                    <thead><tr className="table-header"><th>Commonly used filtered columns</th></tr></thead>
                    <tbody><tr><td>
                      <div className="tags-list">
                        {filteredColumns.map((tag, index) => (
                          <span key={index} className="tag">{tag}
                            <button onClick={() => removeTag(setFilteredColumns, index)} className="remove-btn">×</button>
                          </span>
                        ))} 
                      </div>
                      <div className="input-group">
                        <select className="select-dropdown" value={filteredSelection} onChange={(e) => setFilteredSelection(e.target.value)} disabled={!selectedTable}>
                          <option value="">Select column…</option>
                          {allColumns.map((col) => (<option key={col} value={col}>{col}</option>))} 
                        </select>
                        <button onClick={() => addTag(filteredSelection, filteredColumns, setFilteredColumns, setFilteredSelection)} className="add-btn" disabled={!selectedTable}>Add Column</button>
                      </div>
                    </td></tr></tbody>
                  </table>
                  {/* Aggregate Columns */}
                  <table className="Commonly-Table">
                    <thead><tr className="table-header"><th>Commonly used Aggregate Columns</th></tr></thead>
                    <tbody><tr><td>
                      <div className="tags-list">
                        {aggregateColumns.map((tag, index) => (
                          <span key={index} className="tag">{tag}
                            <button onClick={() => removeTag(setAggregateColumns, index)} className="remove-btn">×</button>
                          </span>
                        ))} 
                      </div>
                      <div className="input-group">
                        <select className="select-dropdown" value={aggregateSelection} onChange={(e) => setAggregateSelection(e.target.value)} disabled={!selectedTable}>
                          <option value="">Select column…</option>
                          {allColumns.map((col) => (<option key={col} value={col}>{col}</option>))} 
                        </select>
                        <button onClick={() => addTag(aggregateSelection, aggregateColumns, setAggregateColumns, setAggregateSelection)} className="add-btn" disabled={!selectedTable}>Add Column</button>
                      </div>
                    </td></tr></tbody>
                  </table>
                </div>
                
                {/* Sorted & Key Columns */}
                <div className="dual-sections-container">
                  {/* Sorted Columns */}
                  <table className="Commonly-Table">
                    <thead><tr className="table-header"><th>Commonly used sorted columns</th></tr></thead>
                    <tbody><tr><td>
                      <div className="tags-list">
                        {sortedColumns.map((tag, index) => (
                          <span key={index} className="tag">{tag}
                            <button onClick={() => removeTag(setSortedColumns, index)} className="remove-btn">×</button>
                          </span>
                        ))} 
                      </div>
                      <div className="input-group">
                        <select className="select-dropdown" value={sortedSelection} onChange={(e) => setSortedSelection(e.target.value)} disabled={!selectedTable}>
                          <option value="">Select column…</option>
                          {allColumns.map((col) => (<option key={col} value={col}>{col}</option>))}
                        </select>
                        <button onClick={() => addTag(sortedSelection, sortedColumns, setSortedColumns, setSortedSelection)} className="add-btn" disabled={!selectedTable}>Add Column</button>
                      </div>
                    </td></tr></tbody>
                  </table>
                  {/* Key Columns */}
                  <table className="Commonly-Table">
                    <thead><tr className="table-header"><th>Key Columns</th></tr></thead>
                    <tbody><tr><td>
                      <div className="tags-list">
                        {keyColumns.map((tag, index) => (
                          <span key={index} className="tag">{tag}
                            <button onClick={() => removeTag(setKeyColumns, index)} className="remove-btn">×</button>
                          </span>
                        ))} 
                      </div>
                      <div className="input-group">
                        <select className="select-dropdown" value={keySelection} onChange={(e) => setKeySelection(e.target.value)} disabled={!selectedTable}>
                          <option value="">Select column…</option>
                          {allColumns.map((col) => (<option key={col} value={col}>{col}</option>))} 
                        </select>
                        <button onClick={() => addTag(keySelection, keyColumns, setKeyColumns, setKeySelection)} className="add-btn" disabled={!selectedTable}>Add Column</button>
                      </div>
                    </td></tr></tbody>
                  </table>
                </div>
              </div>
            </div>

            {/* Sample Usage Section */}
            <div className="curved-section">
              <div className="sample-usage-container">
                <h2 className="sample-usage-heading">Sample Usage</h2>
                <table className="sample-usage-table">
                  <thead><tr><th>Sample Usage Query</th><th>Action</th></tr></thead>
                  <tbody> 
                    {sampleQueries.map((query) => (
                      <tr key={query.id}>
                        <td>{query.isEditing ? (<textarea value={query.tempQuery} onChange={(e) => handleTempChange(query.id, e.target.value)} className="editable-query"/>) : (query.query)}</td>
                        <td>{query.isEditing ? (<button onClick={() => handleSaveQuery(query.id)} className="save-btn-sample"><SaveIcon/></button>) : (<><button onClick={() => handleEdit(query.id)} className="edit-btn-sample"><EditOutlinedIcon/></button><button onClick={() => handleDeleteQuery(query.id)} className="delete-btn-sample"><DeleteOutlineOutlinedIcon/></button></>)}</td>
                      </tr>
                    ))} 
                    <tr><td colSpan="2">
                        <div className="add-query-section">
                        <input type="text" className="new-query-input" placeholder="Enter new query..." value={newQueryValue} onChange={e => setNewQueryValue(e.target.value)} disabled={!selectedTable}/>
                        <button className="save-btn-sample-input" onClick={handleAddNewQuery} disabled={!selectedTable}>Save</button>
                        </div>
                    </td></tr>
                  </tbody>
                </table>
                {/* Audit History Section */}
                <div className="audit-history-section">
                  <h2>Audit History</h2>
                  <table className="audit-history-table">
                    <thead><tr><th>Updated By</th><th>Updated Date and Time</th></tr></thead>
                    <tbody> 
                      {auditHistory.length > 0 ? (
                        auditHistory.map((entry, index) => (
                          <tr key={index}><td>{entry.updatedBy}</td><td>{entry.updatedAt}</td></tr>
                        ))
                      ) : (
                        <tr><td colSpan="2" style={{ textAlign: 'center' }}>No audit history available for this table.</td></tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </>
        ) : (
          /* Column Metadata Content */ 
          <div className="column-metadata">
            <ColumnMetadata/>
          </div>
        )} 
      </div>
    </div>
  );
};

export default DataCatalogEditor;