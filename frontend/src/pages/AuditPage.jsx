import { useEffect, useMemo, useState } from "react";
import { apiRequest } from "../api/client";

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

export default function AuditPage({ token, tenantId }) {
  const [events, setEvents] = useState([]);
  const [search, setSearch] = useState("");
  const [error, setError] = useState("");

  const filteredEvents = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) {
      return events;
    }

    return events.filter((event) => {
      return [
        event.moduleName,
        event.action,
        event.resourceType,
        event.resourceId,
        event.actor,
        event.details
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term));
    });
  }, [events, search]);

  const load = async () => {
    const data = await apiRequest("/api/v1/audit/events", { token, tenantId });
    setEvents(data);
  };

  useEffect(() => {
    load().catch((err) => setError(err.message));
  }, [token, tenantId]);

  return (
    <section className="audit-screen">
      <div className="panel">
        <h3>Trilha de Auditoria</h3>
        <p className="feedback">Historico das alteracoes do sistema por modulo e usuario.</p>

        <div className="audit-toolbar">
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por modulo, acao, recurso, usuario"
          />
          <button type="button" onClick={() => load().catch((err) => setError(err.message))}>Atualizar</button>
        </div>

        {error && <p className="feedback error">{error}</p>}

        <ul className="list audit-list">
          {filteredEvents.map((event) => (
            <li key={event.id}>
              <div className="audit-head">
                <strong>{event.moduleName}</strong>
                <span>{event.action}</span>
              </div>
              <p className="audit-detail">{event.details || "Sem detalhes"}</p>
              <small>
                Recurso: {event.resourceType} #{event.resourceId || "-"} | Usuario: {event.actor || "system"} | {formatDate(event.createdAt)}
              </small>
            </li>
          ))}
          {!filteredEvents.length && <li>Nenhum evento encontrado para o filtro atual.</li>}
        </ul>
      </div>
    </section>
  );
}
