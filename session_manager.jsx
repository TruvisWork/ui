import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './SessionManager.css';

const SessionManager = ({ children }) => {
  const [sessionInfo, setSessionInfo] = useState(null);
  const [showWarning, setShowWarning] = useState(false);
  const [timeLeft, setTimeLeft] = useState(null);
  const navigate = useNavigate();
  const warningTimer = useRef(null);
  const sessionTimer = useRef(null);
  const activityTimer = useRef(null);
  const lastActivityRef = useRef(Date.now());
  const isInitializing = useRef(true);
  
  const API_BASE_URL = 'http://localhost:8000';
  
  // Session configuration (should match backend)
  const SESSION_TIMEOUT_MINUTES = 30;
  const WARNING_MINUTES = 5; // Show warning 5 minutes before timeout
  const ACTIVITY_CHECK_INTERVAL = 60000; // Check every minute
  const ACTIVITY_THROTTLE = 30000; // Only track activity every 30 seconds

  useEffect(() => {
    initializeSessionManager();
    
    return () => {
      clearAllTimers();
      removeActivityListeners();
    };
  }, []);

  const initializeSessionManager = async () => {
    // Wait a moment for any ongoing auth processes to complete
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    const sessionValid = await checkSession();
    if (sessionValid) {
      startActivityTracking();
    }
    
    // Mark initialization as complete
    isInitializing.current = false;
  };

  const clearAllTimers = () => {
    if (warningTimer.current) {
      clearTimeout(warningTimer.current);
      warningTimer.current = null;
    }
    if (sessionTimer.current) {
      clearInterval(sessionTimer.current);
      sessionTimer.current = null;
    }
    if (activityTimer.current) {
      clearInterval(activityTimer.current);
      activityTimer.current = null;
    }
  };

  const checkSession = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/session-info`, {
        credentials: 'include'
      });

      if (response.ok) {
        const data = await response.json();
        
        // Only proceed with session scheduling if we have valid session data
        if (data.session && data.session.last_activity) {
          setSessionInfo(data.session);
          
          // Don't schedule warnings during initial load for fresh sessions
          if (!isInitializing.current) {
            scheduleWarning(data.session);
          } else {
            // For fresh sessions, just schedule a delayed check
            setTimeout(() => scheduleWarning(data.session), 5000);
          }
        }
        return true;
      } else if (response.status === 401) {
        // Only handle expiry if not in initial loading phase
        if (!isInitializing.current) {
          handleSessionExpired();
        }
        return false;
      } else {
        console.warn('Session check returned unexpected status:', response.status);
        return false;
      }
    } catch (error) {
      console.error('Session check failed:', error);
      return false;
    }
  };

  const scheduleWarning = (session) => {
    if (!session || !session.last_activity) return;

    try {
      const lastActivity = new Date(session.last_activity);
      const now = new Date();
      const timeSinceActivity = now - lastActivity;
      const timeUntilWarning = (SESSION_TIMEOUT_MINUTES - WARNING_MINUTES) * 60 * 1000 - timeSinceActivity;
      const timeUntilExpiry = SESSION_TIMEOUT_MINUTES * 60 * 1000 - timeSinceActivity;

      clearAllTimers();

      // Don't show warning for very fresh sessions (less than 1 minute old)
      if (timeSinceActivity < 60000) {
        // Schedule check for later
        warningTimer.current = setTimeout(() => {
          checkSession();
        }, Math.max(60000 - timeSinceActivity, 30000)); // Check in 30s to 1min
        return;
      }

      if (timeUntilWarning > 0) {
        warningTimer.current = setTimeout(() => {
          showSessionWarning();
        }, timeUntilWarning);
      } else if (timeUntilExpiry > 0) {
        // Already in warning period
        showSessionWarning();
      } else {
        // Session should be expired
        handleSessionExpired();
      }
    } catch (error) {
      console.error('Error scheduling warning:', error);
    }
  };

  const showSessionWarning = () => {
    // Double check session is actually near expiry before showing warning
    if (sessionInfo) {
      const lastActivity = new Date(sessionInfo.last_activity);
      const now = new Date();
      const timeSinceActivity = now - lastActivity;
      const timeUntilExpiry = SESSION_TIMEOUT_MINUTES * 60 * 1000 - timeSinceActivity;
      
      // Only show warning if less than warning threshold
      if (timeUntilExpiry <= WARNING_MINUTES * 60 * 1000) {
        setShowWarning(true);
        startCountdown();
      } else {
        // Reschedule for correct time
        scheduleWarning(sessionInfo);
      }
    }
  };

  const startCountdown = () => {
    if (sessionTimer.current) {
      clearInterval(sessionTimer.current);
    }

    const updateCountdown = () => {
      if (!sessionInfo) return;

      try {
        const lastActivity = new Date(sessionInfo.last_activity);
        const now = new Date();
        const timeSinceActivity = now - lastActivity;
        const timeUntilExpiry = SESSION_TIMEOUT_MINUTES * 60 * 1000 - timeSinceActivity;

        if (timeUntilExpiry <= 0) {
          handleSessionExpired();
        } else {
          const minutes = Math.floor(timeUntilExpiry / 60000);
          const seconds = Math.floor((timeUntilExpiry % 60000) / 1000);
          setTimeLeft(`${minutes}:${seconds.toString().padStart(2, '0')}`);
        }
      } catch (error) {
        console.error('Error updating countdown:', error);
        handleSessionExpired();
      }
    };

    updateCountdown();
    sessionTimer.current = setInterval(updateCountdown, 1000);
  };

  const startActivityTracking = () => {
    // Track various user activities
    const activities = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'];
    
    const handleActivity = () => {
      const now = Date.now();
      if (now - lastActivityRef.current > ACTIVITY_THROTTLE) {
        lastActivityRef.current = now;
        trackActivity();
        
        // Reset warning if user becomes active
        if (showWarning) {
          setShowWarning(false);
          clearAllTimers();
          checkSession(); // Refresh session info
        }
      }
    };

    // Add event listeners
    activities.forEach(activity => {
      document.addEventListener(activity, handleActivity, { passive: true });
    });

    // Store cleanup function
    window.sessionManagerCleanup = () => {
      activities.forEach(activity => {
        document.removeEventListener(activity, handleActivity);
      });
    };

    // Periodic session validation - less frequent for fresh sessions
    const checkInterval = isInitializing.current ? 5 * 60000 : ACTIVITY_CHECK_INTERVAL; // 5 minutes vs 1 minute
    activityTimer.current = setInterval(() => {
      if (!isInitializing.current || Date.now() - lastActivityRef.current > 5 * 60000) {
        checkSession();
      }
    }, checkInterval);
  };

  const removeActivityListeners = () => {
    if (window.sessionManagerCleanup) {
      window.sessionManagerCleanup();
      delete window.sessionManagerCleanup;
    }
  };

  const trackActivity = async () => {
    try {
      // Make a lightweight request to update session activity
      await fetch(`${API_BASE_URL}/me`, {
        credentials: 'include'
      });
    } catch (error) {
      console.error('Activity tracking failed:', error);
    }
  };

  const handleSessionExpired = () => {
    // Don't show expiry dialog during initial load
    if (isInitializing.current) return;
    
    clearAllTimers();
    setShowWarning(false);
    sessionStorage.clear();
    
    // Show session expired message
    const confirmed = window.confirm('Your session has expired due to inactivity. Please login again.');
    if (confirmed || confirmed === null) {
      navigate('/auth');
    }
  };

  const extendSession = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/me`, {
        credentials: 'include'
      });

      if (response.ok) {
        setShowWarning(false);
        clearAllTimers();
        await checkSession(); // Refresh session info
      } else {
        handleSessionExpired();
      }
    } catch (error) {
      console.error('Failed to extend session:', error);
      handleSessionExpired();
    }
  };

  const logoutNow = async () => {
    try {
      await fetch(`${API_BASE_URL}/logout`, {
        method: 'POST',
        credentials: 'include'
      });
    } catch (error) {
      console.error('Logout failed:', error);
    }
    
    clearAllTimers();
    setShowWarning(false);
    sessionStorage.clear();
    navigate('/auth');
  };

  return (
    <>
      {children}
      
      {/* Session Warning Modal */}
      {showWarning && (
        <div className="session-warning-overlay">
          <div className="session-warning-modal">
            <div className="session-warning-icon">⏰</div>
            <h3>Session Expiring Soon</h3>
            <p>
              Your session will expire in <strong>{timeLeft || '...'}</strong> due to inactivity.
            </p>
            <p>
              Do you want to extend your session?
            </p>
            <div className="session-warning-buttons">
              <button 
                className="btn-primary" 
                onClick={extendSession}
              >
                Extend Session
              </button>
              <button 
                className="btn-secondary" 
                onClick={logoutNow}
              >
                Logout Now
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default SessionManager;
