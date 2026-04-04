import { useEffect, useState } from "react";
import { apiRequest } from "../api/client";

export default function CustomersPage({ token, tenantId }) {
  const [items, setItems] = useState([]);
  const [form, setForm] = useState({ name: "", document: "", phone: "", email: "" });
  const [error, setError] = useState("");

  const load = async () => {
    const data = await apiRequest("/api/v1/customers", { token, tenantId });
    setItems(data);
  };

  useEffect(() => {
    load().catch((err) => setError(err.message));
  }, []);

  const onSubmit = async (event) => {
    event.preventDefault();
    setError("");

    try {
      await apiRequest("/api/v1/customers", {
        method: "POST",
        body: form,
        token,
        tenantId
      });
      setForm({ name: "", document: "", phone: "", email: "" });
      await load();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <section className="module-layout">
      <form className="panel" onSubmit={onSubmit}>
        <h3>Novo Cliente</h3>
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Nome" required />
        <input
          value={form.document}
          onChange={(e) => setForm({ ...form, document: e.target.value })}
          placeholder="Documento"
          required
        />
        <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} placeholder="Telefone" required />
        <input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="Email" required />
        <button type="submit">Salvar Cliente</button>
      </form>

      <div className="panel">
        <h3>Base de Clientes</h3>
        {error && <p className="feedback error">{error}</p>}
        <ul className="list">
          {items.map((item) => (
            <li key={item.id}>
              <strong>{item.name}</strong> - {item.phone} - {item.email}
            </li>
          ))}
          {!items.length && <li>Nenhum cliente cadastrado.</li>}
        </ul>
      </div>
    </section>
  );
}
