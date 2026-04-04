import { useEffect, useMemo, useState } from "react";
import { login } from "./api/client";
import AuditPage from "./pages/AuditPage";
import BudgetsPage from "./pages/BudgetsPage";
import CustomersPage from "./pages/CustomersPage";
import DashboardPage from "./pages/DashboardPage";
import FinancePage from "./pages/FinancePage";
import InventoryPage from "./pages/InventoryPage";
import UsersPage from "./pages/UsersPage";
import WorkOrdersPage from "./pages/WorkOrdersPage";

const modules = [
  {
    id: "dashboard",
    label: "Painel Geral",
    short: "01",
    description: "Visao consolidada da operacao, indicadores e prioridades do dia.",
    roles: ["ADMIN", "ATENDENTE", "TECNICO"]
  },
  {
    id: "workorders",
    label: "Ordens de Servico",
    short: "02",
    description: "Entrada, reparo, aprovacao e entrega em um fluxo unico.",
    roles: ["ADMIN", "ATENDENTE", "TECNICO"]
  },
  {
    id: "customers",
    label: "Clientes",
    short: "03",
    description: "Cadastros organizados para relacionamento e atendimento.",
    roles: ["ADMIN", "ATENDENTE"]
  },
  {
    id: "inventory",
    label: "Estoque",
    short: "04",
    description: "Controle de pecas, saldo critico e movimentacoes internas.",
    roles: ["ADMIN", "ATENDENTE", "TECNICO"]
  },
  {
    id: "budgets",
    label: "Orcamentos",
    short: "05",
    description: "Propostas tecnicas, aprovacoes e controle do valor estimado.",
    roles: ["ADMIN", "ATENDENTE"]
  },
  {
    id: "finance",
    label: "Financeiro",
    short: "06",
    description: "Recebimentos, pagamentos e visao do fluxo financeiro.",
    roles: ["ADMIN", "ATENDENTE"]
  },
  {
    id: "users",
    label: "Usuarios",
    short: "07",
    description: "Perfis, acessos e permissoes da equipe operacional.",
    roles: ["ADMIN"]
  },
  {
    id: "audit",
    label: "Auditoria",
    short: "08",
    description: "Rastreabilidade das acoes e seguranca administrativa.",
    roles: ["ADMIN"]
  }
];
const moduleComponents = {
  dashboard: DashboardPage,
  workorders: WorkOrdersPage,
  customers: CustomersPage,
  inventory: InventoryPage,
  budgets: BudgetsPage,
  finance: FinancePage,
  users: UsersPage,
  audit: AuditPage
};

const DEFAULT_TENANT_ID = "public";

function roleLabel(role) {
  if (role === "ADMIN") {
    return "Administracao";
  }
  if (role === "ATENDENTE") {
    return "Atendimento";
  }
  if (role === "TECNICO") {
    return "Tecnica";
  }
  return "Equipe";
}

export default function App() {
  const [token, setToken] = useState(localStorage.getItem("erp_token") || "");
  const [tenantId, setTenantId] = useState(
    localStorage.getItem("erp_token")
      ? localStorage.getItem("erp_tenant") || DEFAULT_TENANT_ID
      : DEFAULT_TENANT_ID
  );
  const [username, setUsername] = useState(localStorage.getItem("erp_user") || "");
  const [role, setRole] = useState(localStorage.getItem("erp_role") || "");
  const [activeModule, setActiveModule] = useState("dashboard");
  const [authForm, setAuthForm] = useState({
    username: "",
    password: "",
    tenantId: DEFAULT_TENANT_ID
  });
  const [authError, setAuthError] = useState("");
  const [authLoading, setAuthLoading] = useState(false);
  const [densityMode, setDensityMode] = useState(localStorage.getItem("erp_density") || "compact");

  const availableModules = useMemo(() => {
    if (!role) {
      return modules;
    }
    return modules.filter((module) => module.roles.includes(role));
  }, [role]);

  const activeModuleConfig = useMemo(
    () => modules.find((module) => module.id === activeModule) || modules[0],
    [activeModule]
  );

  useEffect(() => {
    if (!availableModules.some((module) => module.id === activeModule) && availableModules.length) {
      setActiveModule(availableModules[0].id);
    }
  }, [activeModule, availableModules]);

  useEffect(() => {
    const modeClass = densityMode === "comfortable" ? "density-comfortable" : "density-compact";
    document.body.classList.remove("density-compact", "density-comfortable");
    document.body.classList.add(modeClass);
    localStorage.setItem("erp_density", densityMode);
  }, [densityMode]);

  useEffect(() => {
    if (!token) {
      setTenantId(DEFAULT_TENANT_ID);
      setAuthForm((current) => ({ ...current, tenantId: DEFAULT_TENANT_ID }));
      localStorage.setItem("erp_tenant", DEFAULT_TENANT_ID);
    }
  }, [token]);

  const ActivePage = useMemo(() => moduleComponents[activeModule] || DashboardPage, [activeModule]);

  const handleLogin = async (event) => {
    event.preventDefault();
    setAuthError("");
    setAuthLoading(true);

    try {
      const data = await login({
        username: authForm.username,
        password: authForm.password,
        tenantId: DEFAULT_TENANT_ID
      });
      setToken(data.token);
      setTenantId(data.tenantId || DEFAULT_TENANT_ID);
      setUsername(data.username);
      setRole(data.role || "");
      localStorage.setItem("erp_token", data.token);
      localStorage.setItem("erp_tenant", data.tenantId || DEFAULT_TENANT_ID);
      localStorage.setItem("erp_user", data.username);
      localStorage.setItem("erp_role", data.role || "");
    } catch (err) {
      setAuthError(err.message);
    } finally {
      setAuthLoading(false);
    }
  };

  const handleLogout = () => {
    setToken("");
    setTenantId(DEFAULT_TENANT_ID);
    setUsername("");
    setRole("");
    setAuthForm({ username: "", password: "", tenantId: DEFAULT_TENANT_ID });
    localStorage.removeItem("erp_token");
    localStorage.setItem("erp_tenant", DEFAULT_TENANT_ID);
    localStorage.removeItem("erp_user");
    localStorage.removeItem("erp_role");
  };

  if (!token) {
    return (
      <main className="auth-shell auth-shell-pro">
        <section className="auth-stage">
          <article className="auth-spotlight">
            <span className="auth-kicker">DaniCell Gestao</span>
            <h1>Operacao profissional para assistencia tecnica.</h1>
            <p>
              Controle ordens de servico, estoque, financeiro e atendimento em uma interface
              mais limpa, rapida e pronta para o dia a dia da loja.
            </p>

            <div className="auth-benefits">
              <div className="auth-benefit-card">
                <strong>Fluxo centralizado</strong>
                <span>Equipe, estoque, financeiro e OS trabalhando no mesmo painel.</span>
              </div>
              <div className="auth-benefit-card">
                <strong>Marca DaniCell</strong>
                <span>Visual mais profissional, focado na operacao e sem excesso tecnico na tela.</span>
              </div>
              <div className="auth-benefit-card">
                <strong>Pronto para crescer</strong>
                <span>Base organizada para finalizar o projeto e manter a experiencia consistente.</span>
              </div>
            </div>
          </article>

          <form className="auth-card auth-card-pro" onSubmit={handleLogin}>
            <div className="auth-brand-block">
              <span className="auth-section-label">Acesso seguro</span>
              <h2>Entrar no painel interno</h2>
              <p>Use seu login para acessar a central operacional da DaniCell.</p>
            </div>

            <label className="field-stack">
              <span>Usuario</span>
              <input
                value={authForm.username}
                onChange={(e) => setAuthForm({ ...authForm, username: e.target.value })}
                placeholder="Seu usuario"
                required
              />
            </label>

            <label className="field-stack">
              <span>Senha</span>
              <input
                value={authForm.password}
                onChange={(e) => setAuthForm({ ...authForm, password: e.target.value })}
                placeholder="Sua senha"
                type="password"
                minLength={8}
                required
              />
            </label>

            <input type="hidden" value={authForm.tenantId} readOnly />

            <button type="submit" disabled={authLoading}>
              {authLoading ? "Entrando..." : "Acessar sistema"}
            </button>
            {authError && <p className="feedback error">{authError}</p>}
          </form>
        </section>
      </main>
    );
  }

  return (
    <div className="app-shell app-shell-pro">
      <aside className="sidebar sidebar-pro">
        <div className="sidebar-brand">
          <span className="sidebar-kicker">DaniCell Gestao</span>
          <h2>Assistencia Tecnica</h2>
          <p>Base interna para atendimento, reparo, estoque e controle financeiro.</p>
        </div>

        <div className="sidebar-user-card">
          <span className="sidebar-user-role">{roleLabel(role)}</span>
          <strong>{username}</strong>
          <small>Sessao operacional ativa</small>
        </div>

        <div className="density-toggle" role="group" aria-label="Densidade da interface">
          <button
            type="button"
            className={densityMode === "compact" ? "active" : ""}
            onClick={() => setDensityMode("compact")}
          >
            Modo enxuto
          </button>
          <button
            type="button"
            className={densityMode === "comfortable" ? "active" : ""}
            onClick={() => setDensityMode("comfortable")}
          >
            Modo espacado
          </button>
        </div>

        <nav className="sidebar-nav">
          {availableModules.map((module) => (
            <button
              key={module.id}
              onClick={() => setActiveModule(module.id)}
              className={module.id === activeModule ? "active" : ""}
              type="button"
            >
              <span className="sidebar-nav-index">{module.short}</span>
              <span className="sidebar-nav-copy">
                <strong>{module.label}</strong>
                <small>{module.description}</small>
              </span>
            </button>
          ))}
        </nav>

        <button className="logout sidebar-logout" onClick={handleLogout} type="button">
          Encerrar sessao
        </button>
      </aside>

      <main className="content content-pro">
        <header className="content-header content-header-pro">
          <div>
            <span className="content-kicker">Painel interno DaniCell</span>
            <h1>{activeModuleConfig.label}</h1>
            <p>{activeModuleConfig.description}</p>
          </div>

          <div className="content-header-side">
            <span className="content-role-chip">{roleLabel(role)}</span>
            <strong>{username}</strong>
          </div>
        </header>

        <section className="workspace-surface">
          <ActivePage token={token} tenantId={tenantId} role={role} />
        </section>
      </main>
    </div>
  );
}




