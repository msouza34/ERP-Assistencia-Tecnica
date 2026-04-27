import { useEffect, useMemo, useState } from "react";
import { apiRequest, downloadBlob } from "../api/client";

const BUDGET_STATUSES = ["RASCUNHO", "ENVIADO", "APROVADO", "REPROVADO", "EXPIRADO"];

function defaultCreateForm() {
  return {
    customerName: "",
    customerPhone: "",
    equipment: "",
    problemDescription: "",
    itemsDescription: "",
    laborCost: "",
    partsCost: "",
    discountAmount: "",
    validUntil: "",
    notes: ""
  };
}

function toEditForm(item) {
  return {
    customerName: item.customerName || "",
    customerPhone: item.customerPhone || "",
    equipment: item.equipment || "",
    problemDescription: item.problemDescription || "",
    itemsDescription: item.itemsDescription || "",
    laborCost: item.laborCost ?? "",
    partsCost: item.partsCost ?? "",
    discountAmount: item.discountAmount ?? "",
    validUntil: item.validUntil || "",
    notes: item.notes || ""
  };
}

function toMoneyNumber(value) {
  if (value === null || value === undefined || value === "") {
    return 0;
  }
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
}

function buildPayload(form) {
  return {
    customerName: form.customerName.trim(),
    customerPhone: form.customerPhone.trim() || null,
    equipment: form.equipment.trim(),
    problemDescription: form.problemDescription.trim(),
    itemsDescription: form.itemsDescription.trim() || null,
    laborCost: toMoneyNumber(form.laborCost),
    partsCost: toMoneyNumber(form.partsCost),
    discountAmount: toMoneyNumber(form.discountAmount),
    validUntil: form.validUntil || null,
    notes: form.notes.trim() || null
  };
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

function statusLabel(status) {
  return String(status || "RASCUNHO").replaceAll("_", " ").toLowerCase();
}

function statusClass(status) {
  return `status-${String(status || "RASCUNHO").toLowerCase()}`;
}

function safePdfFileName(budgetNumber, fallback = "orcamento") {
  const base = String(budgetNumber || fallback).trim() || fallback;
  const sanitized = base.replace(/[\\/:*?"<>|\r\n]+/g, "_");
  return sanitized.toLowerCase().endsWith(".pdf") ? sanitized : `${sanitized}.pdf`;
}

export default function BudgetsPage({ token, tenantId, role }) {
  const [items, setItems] = useState([]);
  const [metrics, setMetrics] = useState(null);
  const [createForm, setCreateForm] = useState(defaultCreateForm);
  const [editForm, setEditForm] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const canManage = role === "ADMIN" || role === "ATENDENTE";

  const selectedItem = useMemo(
    () => items.find((item) => item.id === selectedId) || null,
    [items, selectedId]
  );

  const previewTotal = useMemo(() => {
    const labor = toMoneyNumber(createForm.laborCost);
    const parts = toMoneyNumber(createForm.partsCost);
    const discount = toMoneyNumber(createForm.discountAmount);
    return Math.max(0, labor + parts - discount);
  }, [createForm]);

  const filteredItems = useMemo(() => {
    const query = search.trim().toLowerCase();

    return items.filter((item) => {
      if (statusFilter && item.status !== statusFilter) {
        return false;
      }

      if (!query) {
        return true;
      }

      const haystack = [
        item.budgetNumber,
        item.customerName,
        item.equipment,
        item.problemDescription,
        statusLabel(item.status)
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return haystack.includes(query);
    });
  }, [items, search, statusFilter]);

  const load = async () => {
    const listPath = statusFilter
      ? `/api/v1/budgets?status=${encodeURIComponent(statusFilter)}`
      : "/api/v1/budgets";

    const [listData, metricsData] = await Promise.all([
      apiRequest(listPath, { token, tenantId }),
      apiRequest("/api/v1/budgets/metrics", { token, tenantId })
    ]);

    setItems(listData);
    setMetrics(metricsData);

    if (selectedId && !listData.some((item) => item.id === selectedId)) {
      setSelectedId(null);
      setEditForm(null);
    }
  };

  useEffect(() => {
    load().catch((err) => setError(err.message));
  }, [token, tenantId, statusFilter]);

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

  const createBudget = async (event) => {
    event.preventDefault();
    if (!canManage) {
      setError("Perfil tecnico nao pode criar orcamentos.");
      return;
    }
    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const saved = await apiRequest("/api/v1/budgets", {
        method: "POST",
        token,
        tenantId,
        body: buildPayload(createForm)
      });

      setCreateForm(defaultCreateForm());
      setSelectedId(saved.id);
      setEditForm(toEditForm(saved));
      await load();
      setSuccess("Orcamento criado com sucesso.");
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
    if (!canManage) {
      setError("Perfil tecnico nao pode editar orcamentos.");
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const updated = await apiRequest(`/api/v1/budgets/${selectedItem.id}`, {
        method: "PATCH",
        token,
        tenantId,
        body: buildPayload(editForm)
      });

      setEditForm(toEditForm(updated));
      await load();
      setSuccess("Orcamento atualizado com sucesso.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const updateStatus = async (item, status) => {
    if (item.status === status) {
      return;
    }
    if (!canManage) {
      setError("Perfil tecnico nao pode alterar status de orcamento.");
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const updated = await apiRequest(`/api/v1/budgets/${item.id}/status`, {
        method: "PATCH",
        token,
        tenantId,
        body: { status }
      });

      if (selectedId === item.id) {
        setEditForm(toEditForm(updated));
      }

      await load();
      setSuccess(`Status alterado para ${statusLabel(status)}.`);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const deleteBudget = async (item) => {
    if (!canManage) {
      setError("Perfil tecnico nao pode excluir orcamentos.");
      return;
    }

    const confirmed = window.confirm(`Excluir o orcamento ${item.budgetNumber}?`);
    if (!confirmed) {
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      await apiRequest(`/api/v1/budgets/${item.id}`, {
        method: "DELETE",
        token,
        tenantId
      });

      if (selectedId === item.id) {
        closeDetails();
      }

      await load();
      setSuccess("Orcamento removido com sucesso.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const downloadBudgetPdf = async (item) => {
    try {
      const blob = await apiRequest(`/api/v1/budgets/${item.id}/document/pdf?download=true`, {
        token,
        tenantId,
        responseType: "blob"
      });
      downloadBlob(blob, safePdfFileName(item.budgetNumber, "orcamento"));
    } catch (err) {
      setError(err.message);
    }
  };

  const shareOnWhatsApp = async (item) => {
    setError("");
    setSuccess("");

    try {
      const [data, pdfBlob] = await Promise.all([
        apiRequest(`/api/v1/budgets/${item.id}/whatsapp-link`, {
          token,
          tenantId
        }),
        apiRequest(`/api/v1/budgets/${item.id}/document/pdf?download=true`, {
          token,
          tenantId,
          responseType: "blob"
        })
      ]);

      const fileName = safePdfFileName(data.pdfFileName || item.budgetNumber, "orcamento");
      const pdfFile = new File([pdfBlob], fileName, { type: "application/pdf" });

      let supportsFileShare = false;
      try {
        supportsFileShare =
          typeof navigator !== "undefined"
          && typeof navigator.share === "function"
          && typeof navigator.canShare === "function"
          && navigator.canShare({ files: [pdfFile] });
      } catch {
        supportsFileShare = false;
      }

      if (supportsFileShare) {
        await navigator.share({
          title: `Orcamento ${item.budgetNumber || ""}`.trim(),
          text: data.message,
          files: [pdfFile]
        });
        setSuccess("PDF do orcamento pronto para envio. Selecione o WhatsApp na tela de compartilhamento.");
        return;
      }

      downloadBlob(pdfBlob, fileName);
      window.open(data.url, "_blank", "noopener,noreferrer");
      setSuccess("WhatsApp aberto e PDF baixado para anexar na conversa.");
    } catch (err) {
      setError(err.message);
    }
  };

  const convertToWorkOrder = async (item) => {
    if (!canManage) {
      setError("Perfil tecnico nao pode converter orcamentos.");
      return;
    }
    if (item.status !== "APROVADO") {
      setError("Apenas orcamentos aprovados podem virar OS.");
      return;
    }

    const confirmed = window.confirm(`Migrar o orcamento ${item.budgetNumber} para uma nova OS?`);
    if (!confirmed) {
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const data = await apiRequest(`/api/v1/budgets/${item.id}/convert-to-work-order`, {
        method: "POST",
        token,
        tenantId
      });
      setSuccess(data.created
        ? `OS ${data.orderNumber} criada a partir do orcamento.`
        : `Este orcamento ja estava migrado para a OS ${data.orderNumber}.`);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="budget-screen finance-screen-pro">
      <div className="module-hero budget-hero panel">
        <div className="module-hero-copy">
          <span className="module-hero-kicker">Modulo de Orcamentos</span>
          <h3>Carteira comercial de orcamentos.</h3>
          <p>Propostas, aprovacao, PDF, WhatsApp e migracao para OS.</p>

          <div className="module-highlight-row">
            <article className="module-highlight-card">
              <span>Pipeline ativo</span>
              <strong>{formatMoney(metrics?.pipelineTotal ?? 0)}</strong>
              <small>Rascunhos e enviados.</small>
            </article>
            <article className="module-highlight-card">
              <span>Aprovados</span>
              <strong>{formatMoney(metrics?.approvedTotal ?? 0)}</strong>
              <small>Prontos para execucao.</small>
            </article>
          </div>
        </div>

        <div className="module-hero-side">
          <div className="module-mini-stat">
            <span>Total</span>
            <strong>{metrics?.total ?? 0}</strong>
            <small>Registrados.</small>
          </div>
          <div className="module-mini-stat">
            <span>Enviados</span>
            <strong>{metrics?.sent ?? 0}</strong>
            <small>Aguardando cliente.</small>
          </div>
          <div className="module-mini-stat">
            <span>Aprovados</span>
            <strong>{metrics?.approved ?? 0}</strong>
            <small>Migraveis para OS.</small>
          </div>
        </div>
      </div>

      <div className="workbench-metrics">
        <article className="metric-card">
          <span>Rascunhos</span>
          <strong>{metrics?.draft ?? 0}</strong>
        </article>
        <article className="metric-card">
          <span>Enviados</span>
          <strong>{metrics?.sent ?? 0}</strong>
        </article>
        <article className="metric-card">
          <span>Reprovados</span>
          <strong>{metrics?.rejected ?? 0}</strong>
        </article>
        <article className="metric-card urgent">
          <span>Expirados</span>
          <strong>{metrics?.expired ?? 0}</strong>
        </article>
      </div>

      {success && <p className="feedback success">{success}</p>}
      {!canManage && (
        <p className="feedback">Perfil tecnico com acesso somente leitura: visualize, baixe PDF e envie por WhatsApp.</p>
      )}

      <div className="finance-layout budget-layout">
        <form className="panel finance-panel-shell" onSubmit={createBudget}>
          <div className="panel-head">
            <div>
              <span className="panel-kicker">Novo orcamento</span>
              <h4>Nova proposta</h4>
              <p>Cliente, problema, itens e valores.</p>
            </div>
          </div>

          <div className="module-form-grid">
            <label className="field-stack field-span-2">
              <span>Cliente</span>
              <input
                value={createForm.customerName}
                onChange={(event) => setCreateForm({ ...createForm, customerName: event.target.value })}
                placeholder="Nome do cliente"
                required
              />
            </label>

            <label className="field-stack">
              <span>Telefone</span>
              <input
                value={createForm.customerPhone}
                onChange={(event) => setCreateForm({ ...createForm, customerPhone: event.target.value })}
                placeholder="(00) 00000-0000"
              />
            </label>

            <label className="field-stack">
              <span>Equipamento</span>
              <input
                value={createForm.equipment}
                onChange={(event) => setCreateForm({ ...createForm, equipment: event.target.value })}
                placeholder="Modelo / tipo"
                required
              />
            </label>

            <label className="field-stack field-span-2">
              <span>Problema relatado</span>
              <textarea
                value={createForm.problemDescription}
                onChange={(event) => setCreateForm({ ...createForm, problemDescription: event.target.value })}
                placeholder="Descreva o defeito informado"
                required
              />
            </label>

            <label className="field-stack field-span-2">
              <span>Itens e servicos previstos</span>
              <textarea
                value={createForm.itemsDescription}
                onChange={(event) => setCreateForm({ ...createForm, itemsDescription: event.target.value })}
                placeholder="Pecas, etapas do reparo e observacoes tecnicas"
              />
            </label>

            <label className="field-stack">
              <span>Mao de obra</span>
              <input
                value={createForm.laborCost}
                onChange={(event) => setCreateForm({ ...createForm, laborCost: event.target.value })}
                type="number"
                step="0.01"
                min="0"
              />
            </label>

            <label className="field-stack">
              <span>Pecas</span>
              <input
                value={createForm.partsCost}
                onChange={(event) => setCreateForm({ ...createForm, partsCost: event.target.value })}
                type="number"
                step="0.01"
                min="0"
              />
            </label>

            <label className="field-stack">
              <span>Desconto</span>
              <input
                value={createForm.discountAmount}
                onChange={(event) => setCreateForm({ ...createForm, discountAmount: event.target.value })}
                type="number"
                step="0.01"
                min="0"
              />
            </label>

            <label className="field-stack">
              <span>Valido ate</span>
              <input
                value={createForm.validUntil}
                onChange={(event) => setCreateForm({ ...createForm, validUntil: event.target.value })}
                type="date"
              />
            </label>

            <label className="field-stack field-span-2">
              <span>Observacoes</span>
              <textarea
                value={createForm.notes}
                onChange={(event) => setCreateForm({ ...createForm, notes: event.target.value })}
                placeholder="Termos comerciais, condicoes especiais ou anotacoes"
              />
            </label>
          </div>

          <p className="field-hint">Total previsto: <strong>{formatMoney(previewTotal)}</strong></p>

          <button type="submit" disabled={!canManage || loading}>
            {!canManage ? "Somente leitura para Tecnico" : (loading ? "Salvando..." : "Criar orcamento")}
          </button>
        </form>

        <div className="panel finance-panel-shell">
          <div className="panel-head panel-head-inline">
            <div>
              <span className="panel-kicker">Carteira de orcamentos</span>
              <h4>Propostas</h4>
              <p>{filteredItems.length} orcamentos exibidos.</p>
            </div>
            <button type="button" onClick={refresh} disabled={loading}>
              {loading ? "Atualizando..." : "Atualizar"}
            </button>
          </div>

          <div className="finance-toolbar finance-toolbar-pro">
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Buscar por numero, cliente, equipamento ou status"
            />
            <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
              <option value="">Todos os status</option>
              {BUDGET_STATUSES.map((status) => (
                <option key={status} value={status}>{statusLabel(status)}</option>
              ))}
            </select>
          </div>

          {error && <p className="feedback error">{error}</p>}

          <ul className="list budget-list">
            {filteredItems.map((item) => (
              <li key={item.id} className={`budget-item ${statusClass(item.status)} ${selectedId === item.id ? "selected" : ""}`}>
                <div className="budget-item-top">
                  <div>
                    <strong>{item.budgetNumber}</strong>
                    <p>{item.customerName}</p>
                    <div className="budget-item-meta">
                      <span>{item.equipment}</span>
                      <span>Validade: {formatDate(item.validUntil)}</span>
                    </div>
                  </div>

                  <div className="budget-item-values">
                    <strong>{formatMoney(item.totalAmount)}</strong>
                    <span className={`budget-status ${statusClass(item.status)}`}>
                      {statusLabel(item.status)}
                    </span>
                  </div>
                </div>

                <div className="finance-item-footer">
                  <div className="chip-row">
                    <span className="inline-chip">Mao de obra {formatMoney(item.laborCost)}</span>
                    <span className="inline-chip">Pecas {formatMoney(item.partsCost)}</span>
                    <span className="inline-chip">Desconto {formatMoney(item.discountAmount)}</span>
                  </div>

                  <div className="os-actions">
                    <button type="button" onClick={() => openDetails(item)}>{canManage ? "Editar" : "Detalhes"}</button>
                    <button type="button" onClick={() => downloadBudgetPdf(item)}>Baixar PDF</button>
                    <button type="button" onClick={() => shareOnWhatsApp(item)}>WhatsApp</button>
                    {canManage && item.status === "APROVADO" && (
                      <button type="button" onClick={() => convertToWorkOrder(item)}>Migrar para OS</button>
                    )}
                    {canManage && (
                      <button type="button" className="button-danger" onClick={() => deleteBudget(item)}>Excluir</button>
                    )}
                  </div>
                </div>
              </li>
            ))}
            {!filteredItems.length && <li className="empty-state">Nenhum orcamento encontrado.</li>}
          </ul>
        </div>

        <div className="panel finance-panel-shell">
          <div className="panel-head">
            <div>
              <span className="panel-kicker">Detalhes</span>
              <h4>{canManage ? "Editar orcamento" : "Detalhes do orcamento"}</h4>
              <p>{canManage ? "Valores, status e conversao para OS." : "Consulta e compartilhamento."}</p>
            </div>
          </div>

          {!selectedItem && (
            <p className="feedback">{canManage ? "Selecione um orcamento para editar e alterar o status." : "Selecione um orcamento para visualizar e compartilhar."}</p>
          )}

          {selectedItem && editForm && (
            <>
              <div className="detail-head detail-head-pro">
                <div>
                  <strong>{selectedItem.budgetNumber}</strong>
                  <span>{selectedItem.customerName}</span>
                </div>
                <span className={`budget-status ${statusClass(selectedItem.status)}`}>
                  {statusLabel(selectedItem.status)}
                </span>
              </div>

              <div className="detail-summary-grid">
                <article className="detail-summary-card">
                  <span>Total</span>
                  <strong>{formatMoney(selectedItem.totalAmount)}</strong>
                </article>
                <article className="detail-summary-card">
                  <span>Valido ate</span>
                  <strong>{formatDate(selectedItem.validUntil)}</strong>
                </article>
                <article className="detail-summary-card">
                  <span>Criado em</span>
                  <strong>{formatDate(selectedItem.createdAt)}</strong>
                </article>
              </div>

              <form className="details-form module-form-grid" onSubmit={saveDetails}>
                <label className="field-stack field-span-2">
                  <span>Cliente</span>
                  <input
                    value={editForm.customerName}
                    onChange={(event) => setEditForm({ ...editForm, customerName: event.target.value })}
                    required
                  />
                </label>

                <label className="field-stack">
                  <span>Telefone</span>
                  <input
                    value={editForm.customerPhone}
                    onChange={(event) => setEditForm({ ...editForm, customerPhone: event.target.value })}
                  />
                </label>

                <label className="field-stack">
                  <span>Equipamento</span>
                  <input
                    value={editForm.equipment}
                    onChange={(event) => setEditForm({ ...editForm, equipment: event.target.value })}
                    required
                  />
                </label>

                <label className="field-stack field-span-2">
                  <span>Problema</span>
                  <textarea
                    value={editForm.problemDescription}
                    onChange={(event) => setEditForm({ ...editForm, problemDescription: event.target.value })}
                    required
                  />
                </label>

                <label className="field-stack field-span-2">
                  <span>Itens e servicos</span>
                  <textarea
                    value={editForm.itemsDescription}
                    onChange={(event) => setEditForm({ ...editForm, itemsDescription: event.target.value })}
                  />
                </label>

                <label className="field-stack">
                  <span>Mao de obra</span>
                  <input
                    value={editForm.laborCost}
                    onChange={(event) => setEditForm({ ...editForm, laborCost: event.target.value })}
                    type="number"
                    step="0.01"
                    min="0"
                  />
                </label>

                <label className="field-stack">
                  <span>Pecas</span>
                  <input
                    value={editForm.partsCost}
                    onChange={(event) => setEditForm({ ...editForm, partsCost: event.target.value })}
                    type="number"
                    step="0.01"
                    min="0"
                  />
                </label>

                <label className="field-stack">
                  <span>Desconto</span>
                  <input
                    value={editForm.discountAmount}
                    onChange={(event) => setEditForm({ ...editForm, discountAmount: event.target.value })}
                    type="number"
                    step="0.01"
                    min="0"
                  />
                </label>

                <label className="field-stack">
                  <span>Valido ate</span>
                  <input
                    value={editForm.validUntil}
                    onChange={(event) => setEditForm({ ...editForm, validUntil: event.target.value })}
                    type="date"
                  />
                </label>

                <label className="field-stack field-span-2">
                  <span>Observacoes</span>
                  <textarea
                    value={editForm.notes}
                    onChange={(event) => setEditForm({ ...editForm, notes: event.target.value })}
                  />
                </label>

                {canManage ? (
                  <button type="submit" disabled={loading} className="field-span-2">
                    {loading ? "Salvando..." : "Salvar alteracoes"}
                  </button>
                ) : (
                  <p className="field-hint field-span-2">Perfil tecnico: edicao bloqueada, use PDF e WhatsApp.</p>
                )}
              </form>

              {canManage && (
                <div className="status-quick-actions">
                  {BUDGET_STATUSES.map((status) => (
                    <button
                      key={status}
                      type="button"
                      className={selectedItem.status === status ? "active" : ""}
                      onClick={() => updateStatus(selectedItem, status)}
                    >
                      {statusLabel(status)}
                    </button>
                  ))}
                </div>
              )}

              <div className="detail-actions">
                <button type="button" onClick={() => downloadBudgetPdf(selectedItem)}>Baixar PDF</button>
                <button type="button" onClick={() => shareOnWhatsApp(selectedItem)}>Enviar WhatsApp</button>
                {canManage && selectedItem.status === "APROVADO" && (
                  <button type="button" onClick={() => convertToWorkOrder(selectedItem)}>Migrar para OS</button>
                )}
                {canManage && (
                  <button type="button" className="button-danger" onClick={() => deleteBudget(selectedItem)}>
                    Excluir orcamento
                  </button>
                )}
                <button type="button" onClick={closeDetails}>
                  Fechar
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </section>
  );
}
