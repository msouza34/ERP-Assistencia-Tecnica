import { useEffect, useMemo, useState } from "react";
import { apiRequest } from "../api/client";

function label(value) {
  return value.replaceAll("_", " ");
}

function formatMoney(value) {
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL"
  }).format(Number(value ?? 0));
}

export default function DashboardPage({ token, tenantId }) {
  const [summary, setSummary] = useState(null);
  const [metrics, setMetrics] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let mounted = true;

    Promise.all([
      apiRequest("/api/v1/dashboard/summary", { token, tenantId }),
      apiRequest("/api/v1/work-orders/metrics", { token, tenantId })
    ])
      .then(([summaryData, metricsData]) => {
        if (mounted) {
          setSummary(summaryData);
          setMetrics(metricsData);
        }
      })
      .catch((err) => {
        if (mounted) {
          setError(err.message);
        }
      });

    return () => {
      mounted = false;
    };
  }, [token, tenantId]);

  const statusBars = useMemo(() => {
    if (!metrics?.byStatus) {
      return [];
    }

    const total = metrics.total || 1;
    return Object.entries(metrics.byStatus).map(([status, count]) => ({
      status,
      count,
      width: Math.max(6, Math.round((count / total) * 100))
    }));
  }, [metrics]);

  if (error) {
    return <p className="feedback error">Nao foi possivel carregar o dashboard: {error}</p>;
  }

  if (!summary || !metrics) {
    return <p className="feedback">Carregando dashboard...</p>;
  }

  const cards = [
    { label: "OS em aberto", value: metrics.open },
    { label: "OS urgentes", value: metrics.urgent },
    { label: "Baixo estoque", value: summary.lowStockItems },
    { label: "Ticket medio", value: formatMoney(metrics.averageTicket) },
    { label: "Total a pagar", value: formatMoney(summary.totalPayable) },
    { label: "Total a receber", value: formatMoney(summary.totalReceivable) }
  ];

  return (
    <section className="dashboard-pro dashboard-pro-hero">
      <div className="dashboard-banner panel">
        <div className="dashboard-banner-copy">
          <span className="dashboard-banner-kicker">Visao do dia</span>
          <h2>Operacao central organizada para a rotina da DaniCell.</h2>
          <p>
            Acompanhe os principais indicadores, os gargalos da equipe e o equilibrio entre
            atendimento, pecas e caixa em um unico painel.
          </p>
        </div>

        <div className="dashboard-banner-highlight">
          <span>Receita prevista</span>
          <strong>{formatMoney(metrics.forecastRevenue)}</strong>
          <small>{metrics.total} ordens acompanhadas no ambiente atual</small>
        </div>
      </div>

      <div className="module-grid dashboard-card-grid">
        {cards.map((card) => (
          <article key={card.label} className="card card-emphasis">
            <h3>{card.label}</h3>
            <p>{card.value}</p>
          </article>
        ))}
      </div>

      <div className="dashboard-split-grid">
        <div className="panel status-distribution">
          <h3>Distribuicao operacional por status</h3>
          <ul>
            {statusBars.map((entry) => (
              <li key={entry.status}>
                <div className="status-head">
                  <span>{label(entry.status)}</span>
                  <strong>{entry.count}</strong>
                </div>
                <div className="status-bar-track">
                  <div className="status-bar-fill" style={{ width: `${entry.width}%` }} />
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div className="panel dashboard-focus-list">
          <h3>Prioridades imediatas</h3>
          <ul className="list">
            <li>
              <strong>{metrics.urgent}</strong> OS com urgencia pedem acompanhamento direto da bancada.
            </li>
            <li>
              <strong>{summary.lowStockItems}</strong> itens em estoque merecem reposicao para evitar atraso no reparo.
            </li>
            <li>
              <strong>{formatMoney(summary.totalReceivable)}</strong> seguem em aberto para entrada no caixa.
            </li>
          </ul>
        </div>
      </div>
    </section>
  );
}
