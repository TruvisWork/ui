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
  const componentMountTime = useRef(Date.now());
  const isInitialized = useRef(false);
  
  const API_BASE_URL = 'http://localhost:8000';
  
  // Session configuration (should match backend)
  const SESSION_TIMEOUT_MINUTES = 30;
  const WARNING_MINUTES = 5; // Show warning 5 minutes before timeout
  const ACTIVITY_CHECK_INTERVAL = 5 * 60000; // Check every 5 minutes instead of 1 minute
  const ACTIVITY_THROTTLE = 30000; // Only track activity every 30 seconds
  const MIN_SESSION_AGE_FOR_WARNING = 2 * 60000; // Don't warn for sessions younger than 2 minutes

  useEffect(() => {
    initializeSessionManager();
    
    return () => {
      clearAllTimers();
      removeActivityListeners();
    };
  }, []);

  const initializeSessionManager = async () => {
    console.log('SessionManager: Initializing...');
    
    // Wait for any ongoing auth processes to complete
    await new Promise(resolve => setTimeout(resolve, 2000));
    
    const sessionValid = await checkSession(true); // Pass true for initial check
    if (sessionValid) {
      startActivityTracking();
      isInitialized.current = true;
      console.log('SessionManager: Initialization complete');
    }
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

  const checkSession = async (isInitialCheck = false) => {
    try {
      const response = await fetch(`${API_BASE_URL}/session-info`, {
        credentials: 'include'
      });

      if (response.ok) {
        const data = await response.json();
        
        if (data.session && data.session.last_activity) {
          setSessionInfo(data.session);
          
          if (isInitialCheck) {
            console.log('SessionManager: Initial session check - session is valid');
            // For initial check, schedule a much later warning check
            scheduleDelayedWarningCheck(data.session);
          } else if (isInitialized.current) {
            // Only schedule warnings for non-initial checks and after initialization
            scheduleWarning(data.session);
          }
        }
        return true;
      } else if (response.status === 401) {
        // Only handle expiry if fully initialized
        if (isInitialized.current && !isInitialCheck) {
          console.log('SessionManager: Session expired (401)');
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

  const scheduleDelayedWarningCheck = (session) => {
    // For fresh sessions, wait at least 10 minutes before any warning logic
    const delayTime = 10 * 60000; // 10 minutes
    console.log('SessionManager: Scheduling delayed warning check in 10 minutes');
    
    clearAllTimers();
    warningTimer.current = setTimeout(() => {
      console.log('SessionManager: Running delayed warning check');
      scheduleWarning(session);
    }, delayTime);
  };

  const scheduleWarning = (session) => {
    if (!session || !session.last_activity) {
      console.log('SessionManager: No session or last_activity, skipping warning schedule');
      return;
    }

    try {
      const lastActivity = new Date(session.last_activity);
      const now = new Date();
      const timeSinceActivity = now - lastActivity;
      const timeUntilWarning = (SESSION_TIMEOUT_MINUTES - WARNING_MINUTES) * 60 * 1000 - timeSinceActivity;
      const timeUntilExpiry = SESSION_TIMEOUT_MINUTES * 60 * 1000 - timeSinceActivity;
      const sessionAge = now - new Date(session.login_time);

      console.log('SessionManager: Schedule warning check', {
        timeSinceActivity: Math.floor(timeSinceActivity / 1000) + 's',
        timeUntilWarning: Math.floor(timeUntilWarning / 1000) + 's',
        timeUntilExpiry: Math.floor(timeUntilExpiry / 1000) + 's',
        sessionAge: Math.floor(sessionAge / 1000) + 's'
      });

      clearAllTimers();

      // Don't show warnings for very fresh sessions
      if (sessionAge < MIN_SESSION_AGE_FOR_WARNING) {
        console.log('SessionManager: Session too fresh for warnings, scheduling later check');
        const waitTime = MIN_SESSION_AGE_FOR_WARNING - sessionAge + 30000; // Add 30s buffer
        warningTimer.current = setTimeout(() => {
          checkSession();
        }, waitTime);
        return;
      }

      // Don't show warnings if very little time has passed since activity
      if (timeSinceActivity < 60000) { // Less than 1 minute
        console.log('SessionManager: Recent activity detected, scheduling later check');
        warningTimer.current = setTimeout(() => {
          checkSession();
        }, 60000); // Check again in 1 minute
        return;
      }

      if (timeUntilWarning > 0) {
        console.log('SessionManager: Scheduling warning for', Math.floor(timeUntilWarning / 1000), 'seconds');
        warningTimer.current = setTimeout(() => {
          showSessionWarning();
        }, timeUntilWarning);
      } else if (timeUntilExpiry > 0) {
        console.log('SessionManager: In warning period, showing warning now');
        showSessionWarning();
      } else {
        console.log('SessionManager: Session should be expired');
        handleSessionExpired();
      }
    } catch (error) {
      console.error('Error scheduling warning:', error);
    }
  };

  const showSessionWarning = () => {
    if (!sessionInfo) {
      console.log('SessionManager: No session info for warning');
      return;
    }

    const lastActivity = new Date(sessionInfo.last_activity);
    const now = new Date();
    const timeSinceActivity = now - lastActivity;
    const timeUntilExpiry = SESSION_TIMEOUT_MINUTES * 60 * 1000 - timeSinceActivity;
    const sessionAge = now - new Date(sessionInfo.login_time);
    
    console.log('SessionManager: Show warning check', {
      timeSinceActivity: Math.floor(timeSinceActivity / 1000) + 's',
      timeUntilExpiry: Math.floor(timeUntilExpiry / 1000) + 's',
      sessionAge: Math.floor(sessionAge / 1000) + 's'
    });

    // Additional safety checks before showing warning
    if (sessionAge < MIN_SESSION_AGE_FOR_WARNING) {
      console.log('SessionManager: Session still too fresh, not showing warning');
      scheduleWarning(sessionInfo); // Reschedule
      return;
    }

    // Only show warning if actually in warning period
    if (timeUntilExpiry <= WARNING_MINUTES * 60 * 1000 && timeUntilExpiry > 0) {
      console.log('SessionManager: Showing session warning');
      setShowWarning(true);
      startCountdown();
    } else if (timeUntilExpiry <= 0) {
      console.log('SessionManager: Session expired, handling expiry');
      handleSessionExpired();
    } else {
      console.log('SessionManager: Not in warning period yet, rescheduling');
      scheduleWarning(sessionInfo); // Reschedule for correct time
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
          console.log('SessionManager: Countdown expired');
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
    console.log('SessionManager: Starting activity tracking');
    
    // Track various user activities
    const activities = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'];
    
    const handleActivity = () => {
      const now = Date.now();
      if (now - lastActivityRef.current > ACTIVITY_THROTTLE) {
        lastActivityRef.current = now;
        trackActivity();
        
        // Reset warning if user becomes active
        if (showWarning) {
          console.log('SessionManager: User activity detected, hiding warning');
          setShowWarning(false);
          clearAllTimers();
          checkSession();
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

    // Periodic session validation - much less frequent
    activityTimer.current = setInterval(() => {
      if (isInitialized.current) {
        console.log('SessionManager: Periodic session check');
        checkSession();
      }
    }, ACTIVITY_CHECK_INTERVAL);
  };

  const removeActivityListeners = () => {
    if (window.sessionManagerCleanup) {
      window.sessionManagerCleanup();
      delete window.sessionManagerCleanup;
    }
  };

  const trackActivity = async () => {
    try {
      await fetch(`${API_BASE_URL}/me`, {
        credentials: 'include'
      });
    } catch (error) {
      console.error('Activity tracking failed:', error);
    }
  };

  const handleSessionExpired = () => {
    // Don't show expiry dialog during initial load or if not initialized
    if (!isInitialized.current) {
      console.log('SessionManager: Not handling expiry - not initialized');
      return;
    }
    
    console.log('SessionManager: Handling session expiry');
    clearAllTimers();
    setShowWarning(false);
    sessionStorage.clear();
    
    const confirmed = window.confirm('Your session has expired due to inactivity. Please login again.');
    if (confirmed || confirmed === null) {
      navigate('/auth');
    }
  };

  const extendSession = async () => {
    console.log('SessionManager: Extending session');
    try {
      const response = await fetch(`${API_BASE_URL}/me`, {
        credentials: 'include'
      });

      if (response.ok) {
        setShowWarning(false);
        clearAllTimers();
        await checkSession();
        console.log('SessionManager: Session extended successfully');
      } else {
        handleSessionExpired();
      }
    } catch (error) {
      console.error('Failed to extend session:', error);
      handleSessionExpired();
    }
  };

  const logoutNow = async () => {
    console.log('SessionManager: Manual logout');
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
