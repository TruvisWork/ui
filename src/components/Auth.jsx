import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import './Auth.css';
import axios from 'axios';

const Auth = () => {
  const [step, setStep] = useState('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [allowedMarkets, setAllowedMarkets] = useState([]);
  const [selectedMarket, setSelectedMarket] = useState('');
  const [resetIdentifier, setResetIdentifier] = useState('');
  const [userName, setUserName] = useState('');
  const [userEmail, setUserEmail] = useState('');
  const [userDisplayName, setUserDisplayName] = useState('');
  const [isChangingMarket, setIsChangingMarket] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [verificationPassword, setVerificationPassword] = useState('');
  const navigate = useNavigate();
  const location = useLocation();

  // API configuration constants
  const API_BASE_URL = 'http://localhost:8000';
  const API_CONFIG = {
    credentials: 'include', // Always include cookies
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // Internal API request handler
  const makeApiRequest = async (endpoint, options = {}) => {
    const config = {
      credentials: 'include',
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    };

    try {
      const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
      return response;
    } catch (error) {
      console.error(`API request failed for ${endpoint}:`, error);
      throw error;
    }
  };

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const response = await makeApiRequest('/me');

      if (response.status === 401) {
        sessionStorage.clear();
        navigate('/login');
        return;
      }

      if (response.ok) {
        const userData = await response.json();
        if (userData.authenticated) {
          
          sessionStorage.setItem('username', userData.username);
          // User is authenticated, check if they want to change market
          const changeMarketRequest = sessionStorage.getItem('changeMarketRequest');
          
          if (changeMarketRequest) {
            sessionStorage.removeItem('changeMarketRequest');
            setIsChangingMarket(true);
            setUserName(userData.username);
            setAllowedMarkets(userData.markets || []);
            setStep('password-verification');
          } else {
            // User is authenticated and has market selected, redirect to main app
            const currentMarket = sessionStorage.getItem('selectedMarket');
            if (currentMarket && userData.markets?.includes(currentMarket)) {
              navigate('/');
            } else {
              // User needs to select market
              setAllowedMarkets(userData.markets || []);
              setUserName(userData.username);
              setStep('market-select');
            }
          }
        }
      } else {
        if (response.status === 401) {
          sessionStorage.clear();
          navigate('/login');
        }
      }
    } catch (error) {
      console.error('Error checking auth status', error);
    }
  };

  const switchForm = (formType) => {
    setStep(formType);
    hideMessages();
  };

  const showMessage = (message, type) => {
    hideMessages();
    if (type === 'success') {
      setSuccessMessage(message);
    } else {
      setErrorMessage(message);
    }
    setTimeout(hideMessages, 5000);
  };

  const hideMessages = () => {
    setSuccessMessage('');
    setErrorMessage('');
  };

  const togglePasswordVisibility = () => {
    setShowPassword(!showPassword);
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    hideMessages();
    setIsLoading(true);

    try {
      sessionStorage.setItem('selectedMarket', '');
      // Create Basic Auth header
      const credentials = btoa(`${username}:${password}`);
      
      const response = await makeApiRequest('/login', {
        method: 'POST',
        headers: { 
          'Authorization': `Basic ${credentials}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(), // FIX: Add an empty body to the request
      });  //added the username, password to the body or else it sends empty body

      if (!response.ok) {
        // FIX: Gracefully handle non-JSON error responses
        const error = await response.json().catch(() => ({ detail: 'Invalid username or password.' }));
        throw new Error(error.detail || 'Login failed');
      }

      const result = await response.json();

      // Store user info and markets for UI purposes
      if (result.user) {
        setUserName(result.user.username);
        setUserEmail(result.user.email);
        setUserDisplayName(result.user.displayName);
        sessionStorage.setItem('username', result.user.username);
      }

      if (result.markets && result.markets.length > 0) {
        setAllowedMarkets(result.markets);
        sessionStorage.setItem('allowedMarkets', JSON.stringify(result.markets));
        setStep('market-select');
      } else {
        throw new Error('No access to any markets. Contact admin.');
      }
    } catch (err) {
      // FIX: Provide a more helpful network error message
      const displayMessage = err.message.includes('Failed to fetch')
        ? 'Network Error: Could not connect to server.'
        : err.message;
      showMessage(displayMessage, 'error');
    } finally {
      setIsLoading(false);
    }
  };

  const handlePasswordVerification = async (e) => {
    e.preventDefault();
    hideMessages();
    setIsLoading(true);

    try {
      // Create Basic Auth header for verification
      const credentials = btoa(`${userName}:${verificationPassword}`);
      
      const response = await makeApiRequest('/login', {
        method: 'POST',
        headers: { 
          'Authorization': `Basic ${credentials}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({}), // FIX: Add an empty body to the request
      });

      if (!response.ok) {
        // FIX: Gracefully handle non-JSON error responses
        const error = await response.json().catch(() => ({ detail: 'Incorrect password.' }));
        throw new Error(error.detail || 'Password verification failed');
      }

      const result = await response.json();
      
      // Store user info and markets for UI purposes
      if (result.user) {
        sessionStorage.setItem('username', result.user.username);
      }
      
      // Update allowed markets and proceed to market selection
      if (result.markets) {
        setAllowedMarkets(result.markets);
        sessionStorage.setItem('allowedMarkets', JSON.stringify(result.markets));
      }
      
      setStep('market-select');
      setVerificationPassword('');
    } catch (err) {
      const displayMessage = err.message.includes('Failed to fetch')
        ? 'Network Error: Could not connect to server.'
        : err.message;
      showMessage(displayMessage, 'error');
    } finally {
      setIsLoading(false);
    }
  };

  const handleForgotPassword = async (e) => {
    e.preventDefault();
    hideMessages();
    setIsLoading(true);

    try {
      const response = await makeApiRequest('/forgot-password', {
        method: 'POST',
        body: JSON.stringify({ identifier: resetIdentifier }),
      });

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error.detail || 'Failed to send reset link');
      }

      const result = await response.json();
      showMessage(result.message || 'Password reset link sent successfully!', 'success');
      
      setTimeout(() => {
        setStep('login');
        setResetIdentifier('');
      }, 3000);
    } catch (err) {
      showMessage(err.message || 'Network error. Please try again.', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  const handleMarketSelect = () => {
    if (!selectedMarket) {
      showMessage('Please select a market.', 'error');
      return;
    }

    sessionStorage.setItem('selectedMarket', selectedMarket);
    navigate('/');
  };

  const handleCancelMarketChange = () => {
    navigate('/');
  };

  async function login(credentials) {
    try {
      return await axios.post('/api/login', credentials);
    } catch (err) {
      if (err.message === "Network Error") {
        await new Promise(res => setTimeout(res, 500));
        return axios.post('/api/login', credentials);
      }
      throw err;
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-container">
        <div className="auth-header">
          <h1>
            {isChangingMarket ? `Welcome back, ${userName}` : 'Welcome'}
          </h1>
          <p>
            {step === 'login' && 'Sign in to your account'}
            {step === 'market-select' && (isChangingMarket ? 'Select your new market' : 'Select your market to continue')}
            {step === 'forgot-password' && 'Reset your password'}
            {step === 'password-verification' && 'Verify your password to change market'}
          </p>
        </div>

        <div className="form-container">
          {/* Login Form */}
          {step === 'login' && (
            <div className="form-section active">
              {successMessage && (
                <div className="success-message">{successMessage}</div>
              )}
              {errorMessage && (
                <div className="error-message">{errorMessage}</div>
              )}

              <form onSubmit={handleLogin}>
                <div className="form-group">
                  <label htmlFor="login-username">Username</label>
                  <input
                    type="text"
                    id="login-username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    required
                    disabled={isLoading}
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="login-password">Password</label>
                  <div className="password-input-container">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      id="login-password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                      disabled={isLoading}
                    />
                    <button
                      type="button"
                      className="password-toggle"
                      onClick={togglePasswordVisibility}
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                      disabled={isLoading}
                    >
                      <svg
                        width="20"
                        height="20"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      >
                        {showPassword ? (
                          <>
                            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                            <line x1="1" y1="1" x2="23" y2="23"/>
                          </>
                        ) : (
                          <>
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                          </>
                        )}
                      </svg>
                    </button>
                  </div>
                </div>

                <button type="submit" className="auth-btn" disabled={isLoading}>
                  {isLoading ? 'Signing In...' : 'Sign In'}
                </button>
              </form>

              <div className="forgot-password">
                <a onClick={() => switchForm('forgot-password')}>
                  Forgot your password?
                </a>
              </div>
            </div>
          )}

          {/* Password Verification Form for Market Change */}
          {step === 'password-verification' && (
            <div className="form-section active">
              {successMessage && (
                <div className="success-message">{successMessage}</div>
              )}
              {errorMessage && (
                <div className="error-message">{errorMessage}</div>
              )}

              <div className="verification-info">
                <div className="user-info">
                  <span className="user-icon">👤</span>
                  <span>Logged in as: <strong>{userName}</strong></span>
                </div>
                <p>Please enter your password to change your market selection.</p>
              </div>

              <form onSubmit={handlePasswordVerification}>
                <div className="form-group">
                  <label htmlFor="verification-password">Password</label>
                  <div className="password-input-container">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      id="verification-password"
                      value={verificationPassword}
                      onChange={(e) => setVerificationPassword(e.target.value)}
                      placeholder="Enter your password"
                      required
                      disabled={isLoading}
                    />
                    <button
                      type="button"
                      className="password-toggle"
                      onClick={togglePasswordVisibility}
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                      disabled={isLoading}
                    >
                      <svg
                        width="20"
                        height="20"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      >
                        {showPassword ? (
                          <>
                            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                            <line x1="1" y1="1" x2="23" y2="23"/>
                          </>
                        ) : (
                          <>
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                          </>
                        )}
                      </svg>
                    </button>
                  </div>
                </div>

                <div className="verification-buttons">
                  <button type="submit" className="auth-btn" disabled={isLoading}>
                    {isLoading ? 'Verifying...' : 'Verify & Continue'}
                  </button>
                  <button 
                    type="button" 
                    className="auth-btn cancel-btn" 
                    onClick={handleCancelMarketChange}
                    disabled={isLoading}
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* Market Selection */}
          {step === 'market-select' && (
            <div className="form-section active">
              {successMessage && (
                <div className="success-message">{successMessage}</div>
              )}
              {errorMessage && (
                <div className="error-message">{errorMessage}</div>
              )}

              <div className="market-selection-section">
                <div className="market-icon">🌐</div>
                <h3>{isChangingMarket ? 'Change Your Market' : 'Select Your Market'}</h3>
                <p className="market-description">
                  {isChangingMarket 
                    ? 'Choose a different market to access. This will change the data and features available to you.'
                    : 'Choose the market you want to access. This will determine the data and features available to you.'
                  }
                </p>

                <div className="market-options">
                  {allowedMarkets.map((market) => (
                    <div
                      key={market}
                      className={`market-option ${selectedMarket === market ? 'selected' : ''}`}
                      onClick={() => setSelectedMarket(market)}
                    >
                      <div className="market-option-content">
                        <span className="market-flag">
                          {market === 'US' && '🇺🇸'}
                          {market === 'UK' && '🇬🇧'}
                          {market === 'CA' && '🇨🇦'}
                          {market === 'DE' && '🇩🇪'}
                          {market === 'JP' && '🇯🇵'}
                          {!['US','UK','CA','DE','JP'].includes(market) && '🌐'}
                        </span>
                        <span className="market-name">{market}</span>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="market-action-buttons">
                  <button 
                    className="auth-btn market-continue-btn" 
                    onClick={handleMarketSelect}
                    disabled={!selectedMarket}
                  >
                    {isChangingMarket ? 'Change Market' : 'Continue to Dashboard'}
                  </button>
                  {isChangingMarket && (
                    <button 
                      className="auth-btn cancel-btn" 
                      onClick={handleCancelMarketChange}
                    >
                      Cancel
                    </button>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Forgot Password Form */}
          {step === 'forgot-password' && (
            <div className="form-section active">
              {successMessage && (
                <div className="success-message">{successMessage}</div>
              )}
              {errorMessage && (
                <div className="error-message">{errorMessage}</div>
              )}

              <form onSubmit={handleForgotPassword}>
                <div className="form-group">
                  <label htmlFor="reset-identifier">Username or Email</label>
                  <input
                    type="text"
                    id="reset-identifier"
                    value={resetIdentifier}
                    onChange={(e) => setResetIdentifier(e.target.value)}
                    placeholder="Enter your username or email address"
                    required
                    disabled={isLoading}
                  />
                </div>

                <button type="submit" className="auth-btn" disabled={isLoading}>
                  {isLoading ? 'Sending...' : 'Send Reset Link'}
                </button>
              </form>

              <div className="forgot-password">
                <a onClick={() => switchForm('login')}>
                  Back to Sign In
                </a>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Auth;
