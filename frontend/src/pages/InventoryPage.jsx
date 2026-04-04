import { useEffect, useMemo, useState } from "react";
import { apiRequest } from "../api/client";

const MOVEMENT_TYPES = ["ENTRY", "EXIT", "ADJUSTMENT"];

function movementLabel(type) {
  if (type === "ENTRY") {
    return "Entrada";
  }
  if (type === "EXIT") {
    return "Saida";
  }
  return "Ajuste";
}

function actorLabel(value) {
  if (!value || value === "system") {
    return "Automacao do sistema";
  }

  return value;
}

function statusLabel(active) {
  return active ? "Ativo" : "Inativo";
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

  return parsed.toLocaleString("pt-BR");
}

function signedDelta(value) {
  const delta = Number(value ?? 0);
  if (delta > 0) {
    return `+${delta}`;
  }
  return `${delta}`;
}

function defaultCreateForm() {
  return {
    sku: "",
    name: "",
    category: "",
    supplierName: "",
    location: "",
    quantity: 0,
    minQuantity: 0,
    unitCost: "",
    salePrice: "",
    notes: "",
    active: true
  };
}

function toEditForm(item) {
  return {
    sku: item.sku || "",
    name: item.name || "",
    category: item.category || "",
    supplierName: item.supplierName || "",
    location: item.location || "",
    minQuantity: item.minQuantity ?? 0,
    unitCost: item.unitCost ?? "",
    salePrice: item.salePrice ?? "",
    notes: item.notes || "",
    active: item.active ?? true
  };
}

function defaultMovementForm() {
  return {
    type: "ENTRY",
    quantity: "",
    targetQuantity: "",
    reason: "",
    referenceCode: ""
  };
}

export default function InventoryPage({ token, tenantId, role }) {
  const [items, setItems] = useState([]);
  const [metrics, setMetrics] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [movements, setMovements] = useState([]);

  const [filters, setFilters] = useState({
    search: "",
    category: "",
    onlyLowStock: false,
    active: "ALL"
  });

  const [createForm, setCreateForm] = useState(defaultCreateForm);
  const [editForm, setEditForm] = useState(null);
  const [movementForm, setMovementForm] = useState(defaultMovementForm);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const canDelete = role === "ADMIN" || role === "ATENDENTE";

  const selectedItem = useMemo(
    () => items.find((item) => item.id === selectedId) || null,
    [items, selectedId]
  );

  const categories = useMemo(() => {
    if (!metrics?.byCategory) {
      return [];
    }

    return Object.keys(metrics.byCategory).sort((a, b) => a.localeCompare(b));
  }, [metrics]);

  const inventorySnapshot = useMemo(
    () => ({
      categoryCount: categories.length,
      inactiveItems: Math.max((metrics?.totalItems ?? 0) - (metrics?.activeItems ?? 0), 0),
      alertCount: alerts.length
    }),
    [alerts.length, categories.length, metrics?.activeItems, metrics?.totalItems]
  );

  const buildListPath = () => {
    const params = new URLSearchParams();

    if (filters.search.trim()) {
      params.set("search", filters.search.trim());
    }

    if (filters.category.trim()) {
      params.set("category", filters.category.trim());
    }

    if (filters.onlyLowStock) {
      params.set("onlyLowStock", "true");
    }

    if (filters.active === "true" || filters.active === "false") {
      params.set("active", filters.active);
    }

    const query = params.toString();
    return query ? `/api/v1/inventory/items?${query}` : "/api/v1/inventory/items";
  };

  const loadMovements = async (itemId) => {
    const data = await apiRequest(`/api/v1/inventory/items/${itemId}/movements`, { token, tenantId });
    setMovements(data);
  };

  const loadInventory = async () => {
    const [itemsData, metricsData, alertData] = await Promise.all([
      apiRequest(buildListPath(), { token, tenantId }),
      apiRequest("/api/v1/inventory/metrics", { token, tenantId }),
      apiRequest("/api/v1/inventory/alerts/low-stock", { token, tenantId })
    ]);

    setItems(itemsData);
    setMetrics(metricsData);
    setAlerts(alertData);

    if (selectedId && !itemsData.some((item) => item.id === selectedId)) {
      setSelectedId(null);
      setEditForm(null);
      setMovements([]);
    }
  };

  const refresh = async () => {
    setLoading(true);
    setError("");
    setSuccess("");

    try {
      await loadInventory();
      if (selectedId) {
        await loadMovements(selectedId);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
  }, [token, tenantId, filters.search, filters.category, filters.onlyLowStock, filters.active]);

  useEffect(() => {
    if (!success) {
      return undefined;
    }

    const timeoutId = window.setTimeout(() => setSuccess(""), 4000);
    return () => window.clearTimeout(timeoutId);
  }, [success]);

  const openDetails = async (item) => {
    setSelectedId(item.id);
    setEditForm(toEditForm(item));
    setMovementForm(defaultMovementForm());
    setError("");
    setSuccess("");

    try {
      await loadMovements(item.id);
    } catch (err) {
      setError(err.message);
    }
  };

  const closeDetails = () => {
    setSelectedId(null);
    setEditForm(null);
    setMovements([]);
    setMovementForm(defaultMovementForm());
  };

  const createItem = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError("");
    setSuccess("");

    try {
      await apiRequest("/api/v1/inventory/items", {
        method: "POST",
        token,
        tenantId,
        body: {
          ...createForm,
          quantity: Number(createForm.quantity),
          minQuantity: Number(createForm.minQuantity),
          unitCost: createForm.unitCost === "" ? null : Number(createForm.unitCost),
          salePrice: createForm.salePrice === "" ? null : Number(createForm.salePrice)
        }
      });

      setCreateForm(defaultCreateForm());
      await refresh();
      setSuccess("Item cadastrado com sucesso.");
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
      await apiRequest(`/api/v1/inventory/items/${selectedItem.id}`, {
        method: "PATCH",
        token,
        tenantId,
        body: {
          ...editForm,
          minQuantity: Number(editForm.minQuantity),
          unitCost: editForm.unitCost === "" ? null : Number(editForm.unitCost),
          salePrice: editForm.salePrice === "" ? null : Number(editForm.salePrice)
        }
      });

      await refresh();
      setSuccess("Item atualizado com sucesso.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const applyMovement = async (event) => {
    event.preventDefault();
    if (!selectedItem) {
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const payload = {
        type: movementForm.type,
        reason: movementForm.reason || null,
        referenceCode: movementForm.referenceCode || null
      };

      if (movementForm.type === "ADJUSTMENT") {
        payload.targetQuantity = Number(movementForm.targetQuantity);
      } else {
        payload.quantity = Number(movementForm.quantity);
      }

      await apiRequest(`/api/v1/inventory/items/${selectedItem.id}/movement`, {
        method: "PATCH",
        token,
        tenantId,
        body: payload
      });

      setMovementForm(defaultMovementForm());
      await refresh();
      await loadMovements(selectedItem.id);
      setSuccess("Movimentacao aplicada com sucesso.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const deleteItem = async (item) => {
    if (!canDelete) {
      return;
    }

    const confirmed = window.confirm(`Excluir o item "${item.name}" do estoque?`);
    if (!confirmed) {
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      await apiRequest(`/api/v1/inventory/items/${item.id}`, {
        method: "DELETE",
        token,
        tenantId
      });

      if (selectedId === item.id) {
        closeDetails();
      }

      await refresh();
      setSuccess("Item excluido com sucesso.");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="inventory-pro inventory-pro-refined">
      <div className="module-hero inventory-hero panel">
        <div className="module-hero-copy">
          <span className="module-hero-kicker">Estoque DaniCell</span>
          <h3>Controle de pecas com leitura rapida, alerta visivel e acao imediata.</h3>
          <p>
            Cadastre itens, acompanhe reposicao e aplique movimentacoes com uma visao mais clara
            do patrimonio e dos gargalos da bancada.
          </p>

          <div className="module-highlight-row">
            <article className="module-highlight-card">
              <span>Valor em estoque</span>
              <strong>{formatMoney(metrics?.totalStockValue)}</strong>
              <small>Base financeira das pecas disponiveis no momento.</small>
            </article>
            <article className="module-highlight-card">
              <span>Itens em alerta</span>
              <strong>{metrics?.lowStockItems ?? 0}</strong>
              <small>Produtos abaixo da cobertura minima configurada.</small>
            </article>
          </div>
        </div>

        <div className="module-hero-side">
          <div className="module-mini-stat">
            <span>Categorias ativas</span>
            <strong>{inventorySnapshot.categoryCount}</strong>
            <small>Organizacao atual do catalogo de pecas.</small>
          </div>
          <div className="module-mini-stat">
            <span>Itens inativos</span>
            <strong>{inventorySnapshot.inactiveItems}</strong>
            <small>Registros fora de operacao, sem poluir a rotina.</small>
          </div>
          <div className="module-mini-stat">
            <span>Alertas monitorados</span>
            <strong>{inventorySnapshot.alertCount}</strong>
            <small>Resumo pronto para reposicao e compra.</small>
          </div>
        </div>
      </div>

      <div className="workbench-metrics">
        <article className="metric-card">
          <span>Total de itens</span>
          <strong>{metrics?.totalItems ?? 0}</strong>
        </article>
        <article className="metric-card">
          <span>Itens ativos</span>
          <strong>{metrics?.activeItems ?? 0}</strong>
        </article>
        <article className="metric-card urgent">
          <span>Baixo estoque</span>
          <strong>{metrics?.lowStockItems ?? 0}</strong>
        </article>
        <article className="metric-card">
          <span>Unidades totais</span>
          <strong>{metrics?.totalUnits ?? 0}</strong>
        </article>
        <article className="metric-card">
          <span>Valor em estoque</span>
          <strong>{formatMoney(metrics?.totalStockValue)}</strong>
        </article>
      </div>

      {success && <p className="feedback success">{success}</p>}

      <div className="inventory-layout">
        <form className="panel inventory-create inventory-panel-shell" onSubmit={createItem}>
          <div className="panel-head">
            <div>
              <span className="panel-kicker">Novo item</span>
              <h4>Adicionar peca ao catalogo</h4>
              <p>Cadastro direto, sem excesso visual, pronto para a rotina da assistencia.</p>
            </div>
          </div>

          <div className="module-form-grid module-form-grid-3">
            <label className="field-stack">
              <span>SKU</span>
              <input
                value={createForm.sku}
                onChange={(e) => setCreateForm({ ...createForm, sku: e.target.value })}
                placeholder="Codigo interno"
                required
              />
            </label>

            <label className="field-stack field-span-2">
              <span>Nome da peca</span>
              <input
                value={createForm.name}
                onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                placeholder="Nome da peca"
                required
              />
            </label>

            <label className="field-stack">
              <span>Categoria</span>
              <input
                value={createForm.category}
                onChange={(e) => setCreateForm({ ...createForm, category: e.target.value })}
                placeholder="Categoria"
              />
            </label>

            <label className="field-stack">
              <span>Fornecedor</span>
              <input
                value={createForm.supplierName}
                onChange={(e) => setCreateForm({ ...createForm, supplierName: e.target.value })}
                placeholder="Fornecedor"
              />
            </label>

            <label className="field-stack">
              <span>Localizacao</span>
              <input
                value={createForm.location}
                onChange={(e) => setCreateForm({ ...createForm, location: e.target.value })}
                placeholder="Prateleira ou gaveta"
              />
            </label>

            <label className="field-stack">
              <span>Estoque inicial</span>
              <input
                value={createForm.quantity}
                onChange={(e) => setCreateForm({ ...createForm, quantity: e.target.value })}
                type="number"
                min="0"
                placeholder="0"
                required
              />
            </label>

            <label className="field-stack">
              <span>Estoque minimo</span>
              <input
                value={createForm.minQuantity}
                onChange={(e) => setCreateForm({ ...createForm, minQuantity: e.target.value })}
                type="number"
                min="0"
                placeholder="0"
                required
              />
            </label>

            <label className="field-stack">
              <span>Custo unitario</span>
              <input
                value={createForm.unitCost}
                onChange={(e) => setCreateForm({ ...createForm, unitCost: e.target.value })}
                type="number"
                min="0"
                step="0.01"
                placeholder="0,00"
              />
            </label>

            <label className="field-stack">
              <span>Preco de venda</span>
              <input
                value={createForm.salePrice}
                onChange={(e) => setCreateForm({ ...createForm, salePrice: e.target.value })}
                type="number"
                min="0"
                step="0.01"
                placeholder="0,00"
              />
            </label>

            <label className="field-stack field-span-3">
              <span>Observacoes</span>
              <textarea
                value={createForm.notes}
                onChange={(e) => setCreateForm({ ...createForm, notes: e.target.value })}
                placeholder="Detalhes de compra, aplicacao ou compatibilidade"
              />
            </label>
          </div>

          <label className="checkbox-row">
            <input
              checked={createForm.active}
              onChange={(e) => setCreateForm({ ...createForm, active: e.target.checked })}
              type="checkbox"
            />
            Item ativo
          </label>

          <button type="submit" disabled={loading}>{loading ? "Salvando..." : "Cadastrar item"}</button>
        </form>

        <div className="panel inventory-catalog inventory-panel-shell">
          <div className="panel-head panel-head-inline">
            <div>
              <span className="panel-kicker">Catalogo</span>
              <h4>Pecas prontas para consulta e reposicao</h4>
              <p>{items.length} itens retornados para os filtros aplicados.</p>
            </div>
            <button type="button" onClick={refresh} disabled={loading}>
              {loading ? "Atualizando..." : "Atualizar"}
            </button>
          </div>

          <div className="inventory-toolbar inventory-toolbar-pro">
            <input
              value={filters.search}
              onChange={(e) => setFilters({ ...filters, search: e.target.value })}
              placeholder="Buscar por nome, SKU, categoria, fornecedor"
            />
            <div className="inventory-filter-grid">
              <input
                list="inventory-categories"
                value={filters.category}
                onChange={(e) => setFilters({ ...filters, category: e.target.value })}
                placeholder="Categoria"
              />
              <datalist id="inventory-categories">
                {categories.map((category) => (
                  <option key={category} value={category} />
                ))}
              </datalist>

              <select
                value={filters.active}
                onChange={(e) => setFilters({ ...filters, active: e.target.value })}
              >
                <option value="ALL">Todos</option>
                <option value="true">Somente ativos</option>
                <option value="false">Somente inativos</option>
              </select>

              <label className="checkbox-row checkbox-row-pill">
                <input
                  checked={filters.onlyLowStock}
                  onChange={(e) => setFilters({ ...filters, onlyLowStock: e.target.checked })}
                  type="checkbox"
                />
                Somente baixo estoque
              </label>
            </div>
          </div>

          {error && <p className="feedback error">{error}</p>}

          <ul className="list inventory-list">
            {items.map((item) => (
              <li key={item.id} className={`inventory-item ${item.lowStock ? "low" : ""}`}>
                <div className="inventory-item-head inventory-item-head-pro">
                  <div>
                    <strong>{item.name}</strong>
                    <span>{item.sku}</span>
                  </div>

                  <div className="chip-row">
                    <span className={`inventory-chip ${item.active ? "active" : "inactive"}`}>
                      {statusLabel(item.active)}
                    </span>
                    {item.lowStock && <span className="inventory-chip alert">Reposicao</span>}
                  </div>
                </div>

                <div className="inventory-item-meta">
                  <span>{item.category || "Sem categoria"}</span>
                  <span>Fornecedor: {item.supplierName || "Nao informado"}</span>
                  <span>Local: {item.location || "Nao definido"}</span>
                </div>

                <div className="inventory-item-values inventory-item-values-pro">
                  <small>Qtd: {item.quantity}</small>
                  <small>Min: {item.minQuantity}</small>
                  <small>Custo: {formatMoney(item.unitCost)}</small>
                  <small>Venda: {formatMoney(item.salePrice)}</small>
                  <small>Estoque: {formatMoney(item.stockValue)}</small>
                </div>

                <div className="os-actions">
                  <button type="button" onClick={() => openDetails(item)}>Detalhes</button>
                  {canDelete && (
                    <button type="button" className="button-danger" onClick={() => deleteItem(item)}>
                      Excluir
                    </button>
                  )}
                </div>
              </li>
            ))}
            {!items.length && <li className="empty-state">Nenhum item encontrado para os filtros atuais.</li>}
          </ul>
        </div>

        <div className="panel inventory-detail inventory-panel-shell">
          <div className="panel-head">
            <div>
              <span className="panel-kicker">Painel do item</span>
              <h4>Detalhes, ajustes e movimentacao</h4>
              <p>Abra um item para revisar cadastro, alterar estoque e acompanhar historico.</p>
            </div>
          </div>

          {!selectedItem && <p className="feedback">Selecione um item para editar e movimentar o estoque.</p>}

          {selectedItem && editForm && (
            <>
              <div className="detail-head detail-head-pro">
                <div>
                  <strong>{selectedItem.name}</strong>
                  <span>{selectedItem.sku}</span>
                </div>
                <div className="chip-row">
                  <span className={`inventory-chip ${selectedItem.active ? "active" : "inactive"}`}>
                    {statusLabel(selectedItem.active)}
                  </span>
                  {selectedItem.lowStock && <span className="inventory-chip alert">Baixo estoque</span>}
                </div>
              </div>

              <div className="detail-summary-grid inventory-summary-grid">
                <article className="detail-summary-card">
                  <span>Saldo atual</span>
                  <strong>{selectedItem.quantity}</strong>
                </article>
                <article className="detail-summary-card">
                  <span>Minimo</span>
                  <strong>{selectedItem.minQuantity}</strong>
                </article>
                <article className="detail-summary-card">
                  <span>Atualizado em</span>
                  <strong>{formatDate(selectedItem.updatedAt)}</strong>
                </article>
              </div>

              <form className="details-form module-form-grid module-form-grid-2" onSubmit={saveDetails}>
                <label className="field-stack">
                  <span>SKU</span>
                  <input
                    value={editForm.sku}
                    onChange={(e) => setEditForm({ ...editForm, sku: e.target.value })}
                    placeholder="SKU"
                    required
                  />
                </label>

                <label className="field-stack">
                  <span>Nome</span>
                  <input
                    value={editForm.name}
                    onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                    placeholder="Nome"
                    required
                  />
                </label>

                <label className="field-stack">
                  <span>Categoria</span>
                  <input
                    value={editForm.category}
                    onChange={(e) => setEditForm({ ...editForm, category: e.target.value })}
                    placeholder="Categoria"
                  />
                </label>

                <label className="field-stack">
                  <span>Fornecedor</span>
                  <input
                    value={editForm.supplierName}
                    onChange={(e) => setEditForm({ ...editForm, supplierName: e.target.value })}
                    placeholder="Fornecedor"
                  />
                </label>

                <label className="field-stack">
                  <span>Localizacao</span>
                  <input
                    value={editForm.location}
                    onChange={(e) => setEditForm({ ...editForm, location: e.target.value })}
                    placeholder="Localizacao"
                  />
                </label>

                <label className="field-stack">
                  <span>Estoque minimo</span>
                  <input
                    value={editForm.minQuantity}
                    onChange={(e) => setEditForm({ ...editForm, minQuantity: e.target.value })}
                    type="number"
                    min="0"
                    placeholder="Estoque minimo"
                    required
                  />
                </label>

                <label className="field-stack">
                  <span>Custo unitario</span>
                  <input
                    value={editForm.unitCost}
                    onChange={(e) => setEditForm({ ...editForm, unitCost: e.target.value })}
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="Custo unitario"
                  />
                </label>

                <label className="field-stack">
                  <span>Preco de venda</span>
                  <input
                    value={editForm.salePrice}
                    onChange={(e) => setEditForm({ ...editForm, salePrice: e.target.value })}
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="Preco de venda"
                  />
                </label>

                <label className="field-stack field-span-2">
                  <span>Observacoes</span>
                  <textarea
                    value={editForm.notes}
                    onChange={(e) => setEditForm({ ...editForm, notes: e.target.value })}
                    placeholder="Observacoes"
                  />
                </label>

                <label className="checkbox-row field-span-2">
                  <input
                    checked={editForm.active}
                    onChange={(e) => setEditForm({ ...editForm, active: e.target.checked })}
                    type="checkbox"
                  />
                  Item ativo
                </label>

                <button type="submit" disabled={loading} className="field-span-2">
                  {loading ? "Salvando..." : "Salvar dados"}
                </button>
              </form>

              <form className="movement-form movement-form-pro" onSubmit={applyMovement}>
                <div className="panel-head compact">
                  <div>
                    <span className="panel-kicker">Movimentacao</span>
                    <h5>Ajustar entrada, saida ou saldo final</h5>
                  </div>
                </div>

                <div className="module-form-grid module-form-grid-2">
                  <label className="field-stack">
                    <span>Tipo de ajuste</span>
                    <select
                      value={movementForm.type}
                      onChange={(e) => setMovementForm({ ...movementForm, type: e.target.value })}
                    >
                      {MOVEMENT_TYPES.map((type) => (
                        <option key={type} value={type}>{movementLabel(type)}</option>
                      ))}
                    </select>
                  </label>

                  {movementForm.type === "ADJUSTMENT" ? (
                    <label className="field-stack">
                      <span>Nova quantidade final</span>
                      <input
                        value={movementForm.targetQuantity}
                        onChange={(e) => setMovementForm({ ...movementForm, targetQuantity: e.target.value })}
                        type="number"
                        min="0"
                        placeholder="Nova quantidade"
                        required
                      />
                    </label>
                  ) : (
                    <label className="field-stack">
                      <span>Quantidade</span>
                      <input
                        value={movementForm.quantity}
                        onChange={(e) => setMovementForm({ ...movementForm, quantity: e.target.value })}
                        type="number"
                        min="1"
                        placeholder="Quantidade"
                        required
                      />
                    </label>
                  )}

                  <label className="field-stack field-span-2">
                    <span>Codigo de referencia</span>
                    <input
                      value={movementForm.referenceCode}
                      onChange={(e) => setMovementForm({ ...movementForm, referenceCode: e.target.value })}
                      placeholder="NF, pedido ou OS"
                    />
                  </label>

                  <label className="field-stack field-span-2">
                    <span>Motivo</span>
                    <textarea
                      value={movementForm.reason}
                      onChange={(e) => setMovementForm({ ...movementForm, reason: e.target.value })}
                      placeholder="Motivo da movimentacao"
                    />
                  </label>
                </div>

                <button type="submit" disabled={loading}>
                  {loading ? "Aplicando..." : "Aplicar movimentacao"}
                </button>
              </form>

              <div className="detail-actions">
                {canDelete && (
                  <button type="button" className="button-danger" onClick={() => deleteItem(selectedItem)}>
                    Excluir item
                  </button>
                )}
                <button type="button" onClick={closeDetails}>Fechar detalhes</button>
              </div>

              <div className="timeline inventory-timeline timeline-pro">
                <div className="panel-head compact">
                  <div>
                    <span className="panel-kicker">Historico</span>
                    <h5>Movimentacoes do item</h5>
                  </div>
                </div>
                <ul>
                  {movements.map((entry) => (
                    <li key={entry.id}>
                      <span>{movementLabel(entry.type)}</span>
                      <p>
                        {signedDelta(entry.delta)} unidades, de {entry.previousQuantity} para {entry.newQuantity}
                      </p>
                      <small>{entry.reason || "Sem justificativa informada"}</small>
                      <small>
                        Ref: {entry.referenceCode || "-"} | {actorLabel(entry.createdBy)} | {formatDate(entry.createdAt)}
                      </small>
                    </li>
                  ))}
                  {!movements.length && <li className="empty-state">Sem movimentacoes registradas para este item.</li>}
                </ul>
              </div>
            </>
          )}
        </div>
      </div>

      <div className="panel inventory-alerts inventory-alerts-pro">
        <div className="panel-head panel-head-inline">
          <div>
            <span className="panel-kicker">Reposicao</span>
            <h4>Alertas de baixo estoque</h4>
            <p>Leitura rapida do que merece compra ou redistribuicao.</p>
          </div>
          <span className="inline-chip">{alerts.length} alertas</span>
        </div>

        <ul className="list inventory-alert-list">
          {alerts.map((item) => (
            <li key={item.id}>
              <strong>{item.name}</strong>
              <span>{item.sku}</span>
              <small>Qtd atual {item.quantity} | Minimo {item.minQuantity}</small>
            </li>
          ))}
          {!alerts.length && <li className="empty-state">Sem alertas no momento.</li>}
        </ul>
      </div>
    </section>
  );
}
