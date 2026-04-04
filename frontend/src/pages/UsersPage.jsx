import { useEffect, useMemo, useState } from "react";
import { apiRequest } from "../api/client";

const ROLES = ["ADMIN", "ATENDENTE", "TECNICO"];

function defaultCreateForm() {
  return {
    username: "",
    displayName: "",
    password: "",
    role: "ATENDENTE",
    active: true
  };
}

function toEditForm(user) {
  return {
    username: user.username || "",
    displayName: user.displayName || "",
    role: user.role || "ATENDENTE",
    active: user.active ?? true
  };
}

function formatDate(value) {
  if (!value) {
    return "-";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleString("pt-BR");
}

export default function UsersPage({ token, tenantId }) {
  const [users, setUsers] = useState([]);
  const [createForm, setCreateForm] = useState(defaultCreateForm);
  const [selectedId, setSelectedId] = useState(null);
  const [editForm, setEditForm] = useState(null);
  const [passwordForm, setPasswordForm] = useState({ newPassword: "" });
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const filteredUsers = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) {
      return users;
    }

    return users.filter((user) =>
      [user.username, user.displayName, user.role]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term))
    );
  }, [users, search]);

  const selectedUser = useMemo(() => users.find((item) => item.id === selectedId) || null, [users, selectedId]);

  const load = async () => {
    const data = await apiRequest("/api/v1/users", { token, tenantId });
    setUsers(data);

    if (selectedId && !data.some((item) => item.id === selectedId)) {
      setSelectedId(null);
      setEditForm(null);
      setPasswordForm({ newPassword: "" });
    }
  };

  useEffect(() => {
    load().catch((err) => setError(err.message));
  }, [token, tenantId]);

  const openDetails = (user) => {
    setSelectedId(user.id);
    setEditForm(toEditForm(user));
    setPasswordForm({ newPassword: "" });
  };

  const createUser = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError("");

    try {
      await apiRequest("/api/v1/users", {
        method: "POST",
        token,
        tenantId,
        body: createForm
      });

      setCreateForm(defaultCreateForm());
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const updateUser = async (event) => {
    event.preventDefault();
    if (!selectedUser || !editForm) {
      return;
    }

    setLoading(true);
    setError("");

    try {
      await apiRequest(`/api/v1/users/${selectedUser.id}`, {
        method: "PATCH",
        token,
        tenantId,
        body: editForm
      });

      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const changePassword = async (event) => {
    event.preventDefault();
    if (!selectedUser) {
      return;
    }

    setLoading(true);
    setError("");

    try {
      await apiRequest(`/api/v1/users/${selectedUser.id}/password`, {
        method: "PATCH",
        token,
        tenantId,
        body: passwordForm
      });

      setPasswordForm({ newPassword: "" });
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="users-screen">
      <div className="workbench-header">
        <h3>Gestao de Usuarios e Perfis</h3>
        <p>Crie acessos reais do sistema e defina permissoes por perfil operacional.</p>
      </div>

      <div className="users-layout">
        <form className="panel users-create" onSubmit={createUser}>
          <h4>Novo Usuario</h4>
          <input
            value={createForm.username}
            onChange={(e) => setCreateForm({ ...createForm, username: e.target.value })}
            placeholder="Login (ex: joao.tecnico)"
            required
          />
          <input
            value={createForm.displayName}
            onChange={(e) => setCreateForm({ ...createForm, displayName: e.target.value })}
            placeholder="Nome exibicao"
            required
          />
          <input
            value={createForm.password}
            onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })}
            type="password"
            minLength={8}
            placeholder="Senha inicial"
            required
          />
          <select
            value={createForm.role}
            onChange={(e) => setCreateForm({ ...createForm, role: e.target.value })}
          >
            {ROLES.map((role) => (
              <option key={role} value={role}>{role}</option>
            ))}
          </select>
          <label className="checkbox-row">
            <input
              checked={createForm.active}
              onChange={(e) => setCreateForm({ ...createForm, active: e.target.checked })}
              type="checkbox"
            />
            Usuario ativo
          </label>
          <button type="submit" disabled={loading}>{loading ? "Salvando..." : "Criar usuario"}</button>
        </form>

        <div className="panel users-list-panel">
          <div className="users-toolbar">
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por login, nome ou perfil"
            />
            <button type="button" onClick={() => load().catch((err) => setError(err.message))}>Atualizar</button>
          </div>

          {error && <p className="feedback error">{error}</p>}

          <ul className="list users-list">
            {filteredUsers.map((user) => (
              <li key={user.id} className={`user-item ${!user.active ? "inactive" : ""}`}>
                <div className="user-head">
                  <strong>{user.displayName}</strong>
                  <span>{user.role}</span>
                </div>
                <small>Login: {user.username}</small>
                <small>Ultimo acesso: {formatDate(user.lastLoginAt)}</small>
                <small>Status: {user.active ? "Ativo" : "Inativo"}</small>
                <div className="os-actions">
                  <button type="button" onClick={() => openDetails(user)}>Editar</button>
                </div>
              </li>
            ))}
            {!filteredUsers.length && <li>Nenhum usuario encontrado.</li>}
          </ul>
        </div>

        <div className="panel users-detail">
          <h4>Edicao de Usuario</h4>
          {!selectedUser && <p className="feedback">Selecione um usuario para editar perfil e senha.</p>}

          {selectedUser && editForm && (
            <>
              <div className="detail-head">
                <strong>{selectedUser.displayName}</strong>
                <span>{selectedUser.username}</span>
                <small>Criado em {formatDate(selectedUser.createdAt)}</small>
              </div>

              <form className="details-form" onSubmit={updateUser}>
                <input
                  value={editForm.username}
                  onChange={(e) => setEditForm({ ...editForm, username: e.target.value })}
                  placeholder="Login"
                  required
                />
                <input
                  value={editForm.displayName}
                  onChange={(e) => setEditForm({ ...editForm, displayName: e.target.value })}
                  placeholder="Nome exibicao"
                  required
                />
                <select
                  value={editForm.role}
                  onChange={(e) => setEditForm({ ...editForm, role: e.target.value })}
                >
                  {ROLES.map((role) => (
                    <option key={role} value={role}>{role}</option>
                  ))}
                </select>
                <label className="checkbox-row">
                  <input
                    checked={editForm.active}
                    onChange={(e) => setEditForm({ ...editForm, active: e.target.checked })}
                    type="checkbox"
                  />
                  Usuario ativo
                </label>
                <button type="submit" disabled={loading}>{loading ? "Salvando..." : "Salvar perfil"}</button>
              </form>

              <form className="details-form" onSubmit={changePassword}>
                <h5>Alterar Senha</h5>
                <input
                  value={passwordForm.newPassword}
                  onChange={(e) => setPasswordForm({ newPassword: e.target.value })}
                  type="password"
                  minLength={8}
                  placeholder="Nova senha"
                  required
                />
                <button type="submit" disabled={loading}>{loading ? "Atualizando..." : "Atualizar senha"}</button>
              </form>
            </>
          )}
        </div>
      </div>
    </section>
  );
}
