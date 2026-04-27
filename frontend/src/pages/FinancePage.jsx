import { useEffect, useMemo, useState } from "react";
import { apiRequest } from "../api/client";

function defaultCreateForm() {
  return {
    description: "",
    type: "RECEIVABLE",
    amount: "",
    dueDate: "",
    paid: false
  };
}

function toEditForm(item) {
  return {
    description: item.description || "",
    type: item.type || "RECEIVABLE",
    amount: item.amount ?? "",
    dueDate: item.dueDate || "",
    paid: item.paid ?? false
  };
}

function buildPayload(form) {
  return {
    description: form.description.trim(),
    type: form.type,
    amount: Number(form.amount),
    dueDate: form.dueDate,
    paid: Boolean(form.paid)
  };
}

function typeLabel(type) {
  return type === "PAYABLE" ? "A pagar" : "A receber";
}

function statusLabel(item) {
  return item.paid ? "Pago" : "Em aberto";
}

function formatMoney(value) {
  const numeric = Number(value ?? 0);
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL"
  }).format(Number.isFinite(numeric) ? numeric : 0);
}

function formatDate(value) {
  if (!value) {
    return "-";
  }

  const parts = String(value).split("-");
  if (parts.length === 3) {
    return `${parts[2]}/${parts[1]}/${parts[0]}`;
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleDateString("pt-BR");
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleString("pt-BR");
}

function normalizeDate(value) {
  if (!value) {
    return null;
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }

  parsed.setHours(0, 0, 0, 0);
  return parsed;
}

export default function FinancePage({ token, tenantId }) {
  const [items, setItems] = useState([]);
  const [createForm, setCreateForm] = useState(defaultCreateForm);
  const [editForm, setEditForm] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const selectedItem = useMemo(
    () => items.find((item) => item.id === selectedId) || null,
    [items, selectedId]
  );

  const summary = useMemo(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    return items.reduce(
      (accumulator, item) => {
        const amount = Number(item.amount ?? 0);
        const dueDate = normalizeDate(item.dueDate);

        accumulator.totalEntries += 1;

        if (item.paid) {
          accumulator.paidEntries += 1;
        } else {
          accumulator.openEntries += 1;

          if (item.type === "PAYABLE") {
            accumulator.totalPayable += amount;
          } else {
            accumulator.totalReceivable += amount;
          }

          if (dueDate) {
            const diff = Math.round((dueDate.getTime() - today.getTime()) / 86400000);
            if (diff < 0) {
              accumulator.overdueEntries += 1;
            }
            if (diff >= 0 && diff <= 3) {
              accumulator.dueSoonEntries += 1;
            }
          }
        }

        return accumulator;
      },
      {
        totalEntries: 0,
        paidEntries: 0,
        openEntries: 0,
        totalReceivable: 0,
        totalPayable: 0,
        overdueEntries: 0,
        dueSoonEntries: 0
      }
    );
  }, [items]);

  const filteredItems = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) {
      return items;
    }

    return items.filter((item) => {
      const haystack = [
        item.description,
        typeLabel(item.type),
        item.paid ? "pago" : "aberto",
        formatDate(item.dueDate)
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return haystack.includes(query);
    });
  }, [items, search]);

  const projectedBalance = summary.totalReceivable - summary.totalPayable;

  const load = async () => {
    const data = await apiRequest("/api/v1/finance/entries", { token, tenantId });
    setItems(data);

    if (selectedId && !data.some((item) => item.id === selectedId)) {
      setSelectedId(null);
      setEditForm(null);
    }
  };

  useEffect(() => {
    load().catch((err) => setError(err.message));
  }, [token, tenantId]);

  useEffect(() => {
    if (!success) {
      return undefined;
    }

    const timeoutId = window.setTimeout(() => setSuccess(""), 4000);
    return () => window.clearTimeout(timeoutId);
  }, [success]);

  const refresh = async () => {
    setLoading(true);
    setError("");
    setSuccess("");

    try {
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const openDetails = (item) => {
    setSelectedId(item.id);
    setEditForm(toEditForm(item));
    setError("");
    setSuccess("");
  };

  const closeDetails = () => {
    setSelectedId(null);
    setEditForm(null);
  };

  const createEntry = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const saved = await apiRequest("/api/v1/finance/entries", {
        method: "POST",
        token,
        tenantId,
        body: buildPayload(createForm)
      });

      setCreateForm(defaultCreateForm());
      setSelectedId(saved.id);
      setEditForm(toEditForm(saved));
      await load();
      setSuccess("Lancamento criado com sucesso.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const saveDetails = async (event) => {
    event.preventDefault();
    if (!selectedItem || !editForm) {
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const updated = await apiRequest(`/api/v1/finance/entries/${selectedItem.id}`, {
        method: "PATCH",
        token,
        tenantId,
        body: buildPayload(editForm)
      });

      setEditForm(toEditForm(updated));
      await load();
      setSuccess("Lancamento atualizado com sucesso.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const togglePaid = async (item) => {
    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const updated = await apiRequest(`/api/v1/finance/entries/${item.id}/paid`, {
        method: "PATCH",
        token,
        tenantId,
        body: { paid: !item.paid }
      });

      if (selectedId === item.id) {
        setEditForm(toEditForm(updated));
      }

      await load();
      setSuccess(updated.paid ? "Lancamento marcado como pago." : "Lancamento reaberto com sucesso.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const deleteEntry = async (item) => {
    const confirmed = window.confirm(`Excluir o lancamento "${item.description}"?`);
    if (!confirmed) {
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      await apiRequest(`/api/v1/finance/entries/${item.id}`, {
        method: "DELETE",
        token,
        tenantId
      });

      if (selectedId === item.id) {
        closeDetails();
      }

      await load();
      setSuccess("Lancamento excluido com sucesso.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="finance-screen finance-screen-pro">
      <div className="module-hero finance-hero panel">
        <div className="module-hero-copy">
          <span className="module-hero-kicker">Financeiro DaniCell</span>
          <h3>Controle financeiro operacional.</h3>
          <p>Entradas, saidas, vencimentos e baixas em uma tela unica.</p>

          <div className="module-highlight-row">
            <article className="module-highlight-card">
              <span>Saldo projetado</span>
              <strong>{formatMoney(projectedBalance)}</strong>
              <small>Receber menos pagar.</small>
            </article>
            <article className="module-highlight-card">
              <span>Vencendo agora</span>
              <strong>{summary.dueSoonEntries}</strong>
              <small>Hoje ate 3 dias.</small>
            </article>
          </div>
        </div>

        <div className="module-hero-side">
          <div className="module-mini-stat">
            <span>Registros ativos</span>
            <strong>{summary.openEntries}</strong>
            <small>Aguardando baixa.</small>
          </div>
          <div className="module-mini-stat">
            <span>Atrasados</span>
            <strong>{summary.overdueEntries}</strong>
            <small>Passaram do vencimento.</small>
          </div>
          <div className="module-mini-stat">
            <span>Receber em aberto</span>
            <strong>{formatMoney(summary.totalReceivable)}</strong>
            <small>Entrada prevista.</small>
          </div>
        </div>
      </div>

      <div className="workbench-metrics">
        <article className="metric-card">
          <span>Lancamentos</span>
          <strong>{summary.totalEntries}</strong>
        </article>
        <article className="metric-card">
          <span>Em aberto a receber</span>
          <strong>{formatMoney(summary.totalReceivable)}</strong>
        </article>
        <article className="metric-card urgent">
          <span>Em aberto a pagar</span>
          <strong>{formatMoney(summary.totalPayable)}</strong>
        </article>
        <article className="metric-card">
          <span>Ja pagos</span>
          <strong>{summary.paidEntries}</strong>
        </article>
      </div>

      {success && <p className="feedback success">{success}</p>}

      <div className="finance-layout">
        <form className="panel finance-create finance-panel-shell" onSubmit={createEntry}>
          <div className="panel-head">
            <div>
              <span className="panel-kicker">Novo lancamento</span>
              <h4>Registrar conta</h4>
              <p>Descricao, tipo, valor e vencimento.</p>
            </div>
          </div>

          <div className="module-form-grid">
            <label className="field-stack field-span-2">
              <span>Descricao</span>
              <input
                value={createForm.description}
                onChange={(event) => setCreateForm({ ...createForm, description: event.target.value })}
                placeholder="Ex.: troca de tela, pagamento de fornecedor"
                required
              />
            </label>

            <label className="field-stack">
              <span>Tipo</span>
              <select
                value={createForm.type}
                onChange={(event) => setCreateForm({ ...createForm, type: event.target.value })}
              >
                <option value="RECEIVABLE">A receber</option>
                <option value="PAYABLE">A pagar</option>
              </select>
            </label>

            <label className="field-stack">
              <span>Valor</span>
              <input
                value={createForm.amount}
                onChange={(event) => setCreateForm({ ...createForm, amount: event.target.value })}
                type="number"
                step="0.01"
                min="0.01"
                placeholder="0,00"
                required
              />
            </label>

            <label className="field-stack field-span-2">
              <span>Vencimento</span>
              <input
                value={createForm.dueDate}
                onChange={(event) => setCreateForm({ ...createForm, dueDate: event.target.value })}
                type="date"
                required
              />
            </label>
          </div>

          <label className="checkbox-row">
            <input
              checked={createForm.paid}
              onChange={(event) => setCreateForm({ ...createForm, paid: event.target.checked })}
              type="checkbox"
            />
            Lancamento ja pago
          </label>

          <button type="submit" disabled={loading}>
            {loading ? "Salvando..." : "Salvar lancamento"}
          </button>
        </form>

        <div className="panel finance-list-panel finance-panel-shell">
          <div className="panel-head panel-head-inline">
            <div>
              <span className="panel-kicker">Fluxo financeiro</span>
              <h4>Contas e vencimentos</h4>
              <p>
                {filteredItems.length} de {summary.totalEntries} registros visiveis neste momento.
              </p>
            </div>
            <button type="button" onClick={refresh} disabled={loading}>
              {loading ? "Atualizando..." : "Atualizar"}
            </button>
          </div>

          <div className="finance-toolbar finance-toolbar-pro">
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Buscar por descricao, tipo, status ou vencimento"
            />

            <div className="inline-chip-row">
              <span className="inline-chip">{summary.openEntries} em aberto</span>
              <span className="inline-chip">{summary.overdueEntries} atrasados</span>
              <span className="inline-chip">{summary.dueSoonEntries} vencendo agora</span>
            </div>
          </div>

          {error && <p className="feedback error">{error}</p>}

          <ul className="list finance-list">
            {filteredItems.map((item) => (
              <li
                key={item.id}
                className={`finance-item ${item.type === "PAYABLE" ? "payable" : "receivable"} ${item.paid ? "paid" : "open"} ${selectedId === item.id ? "selected" : ""}`}
              >
                <div className="finance-item-top">
                  <div>
                    <strong>{item.description}</strong>
                    <div className="finance-item-meta">
                      <span>Vencimento: {formatDate(item.dueDate)}</span>
                      <span>Criado em: {formatDateTime(item.createdAt)}</span>
                    </div>
                  </div>

                  <div className="finance-item-values">
                    <strong>{formatMoney(item.amount)}</strong>
                    <span className={`finance-status ${item.paid ? "paid" : "open"}`}>
                      {statusLabel(item)}
                    </span>
                  </div>
                </div>

                <div className="finance-item-footer">
                  <div className="chip-row">
                    <span className={`finance-type ${item.type === "PAYABLE" ? "payable" : "receivable"}`}>
                      {typeLabel(item.type)}
                    </span>
                  </div>

                  <div className="os-actions">
                    <button type="button" onClick={() => openDetails(item)}>Editar</button>
                    <button type="button" onClick={() => togglePaid(item)}>
                      {item.paid ? "Marcar aberto" : "Marcar pago"}
                    </button>
                    <button type="button" className="button-danger" onClick={() => deleteEntry(item)}>
                      Excluir
                    </button>
                  </div>
                </div>
              </li>
            ))}
            {!filteredItems.length && <li className="empty-state">Nenhum lancamento encontrado.</li>}
          </ul>
        </div>

        <div className="panel finance-detail finance-panel-shell">
          <div className="panel-head">
            <div>
              <span className="panel-kicker">Edicao</span>
              <h4>Detalhes do lancamento</h4>
              <p>Valor, vencimento, tipo e status.</p>
            </div>
          </div>

          {!selectedItem && (
            <p className="feedback">
              Selecione um lancamento para editar os dados preenchidos e controlar o status.
            </p>
          )}

          {selectedItem && editForm && (
            <>
              <div className="detail-head detail-head-pro">
                <div>
                  <strong>{selectedItem.description}</strong>
                  <span>{typeLabel(selectedItem.type)}</span>
                </div>
                <div className="chip-row">
                  <span className={`finance-status ${selectedItem.paid ? "paid" : "open"}`}>
                    {statusLabel(selectedItem)}
                  </span>
                </div>
              </div>

              <div className="detail-summary-grid">
                <article className="detail-summary-card">
                  <span>Valor atual</span>
                  <strong>{formatMoney(selectedItem.amount)}</strong>
                </article>
                <article className="detail-summary-card">
                  <span>Vencimento</span>
                  <strong>{formatDate(selectedItem.dueDate)}</strong>
                </article>
                <article className="detail-summary-card">
                  <span>Criado em</span>
                  <strong>{formatDateTime(selectedItem.createdAt)}</strong>
                </article>
              </div>

              <form className="details-form module-form-grid" onSubmit={saveDetails}>
                <label className="field-stack field-span-2">
                  <span>Descricao</span>
                  <input
                    value={editForm.description}
                    onChange={(event) => setEditForm({ ...editForm, description: event.target.value })}
                    placeholder="Descricao do lancamento"
                    required
                  />
                </label>

                <label className="field-stack">
                  <span>Tipo</span>
                  <select
                    value={editForm.type}
                    onChange={(event) => setEditForm({ ...editForm, type: event.target.value })}
                  >
                    <option value="RECEIVABLE">A receber</option>
                    <option value="PAYABLE">A pagar</option>
                  </select>
                </label>

                <label className="field-stack">
                  <span>Valor</span>
                  <input
                    value={editForm.amount}
                    onChange={(event) => setEditForm({ ...editForm, amount: event.target.value })}
                    type="number"
                    step="0.01"
                    min="0.01"
                    placeholder="Valor"
                    required
                  />
                </label>

                <label className="field-stack field-span-2">
                  <span>Vencimento</span>
                  <input
                    value={editForm.dueDate}
                    onChange={(event) => setEditForm({ ...editForm, dueDate: event.target.value })}
                    type="date"
                    required
                  />
                </label>

                <label className="checkbox-row field-span-2">
                  <input
                    checked={editForm.paid}
                    onChange={(event) => setEditForm({ ...editForm, paid: event.target.checked })}
                    type="checkbox"
                  />
                  Lancamento pago
                </label>

                <button type="submit" disabled={loading} className="field-span-2">
                  {loading ? "Salvando..." : "Salvar alteracoes"}
                </button>
              </form>

              <div className="detail-actions">
                <button type="button" onClick={() => togglePaid(selectedItem)}>
                  {selectedItem.paid ? "Reabrir lancamento" : "Marcar como pago"}
                </button>
                <button type="button" className="button-danger" onClick={() => deleteEntry(selectedItem)}>
                  Excluir lancamento
                </button>
                <button type="button" onClick={closeDetails}>
                  Fechar edicao
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </section>
  );
}
