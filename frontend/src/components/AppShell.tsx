import { AppBar, Box, Button, Chip, Container, Toolbar, Typography } from "@mui/material";
import { LogOut, ShieldCheck } from "lucide-react";
import { Outlet } from "react-router-dom";
import { useAuth } from "../features/auth/AuthContext";

export function AppShell() {
  const { user, logout } = useAuth();
  return (
    <Box className="app-shell">
      <AppBar position="static" color="inherit" elevation={0} className="topbar">
        <Toolbar>
          <ShieldCheck size={22} />
          <Box sx={{ flexGrow: 1, ml: 1.5 }}>
            <Typography variant="h6">Northstar Workforce</Typography>
            <Typography variant="caption" color="text.secondary">
              Enterprise HCM portfolio demo
            </Typography>
          </Box>
          {user ? (
            <Chip
              label={`${user.displayName} · ${user.roles.join(", ")}`}
              variant="outlined"
              sx={{ mr: 1.5, display: { xs: "none", md: "inline-flex" } }}
            />
          ) : null}
          <Button variant="text" color="inherit" startIcon={<LogOut size={17} />} onClick={logout}>
            Sign out
          </Button>
        </Toolbar>
      </AppBar>
      <Container maxWidth="xl" className="content">
        <Outlet />
      </Container>
    </Box>
  );
}
