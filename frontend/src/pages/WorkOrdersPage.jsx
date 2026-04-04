import { useEffect, useMemo, useState } from "react";
import { apiRequest, downloadBlob, openBlobInNewTab } from "../api/client";

const STATUSES = [
  "ENTRADA",
  "EM_ANALISE",
  "AGUARDANDO_APROVACAO",
  "EM_REPARO",
  "FINALIZADO",
  "ENTREGUE"
];

const PRIORITIES = ["BAIXA", "MEDIA", "ALTA", "URGENTE"];

function label(value) {
  return value.replaceAll("_", " ");
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

function actorLabel(value) {
  if (!value || value === "system") {
    return "Automacao do sistema";
  }

  return value;
}

function eventTypeLabel(value) {
  if (!value) {
    return "Atualizacao";
  }

  return label(value);
}

export default function WorkOrdersPage({ token, tenantId, role }) {
  const [items, setItems] = useState([]);
  const [timeline, setTimeline] = useState([]);
  const [metrics, setMetrics] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [priorityFilter, setPriorityFilter] = useState("ALL");
  const [viewMode, setViewMode] = useState("kanban");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [createForm, setCreateForm] = useState({
    customerName: "",
    customerPhone: "",
    equipment: "",
    defectDescription: "",
    notes: "",
    priority: "MEDIA",
    technicianName: "",
    estimatedCompletionDate: "",
    serviceCost: ""
  });
  const [attachment, setAttachment] = useState(null);

  const [detailsForm, setDetailsForm] = useState({
    priority: "MEDIA",
    technicianName: "",
    estimatedCompletionDate: "",
    serviceCost: "",
    customerPhone: "",
    notes: ""
  });

  const canDelete = role === "ADMIN" || role === "ATENDENTE";

  const selectedOrder = useMemo(
    () => items.find((item) => item.id === selectedId) || null,
    [items, selectedId]
  );

  const kanbanColumns = useMemo(() => {
    const columns = {};
    STATUSES.forEach((status) => {
      columns[status] = items.filter((item) => item.status === status);
    });
    return columns;
  }, [items]);

  const highlightedStatuses = useMemo(
    () => ["ENTRADA", "EM_REPARO", "FINALIZADO"].map((status) => ({ status, total: metrics?.byStatus?.[status] || 0 })),
    [metrics]
  );

  const buildListPath = () => {
    const params = new URLSearchParams();
    if (statusFilter !== "ALL") {
      params.set("status", statusFilter);
    }
    if (priorityFilter !== "ALL") {
      params.set("priority", priorityFilter);
    }
    if (search.trim()) {
      params.set("search", search.trim());
    }

    const query = params.toString();
    return query ? `/api/v1/work-orders?${query}` : "/api/v1/work-orders";
  };

  const loadOrders = async () => {
    const data = await apiRequest(buildListPath(), { token, tenantId });
    setItems(data);

    if (selectedId && !data.some((item) => item.id === selectedId)) {
      setSelectedId(null);
      setTimeline([]);
    }
  };

  const loadMetrics = async () => {
    const data = await apiRequest("/api/v1/work-orders/metrics", { token, tenantId });
    setMetrics(data);
  };

  const loadTimeline = async (id) => {
    const data = await apiRequest(`/api/v1/work-orders/${id}/timeline`, { token, tenantId });
    setTimeline(data);
  };

  const refresh = async () => {
    setLoading(true);
    setError("");
    setSuccess("");
    try {
      await Promise.all([loadOrders(), loadMetrics()]);
      if (selectedId) {
        await loadTimeline(selectedId);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
  }, [search, statusFilter, priorityFilter, token, tenantId]);

  useEffect(() => {
    if (!success) {
      return undefined;
    }

    const timeoutId = window.setTimeout(() => setSuccess(""), 4000);
    return () => window.clearTimeout(timeoutId);
  }, [success]);

  const openDetails = async (item) => {
    setSelectedId(item.id);
    setError("");
    setSuccess("");
    setDetailsForm({
      priority: item.priority || "MEDIA",
      technicianName: item.technicianName || "",
      estimatedCompletionDate: item.estimatedCompletionDate || "",
      serviceCost: item.serviceCost ?? "",
      customerPhone: item.customerPhone || "",
      notes: item.notes || ""
    });

    try {
      await loadTimeline(item.id);
    } catch (err) {
      setError(err.message);
    }
  };

  const closeDetails = () => {
    setSelectedId(null);
    setTimeline([]);
  };

  const createWorkOrder = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const formData = new FormData();
      Object.entries(createForm).forEach(([key, value]) => {
        if (value !== null && value !== undefined && value !== "") {
          formData.append(key, String(value));
        }
      });

      if (attachment) {
        formData.append("attachment", attachment);
      }

      await apiRequest("/api/v1/work-orders", {
        method: "POST",
        formData,
        token,
        tenantId
      });

      setCreateForm({
        customerName: "",
        customerPhone: "",
        equipment: "",
        defectDescription: "",
        notes: "",
        priority: "MEDIA",
        technicianName: "",
        estimatedCompletionDate: "",
        serviceCost: ""
      });
      setAttachment(null);
      await refresh();
      setSuccess("OS criada com sucesso.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const changeStatus = async (workOrderId, status) => {
    setLoading(true);
    setError("");
    setSuccess("");

    try {
      await apiRequest(`/api/v1/work-orders/${workOrderId}/status`, {
        method: "PATCH",
        body: { status },
        token,
        tenantId
      });
      await refresh();
      setSuccess(`Status alterado para ${label(status)}.`);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const saveDetails = async (event) => {
    event.preventDefault();
    if (!selectedId) {
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      await apiRequest(`/api/v1/work-orders/${selectedId}/details`, {
        method: "PATCH",
        body: {
          ...detailsForm,
          serviceCost:
            detailsForm.serviceCost === "" || detailsForm.serviceCost === null
              ? null
              : Number(detailsForm.serviceCost)
        },
        token,
        tenantId
      });
      await refresh();
      setSuccess("Detalhes da OS atualizados com sucesso.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const deleteWorkOrder = async (item) => {
    if (!canDelete) {
      return;
    }

    const confirmed = window.confirm(`Excluir a OS ${item.orderNumber}?`);
    if (!confirmed) {
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      await apiRequest(`/api/v1/work-orders/${item.id}`, {
        method: "DELETE",
        token,
        tenantId
      });

      if (selectedId === item.id) {
        closeDetails();
      }

      await Promise.all([loadOrders(), loadMetrics()]);
      setSuccess("OS excluida com sucesso.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const viewDocument = async (item) => {
    try {
      const blob = await apiRequest(`/api/v1/work-orders/${item.id}/document`, {
        token,
        tenantId,
        responseType: "blob"
      });
      openBlobInNewTab(blob);
    } catch (err) {
      setError(err.message);
    }
  };

  const downloadDocument = async (item) => {
    try {
      const blob = await apiRequest(`/api/v1/work-orders/${item.id}/document?download=true`, {
        token,
        tenantId,
        responseType: "blob"
      });
      downloadBlob(blob, `${item.orderNumber || "os"}.html`);
    } catch (err) {
      setError(err.message);
    }
  };

  const viewAttachment = async (item) => {
    try {
      const blob = await apiRequest(`/api/v1/work-orders/${item.id}/attachment`, {
        token,
        tenantId,
        responseType: "blob"
      });
      openBlobInNewTab(blob);
    } catch (err) {
      setError(err.message);
    }
  };

  const downloadAttachment = async (item) => {
    try {
      const blob = await apiRequest(`/api/v1/work-orders/${item.id}/attachment?download=true`, {
        token,
        tenantId,
        responseType: "blob"
      });
      downloadBlob(blob, item.attachmentFileName || `${item.orderNumber}-anexo`);
    } catch (err) {
      setError(err.message);
    }
  };

  const shareOnWhatsApp = async (item) => {
    try {
      const data = await apiRequest(`/api/v1/work-orders/${item.id}/whatsapp-link`, {
        token,
        tenantId
      });
      window.open(data.url, "_blank", "noopener,noreferrer");
    } catch (err) {
      setError(err.message);
    }
  };

  const statusTotal = (status) => metrics?.byStatus?.[status] || 0;

  return (
    <section className="workbench workbench-pro-refined">
      <div className="module-hero workorders-hero panel">
        <div className="module-hero-copy">
          <span className="module-hero-kicker">Ordem de servico</span>
          <h3>Operacao de OS com leitura executiva e execucao mais profissional.</h3>
          <p>
            Acompanhe entrada, bancada, aprovacao e entrega em um painel mais refinado, com acesso
            rapido ao documento profissional da DaniCell e a linha do tempo do atendimento.
          </p>

          <div className="module-highlight-row">
            <article className="module-highlight-card">
              <span>OS em aberto</span>
              <strong>{metrics?.open ?? 0}</strong>
              <small>Fluxo ativo entre entrada, analise, reparo e finalizacao.</small>
            </article>
            <article className="module-highlight-card">
              <span>Ticket medio</span>
              <strong>{formatMoney(metrics?.averageTicket)}</strong>
              <small>Base media dos servicos processados.</small>
            </article>
          </div>
        </div>

        <div className="module-hero-side">
          <div className="module-mini-stat">
            <span>Urgentes</span>
            <strong>{metrics?.urgent ?? 0}</strong>
            <small>Ordens que pedem prioridade da equipe.</small>
          </div>
          <div className="module-mini-stat">
            <span>Lista atual</span>
            <strong>{items.length}</strong>
            <small>Resultado conforme filtros e busca aplicados.</small>
          </div>
          <div className="module-mini-stat">
            <span>Status filtrado</span>
            <strong>{statusFilter === "ALL" ? "Todos" : label(statusFilter)}</strong>
            <small>Leitura imediata do recorte operacional exibido.</small>
          </div>
        </div>
      </div>

      <div className="workbench-metrics">
        <article className="metric-card">
          <span>Total de OS</span>
          <strong>{metrics?.total ?? 0}</strong>
        </article>
        <article className="metric-card">
          <span>OS em aberto</span>
          <strong>{metrics?.open ?? 0}</strong>
        </article>
        <article className="metric-card urgent">
          <span>Urgentes</span>
          <strong>{metrics?.urgent ?? 0}</strong>
        </article>
        <article className="metric-card">
          <span>Ticket medio</span>
          <strong>{formatMoney(metrics?.averageTicket)}</strong>
        </article>
      </div>

      {success && <p className="feedback success">{success}</p>}

      <div className="workbench-layout">
        <form className="panel create-os workorders-panel-shell" onSubmit={createWorkOrder}>
          <div className="panel-head">
            <div>
              <span className="panel-kicker">Nova OS</span>
              <h4>Abrir atendimento com padrao DaniCell</h4>
              <p>Cadastro direto, organizado e pronto para o documento profissional.</p>
            </div>
          </div>

          <div className="module-form-grid module-form-grid-2">
            <label className="field-stack">
              <span>Cliente</span>
              <input
                value={createForm.customerName}
                onChange={(e) => setCreateForm({ ...createForm, customerName: e.target.value })}
                placeholder="Nome do cliente"
                required
              />
            </label>

            <label className="field-stack">
              <span>WhatsApp</span>
              <input
                value={createForm.customerPhone}
                onChange={(e) => setCreateForm({ ...createForm, customerPhone: e.target.value })}
                placeholder="Telefone ou WhatsApp"
              />
            </label>

            <label className="field-stack field-span-2">
              <span>Equipamento</span>
              <input
                value={createForm.equipment}
                onChange={(e) => setCreateForm({ ...createForm, equipment: e.target.value })}
                placeholder="Modelo ou equipamento"
                required
              />
            </label>

            <label className="field-stack field-span-2">
              <span>Defeito relatado</span>
              <input
                value={createForm.defectDescription}
                onChange={(e) => setCreateForm({ ...createForm, defectDescription: e.target.value })}
                placeholder="Resumo do defeito"
                required
              />
            </label>

            <label className="field-stack">
              <span>Prioridade</span>
              <select
                value={createForm.priority}
                onChange={(e) => setCreateForm({ ...createForm, priority: e.target.value })}
              >
                {PRIORITIES.map((value) => (
                  <option key={value} value={value}>
                    {label(value)}
                  </option>
                ))}
              </select>
            </label>

            <label className="field-stack">
              <span>Tecnico responsavel</span>
              <input
                value={createForm.technicianName}
                onChange={(e) => setCreateForm({ ...createForm, technicianName: e.target.value })}
                placeholder="Tecnico responsavel"
              />
            </label>

            <label className="field-stack">
              <span>Previsao de entrega</span>
              <input
                value={createForm.estimatedCompletionDate}
                onChange={(e) => setCreateForm({ ...createForm, estimatedCompletionDate: e.target.value })}
                type="date"
              />
            </label>

            <label className="field-stack">
              <span>Valor do servico</span>
              <input
                value={createForm.serviceCost}
                onChange={(e) => setCreateForm({ ...createForm, serviceCost: e.target.value })}
                type="number"
                step="0.01"
                min="0"
                placeholder="0,00"
              />
            </label>

            <label className="field-stack field-span-2">
              <span>Observacoes tecnicas</span>
              <textarea
                value={createForm.notes}
                onChange={(e) => setCreateForm({ ...createForm, notes: e.target.value })}
                placeholder="Observacoes tecnicas"
              />
            </label>

            <label className="field-stack field-span-2 file-field-stack">
              <span>Anexo</span>
              <input type="file" onChange={(e) => setAttachment(e.target.files?.[0] || null)} />
            </label>
          </div>

          <button type="submit" disabled={loading}>{loading ? "Processando..." : "Criar OS"}</button>
        </form>

        <div className="panel operations-panel workorders-panel-shell">
          <div className="panel-head panel-head-inline">
            <div>
              <span className="panel-kicker">Operacao</span>
              <h4>Fila de atendimento e andamento da bancada</h4>
              <p>{items.length} ordens retornadas para a consulta atual.</p>
            </div>
            <div className="view-switcher">
              <button type="button" className={viewMode === "kanban" ? "active" : ""} onClick={() => setViewMode("kanban")}>Kanban</button>
              <button type="button" className={viewMode === "list" ? "active" : ""} onClick={() => setViewMode("list")}>Lista</button>
            </div>
          </div>

          <div className="operations-toolbar operations-toolbar-pro">
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por OS, cliente, equipamento ou tecnico"
            />
            <div className="filters-row">
              <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                <option value="ALL">Todos os status</option>
                {STATUSES.map((value) => (
                  <option key={value} value={value}>{label(value)}</option>
                ))}
              </select>
              <select value={priorityFilter} onChange={(e) => setPriorityFilter(e.target.value)}>
                <option value="ALL">Todas prioridades</option>
                {PRIORITIES.map((value) => (
                  <option key={value} value={value}>{label(value)}</option>
                ))}
              </select>
              <button type="button" onClick={refresh} disabled={loading}>
                {loading ? "Atualizando..." : "Atualizar"}
              </button>
            </div>
          </div>

          <div className="workorder-status-strip">
            {highlightedStatuses.map((entry) => (
              <article key={entry.status} className="status-pill">
                <span>{label(entry.status)}</span>
                <strong>{entry.total}</strong>
              </article>
            ))}
          </div>

          {error && <p className="feedback error">{error}</p>}

          {viewMode === "kanban" ? (
            <div className="kanban-board">
              {STATUSES.map((status) => (
                <section key={status} className="kanban-column">
                  <header>
                    <h5>{label(status)}</h5>
                    <span>{statusTotal(status)}</span>
                  </header>
                  <div className="kanban-cards">
                    {kanbanColumns[status]?.map((item) => (
                      <article key={item.id} className={`os-card os-card-pro priority-${(item.priority || "MEDIA").toLowerCase()}`}>
                        <div className="os-card-top">
                          <strong>{item.orderNumber}</strong>
                          <span className={`priority-chip priority-${(item.priority || "MEDIA").toLowerCase()}`}>
                            {label(item.priority || "MEDIA")}
                          </span>
                        </div>
                        <p>{item.customerName}</p>
                        <small>{item.equipment}</small>
                        <div className="os-card-meta">
                          <span>{item.technicianName || "Tecnico a definir"}</span>
                          <span>
                            {item.estimatedCompletionDate
                              ? `Previsao ${formatDate(item.estimatedCompletionDate)}`
                              : "Sem previsao"}
                          </span>
                        </div>
                        <div className="card-actions">
                          <button type="button" onClick={() => openDetails(item)}>Detalhes</button>
                          <button type="button" onClick={() => viewDocument(item)}>Ver OS</button>
                          {canDelete && (
                            <button type="button" className="button-danger" onClick={() => deleteWorkOrder(item)}>
                              Excluir
                            </button>
                          )}
                        </div>
                      </article>
                    ))}
                    {!kanbanColumns[status]?.length && <p className="empty-column">Sem itens</p>}
                  </div>
                </section>
              ))}
            </div>
          ) : (
            <ul className="list advanced-list advanced-list-pro">
              {items.map((item) => (
                <li key={item.id}>
                  <div className="os-line os-line-pro">
                    <div className="os-line-header">
                      <strong>{item.orderNumber}</strong>
                      <span className={`priority-chip priority-${(item.priority || "MEDIA").toLowerCase()}`}>
                        {label(item.priority || "MEDIA")}
                      </span>
                    </div>
                    <p>{item.customerName}</p>
                  </div>
                  <div className="os-meta">
                    <span>{label(item.status)}</span>
                    <span>{item.equipment}</span>
                    <span>{item.technicianName || "Tecnico a definir"}</span>
                    <span>
                      {item.estimatedCompletionDate
                        ? `Previsao ${formatDate(item.estimatedCompletionDate)}`
                        : "Sem previsao"}
                    </span>
                  </div>
                  <div className="os-actions os-actions-tight">
                    <button type="button" onClick={() => openDetails(item)}>Detalhes</button>
                    <button type="button" onClick={() => viewDocument(item)}>Visualizar OS</button>
                    <button type="button" onClick={() => downloadDocument(item)}>Baixar OS</button>
                    {item.hasAttachment && <button type="button" onClick={() => viewAttachment(item)}>Visualizar anexo</button>}
                    {item.hasAttachment && <button type="button" onClick={() => downloadAttachment(item)}>Baixar anexo</button>}
                    <button type="button" onClick={() => shareOnWhatsApp(item)}>WhatsApp</button>
                    {canDelete && (
                      <button type="button" className="button-danger" onClick={() => deleteWorkOrder(item)}>
                        Excluir
                      </button>
                    )}
                  </div>
                </li>
              ))}
              {!items.length && <li className="empty-state">Nenhuma OS encontrada com os filtros atuais.</li>}
            </ul>
          )}
        </div>

        <div className="panel detail-panel workorders-panel-shell">
          <div className="panel-head">
            <div>
              <span className="panel-kicker">Painel da OS</span>
              <h4>Detalhes, status e documento profissional</h4>
              <p>Abra uma ordem para editar a operacao e acompanhar a timeline do atendimento.</p>
            </div>
          </div>

          {!selectedOrder && <p className="feedback">Selecione uma OS para ver timeline e editar operacao.</p>}

          {selectedOrder && (
            <>
              <div className="detail-head detail-head-pro">
                <div>
                  <strong>{selectedOrder.orderNumber}</strong>
                  <span>{selectedOrder.customerName}</span>
                </div>
                <div className="chip-row">
                  <span className="inline-chip">{label(selectedOrder.status)}</span>
                  <span className={`priority-chip priority-${(selectedOrder.priority || "MEDIA").toLowerCase()}`}>
                    {label(selectedOrder.priority || "MEDIA")}
                  </span>
                </div>
              </div>

              <div className="detail-summary-grid">
                <article className="detail-summary-card">
                  <span>Equipamento</span>
                  <strong>{selectedOrder.equipment}</strong>
                </article>
                <article className="detail-summary-card">
                  <span>Previsao</span>
                  <strong>{formatDate(selectedOrder.estimatedCompletionDate)}</strong>
                </article>
                <article className="detail-summary-card">
                  <span>Valor do servico</span>
                  <strong>{formatMoney(selectedOrder.serviceCost)}</strong>
                </article>
              </div>

              <div className="status-quick-actions">
                {STATUSES.map((status) => (
                  <button
                    key={status}
                    type="button"
                    className={selectedOrder.status === status ? "active" : ""}
                    onClick={() => changeStatus(selectedOrder.id, status)}
                  >
                    {label(status)}
                  </button>
                ))}
              </div>

              <form className="details-form module-form-grid module-form-grid-2" onSubmit={saveDetails}>
                <label className="field-stack">
                  <span>Prioridade</span>
                  <select
                    value={detailsForm.priority}
                    onChange={(e) => setDetailsForm({ ...detailsForm, priority: e.target.value })}
                  >
                    {PRIORITIES.map((value) => (
                      <option key={value} value={value}>{label(value)}</option>
                    ))}
                  </select>
                </label>

                <label className="field-stack">
                  <span>Tecnico</span>
                  <input
                    value={detailsForm.technicianName}
                    onChange={(e) => setDetailsForm({ ...detailsForm, technicianName: e.target.value })}
                    placeholder="Tecnico"
                  />
                </label>

                <label className="field-stack">
                  <span>Previsao de entrega</span>
                  <input
                    value={detailsForm.estimatedCompletionDate}
                    onChange={(e) => setDetailsForm({ ...detailsForm, estimatedCompletionDate: e.target.value })}
                    type="date"
                  />
                </label>

                <label className="field-stack">
                  <span>Valor do servico</span>
                  <input
                    value={detailsForm.serviceCost}
                    onChange={(e) => setDetailsForm({ ...detailsForm, serviceCost: e.target.value })}
                    type="number"
                    step="0.01"
                    min="0"
                    placeholder="Valor do servico"
                  />
                </label>

                <label className="field-stack">
                  <span>WhatsApp</span>
                  <input
                    value={detailsForm.customerPhone}
                    onChange={(e) => setDetailsForm({ ...detailsForm, customerPhone: e.target.value })}
                    placeholder="WhatsApp"
                  />
                </label>

                <label className="field-stack field-span-2">
                  <span>Notas internas</span>
                  <textarea
                    value={detailsForm.notes}
                    onChange={(e) => setDetailsForm({ ...detailsForm, notes: e.target.value })}
                    placeholder="Notas internas"
                  />
                </label>

                <button type="submit" disabled={loading} className="field-span-2">Salvar ajustes</button>
              </form>

              <div className="detail-actions">
                <button type="button" onClick={() => viewDocument(selectedOrder)}>Visualizar OS</button>
                <button type="button" onClick={() => downloadDocument(selectedOrder)}>Baixar OS</button>
                {selectedOrder.hasAttachment && (
                  <button type="button" onClick={() => viewAttachment(selectedOrder)}>Visualizar anexo</button>
                )}
                {selectedOrder.hasAttachment && (
                  <button type="button" onClick={() => downloadAttachment(selectedOrder)}>Baixar anexo</button>
                )}
                <button type="button" onClick={() => shareOnWhatsApp(selectedOrder)}>Enviar WhatsApp</button>
                {canDelete && (
                  <button type="button" className="button-danger" onClick={() => deleteWorkOrder(selectedOrder)}>
                    Excluir OS
                  </button>
                )}
                <button type="button" onClick={closeDetails}>Fechar painel</button>
              </div>

              <div className="timeline timeline-pro">
                <div className="panel-head compact">
                  <div>
                    <span className="panel-kicker">Timeline</span>
                    <h5>Acompanhamento do atendimento</h5>
                  </div>
                </div>
                <ul>
                  {timeline.map((entry) => (
                    <li key={entry.id}>
                      <span>{eventTypeLabel(entry.eventType)}</span>
                      <p>{entry.description}</p>
                      <small>{actorLabel(entry.createdBy)}</small>
                      <small>{formatDateTime(entry.createdAt)}</small>
                    </li>
                  ))}
                  {!timeline.length && <li className="empty-state">Sem eventos registrados ainda.</li>}
                </ul>
              </div>
            </>
          )}
        </div>
      </div>
    </section>
  );
}
