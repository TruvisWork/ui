@echo off
setlocal

REM Set paths
set BACKEND_DIR=C:\TechDemo\textToSql_4\text-to-Sql
set FRONTEND_DIR=C:\TechDemo\tag-ui-main\tag-ui-main

echo ========================================
echo 🔧 Setting up Backend...
echo ========================================

REM Check if Python is installed
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Python is not installed! Please install Python and try again.
    exit /b 1
)

REM Create virtual environment & install dependencies
cd /d %BACKEND_DIR%
if not exist "venv" (
    echo Creating virtual environment...
    python -m venv venv
)

call venv\Scripts\activate
pip install --upgrade pip
pip install -r requirements.txt

REM Start Backend
echo ✅ Starting Backend...
start cmd /k "cd /d %BACKEND_DIR% && call venv\Scripts\activate && python app.py"

echo ========================================
echo 🚀 Setting up UI...
echo ========================================

REM Check if Node.js is installed
node --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Node.js is not installed! Please install Node.js and try again.
    exit /b 1
)

REM Install frontend dependencies if not installed
cd /d %FRONTEND_DIR%
echo Installing frontend dependencies...
call npm install


REM Start React UI
echo ✅ Starting React UI...
start cmd /k "cd /d %FRONTEND_DIR% && npm run dev"

echo ========================================
echo 🎉 Application is now running in http://localhost:5173/ 
echo 📌 Backend: http://localhost:8443
echo 📌 UI: http://localhost:5173/
echo ========================================

exit /b 0
