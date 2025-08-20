import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const SessionManager = ({ children }) => {
  const [sessionInfo, setSessionInfo] = useState(null);
  const [showWarning, setShowWarning] = useState(false);
  const [timeLeft, setTimeLeft] = useState(null);
  const navigate = useNavigate();
  const warningTimer = useRef(null);
  const sessionTimer = useRef(null);
  const activityTimer = useRef(null);
  
  const API_BASE_URL = 'http://localhost:8000';
  
  // Session configuration (should match backend)
  const SESSION_TIMEOUT_MINUTES = 30;
  const WARNING_MINUTES = 5; // Show warning 5 minutes before timeout
  const ACTIVITY_CHECK_INTERVAL = 60000; // Check every minute

  useEffect(() => {
    checkSession();
    startActivityTracking();
    
    return () => {
      clearAllTimers();
    };
  }, []);

  const clearAllTimers = () => {
    if (warningTimer.current) clearTimeout(warningTimer.current);
    if (sessionTimer.current) clearTimeout(sessionTimer.current);
    if (activityTimer.current) clearInterval(activityTimer.current);
  };

  const checkSession = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/session-info`, {
        credentials: 'include'
      });

      if (response.ok) {
        const data = await response.json();
        setSessionInfo(data.session);
        scheduleWarning(data.session);
      } else if (response.status === 401) {
        handleSessionExpired();
      }
    } catch (error) {
      console.error('Session check failed:', error);
    }
  };

  const scheduleWarning = (session) => {
    if (!session) return;

    const lastActivity = new Date(session.last_activity);
    const now = new Date();
    const timeSinceActivity = now - lastActivity;
    const timeUntilWarning = (SESSION_TIMEOUT_MINUTES - WARNING_MINUTES) * 60 * 1000 - timeSinceActivity;
    const timeUntilExpiry = SESSION_TIMEOUT_MINUTES * 60 * 1000 - timeSinceActivity;

    clearAllTimers();

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
  };

  const showSessionWarning = () => {
    setShowWarning(true);
    startCountdown();
  };

  const startCountdown = () => {
    const updateCountdown = () => {
      if (!sessionInfo) return;

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
    };

    updateCountdown();
    sessionTimer.current = setInterval(updateCountdown, 1000);
  };

  const startActivityTracking = () => {
    // Track user activity
    const trackActivity = () => {
      // Make a lightweight request to update session activity
      fetch(`${API_BASE_URL}/me`, {
        credentials: 'include'
      }).catch(err => {
        console.error('Activity tracking failed:', err);
      });
    };

    // Track various user activities
    const activities = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'];
    
    let lastActivity = Date.now();
    const handleActivity = () => {
      const now = Date.now();
      if (now - lastActivity > 30000) { // Only track if 30 seconds have passed
        lastActivity = now;
        trackActivity();
        
        // Reset warning if user becomes active
        if (showWarning) {
          setShowWarning(false);
          clearAllTimers();
          checkSession(); // Refresh session info
        }
      }
    };

    activities.forEach(activity => {
      document.addEventListener(activity, handleActivity, { passive: true });
    });

    // Periodic session validation
    activityTimer.current = setInterval(checkSession, ACTIVITY_CHECK_INTERVAL);

    return () => {
      activities.forEach(activity => {
        document.removeEventListener(activity, handleActivity);
      });
    };
  };

  const handleSessionExpired = () => {
    clearAllTimers();
    setShowWarning(false);
    sessionStorage.clear();
    
    // Show session expired message
    alert('Your session has expired due to inactivity. Please login again.');
    navigate('/auth');
  };

  const extendSession = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/me`, {
        credentials: 'include'
      });

      if (response.ok) {
        setShowWarning(false);
        clearAllTimers();
        checkSession(); // Refresh session info
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
              Your session will expire in <strong>{timeLeft}</strong> due to inactivity.
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

      <style jsx>{`
        .session-warning-overlay {
          position: fixed;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          background: rgba(0, 0, 0, 0.7);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 10000;
        }

        .session-warning-modal {
          background: white;
          border-radius: 12px;
          padding: 2rem;
          max-width: 400px;
          width: 90%;
          text-align: center;
          box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
        }

        .session-warning-icon {
          font-size: 3rem;
          margin-bottom: 1rem;
        }

        .session-warning-modal h3 {
          color: #e74c3c;
          margin-bottom: 1rem;
          font-size: 1.5rem;
        }

        .session-warning-modal p {
          margin-bottom: 1rem;
          color: #555;
          line-height: 1.5;
        }

        .session-warning-buttons {
          display: flex;
          gap: 1rem;
          justify-content: center;
          margin-top: 1.5rem;
        }

        .btn-primary {
          background: #3498db;
          color: white;
          border: none;
          padding: 0.75rem 1.5rem;
          border-radius: 6px;
          cursor: pointer;
          font-size: 1rem;
          transition: background 0.2s;
        }

        .btn-primary:hover {
          background: #2980b9;
        }

        .btn-secondary {
          background: #95a5a6;
          color: white;
          border: none;
          padding: 0.75rem 1.5rem;
          border-radius: 6px;
          cursor: pointer;
          font-size: 1rem;
          transition: background 0.2s;
        }

        .btn-secondary:hover {
          background: #7f8c8d;
        }
      `}</style>
    </>
  );
};

export default SessionManager;
