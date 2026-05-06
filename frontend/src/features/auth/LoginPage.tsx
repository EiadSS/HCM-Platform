import { zodResolver } from "@hookform/resolvers/zod";
import {
  Alert,
  Box,
  Button,
  Chip,
  Container,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography
} from "@mui/material";
import { LogIn, ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Navigate } from "react-router-dom";
import { z } from "zod";
import { api } from "../../api/client";
import { useAuth } from "./AuthContext";
import { demoAccounts } from "./demoAccounts";

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(1)
});

type LoginForm = z.infer<typeof schema>;

export function LoginPage() {
  const { user, login } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [backendStatus, setBackendStatus] = useState<"warming" | "ready" | "slow">("warming");
  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting }
  } = useForm<LoginForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      email: "manager@demo.hcm.local",
      password: "DemoPass123!"
    }
  });

  useEffect(() => {
    let active = true;
    const slowTimer = window.setTimeout(() => {
      if (active) {
        setBackendStatus("slow");
      }
    }, 4000);

    api.wakeBackend()
      .then(() => {
        if (active) {
          setBackendStatus("ready");
        }
      })
      .catch(() => {
        if (active) {
          setBackendStatus("slow");
        }
      })
      .finally(() => window.clearTimeout(slowTimer));

    return () => {
      active = false;
      window.clearTimeout(slowTimer);
    };
  }, []);

  if (user) {
    return <Navigate to="/" replace />;
  }

  const submit = handleSubmit(async (values) => {
    setError(null);
    try {
      await login(values.email, values.password);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to sign in");
    }
  });

  return (
    <Box className="login-shell">
      <Container maxWidth="lg">
        <Box className="login-grid">
          <Stack spacing={3}>
            <Chip icon={<ShieldCheck size={16} />} label="Enterprise HCM workforce demo" className="hero-chip" />
            <Box>
              <Typography variant="h3" component="h1" className="login-title">
                Northstar Workforce Command Center
              </Typography>
              <Typography variant="body1" className="login-copy">
                A Spring Boot + React portfolio system for role-based workforce operations, scheduling risk, timesheet approvals, payroll preview explanations, CSV import quality, and auditability.
              </Typography>
            </Box>
            <Paper className="demo-panel" elevation={0}>
              <Typography variant="overline">Guided recruiter path</Typography>
              <Typography variant="body2">
                Manager login is prefilled. Review schedule warnings, approve a submitted timesheet, open the payroll preview, then check the audit log.
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                Need a fresh walkthrough? Use the System Admin quick-fill, then click Reset Demo Data from the dashboard.
              </Typography>
            </Paper>
            <Alert severity={backendStatus === "ready" ? "success" : backendStatus === "slow" ? "warning" : "info"} className="backend-wake-alert">
              {backendStatus === "ready"
                ? "Backend is awake. You can sign in normally."
                : backendStatus === "slow"
                  ? "The hosted backend may be waking from free-tier inactivity. First sign-in can take about a minute, then the demo runs normally."
                  : "Warming the hosted backend now. Free demo servers can sleep after inactivity, so the first request may take a moment."}
            </Alert>
          </Stack>

          <Paper className="login-card" elevation={0}>
            <Stack spacing={2.25}>
              <Box>
                <Typography variant="h5">Sign in</Typography>
                <Typography variant="body2" color="text.secondary">
                  Use a seeded role account to explore a populated tenant.
                </Typography>
              </Box>

              {error ? <Alert severity="error">{error}</Alert> : null}

              <Box component="form" onSubmit={submit}>
                <Stack spacing={2}>
                  <TextField
                    label="Email"
                    autoComplete="email"
                    {...register("email")}
                    error={Boolean(errors.email)}
                    helperText={errors.email?.message}
                  />
                  <TextField
                    label="Password"
                    type="password"
                    autoComplete="current-password"
                    {...register("password")}
                    error={Boolean(errors.password)}
                    helperText={errors.password?.message}
                  />
                  <Button type="submit" variant="contained" size="large" disabled={isSubmitting} startIcon={<LogIn size={18} />}>
                    Sign in to demo
                  </Button>
                </Stack>
              </Box>

              <Divider />

              <Stack spacing={1}>
                <Typography variant="overline">Demo quick-fill</Typography>
                {demoAccounts.map((account) => (
                  <Button
                    key={account.email}
                    variant="outlined"
                    className="demo-account"
                    onClick={() => {
                      setValue("email", account.email);
                      setValue("password", account.password);
                    }}
                  >
                    <span>{account.label}</span>
                    <small>{account.focus}</small>
                  </Button>
                ))}
              </Stack>
            </Stack>
          </Paper>
        </Box>
      </Container>
    </Box>
  );
}
