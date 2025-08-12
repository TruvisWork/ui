// src/theme.js
import { createTheme } from '@mui/material/styles';

const theme = createTheme({
  palette: {
    primary: {
      main: '#000000', // your custom primary color
      contrastText: '#ffffff'
    },
    secondary: {
      main: '#db1110', // your custom secondary color
      contrastText: '#6c72e8'
    },
    background: {
      default: '#F5F5F5',
    }
  },
  components: {
    MuiLoadingButton: { // ✅ Target LoadingButton directly
      styleOverrides: {
        root: {
          '&:hover': {
            backgroundColor: '#db1110', // Change hover color
          },
        },
      },
    },
  },
  typography: {
    fontFamily: 'Roboto, sans-serif',
  },
});

export default theme;
