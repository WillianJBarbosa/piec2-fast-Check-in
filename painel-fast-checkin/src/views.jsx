import { useEffect, useState } from 'react';
import { api, data as dt } from './api';

/* ---------- peças de interface ---------- */
const campo = 'rounded border border-tinta/20 bg-white px-3 py-2 focus:outline-2 focus:outline-latao';
const Botao = ({ cor = 'noite', ...p }) => (
  <button {...p} className={`rounded px-3 py-2 text-sm font-bold ${cor === 'noite' ? 'bg-noite text-white hover:bg-noite/90' : cor === 'latao' ? 'bg-latao text-noite hover:brightness-95' : 'border border-tinta/25 hover:bg-tinta/5'} disabled:opacity-60`} />
);
const Rotulo = ({ t, children }) => <label className="grid gap-1 text-sm font-medium">{t}{children}</label>;
const Estado = ({ s }) => (
  <span className={`rounded-full px-2.5 py-0.5 text-xs font-bold ${s === 'CONFIRMED' ? 'bg-emerald-100 text-emerald-900' : 'bg-amber-100 text-amber-900'}`}>
    {{ CONFIRMED: 'Confirmada', PENDING: 'Pendente' }[s] ?? s}
  </span>
);
const Revisar = () => <span className="rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-bold text-red-900">Revisar</span>;
const situacao = (f) => (f.status === 'PENDING' && f.reviewRequired ? <Revisar /> : <Estado s={f.status} />);
const ROSTO = {
  COMPATIVEL: ['Compatível', 'bg-emerald-100 text-emerald-900'],
  REVISAR: ['Revisar', 'bg-amber-100 text-amber-900'],
  DIFERENTE: ['Diferente', 'bg-red-100 text-red-900'],
};
const Rosto = ({ f }) => {
  if (!f.facialResult) return <span className="text-xs text-tinta/60">Não verificado</span>;
  const [nome, cor] = ROSTO[f.facialResult] ?? [f.facialResult, 'bg-tinta/10'];
  return (
    <span className="grid gap-0.5">
      <span className={`w-fit rounded-full px-2.5 py-0.5 text-xs font-bold ${cor}`}>{nome}</span>
      {f.facialConfirmedManually && <span className="text-xs text-tinta/70">conferido pela recepção</span>}
    </span>
  );
};
const confirmar = (f, rodar) => {
  if (f.reviewRequired && !confirm('Você conferiu os dados com o documento do hóspede?')) return;
  const rostoPendente = (f.facialResult === 'REVISAR' || f.facialResult === 'DIFERENTE') && !f.facialConfirmedManually;
  if (rostoPendente && !confirm('O rosto está como "' + ROSTO[f.facialResult][0] + '" e ainda não foi conferido por você. Confirmar a ficha mesmo assim?')) return;
  rodar(() => api(`/fnrh-registrations/${f.id}/confirm`, { method: 'POST' }));
};
const confirmarRosto = (f, rodar) => {
  if (!confirm('Você conferiu pessoalmente que o hóspede é a pessoa do documento?')) return;
  rodar(() => api(`/fnrh-registrations/${f.id}/face-check/confirm`, { method: 'POST' }));
};
const Titulo = ({ t, sub }) => (
  <header className="mb-6"><h1 className="text-2xl font-extrabold text-noite">{t}</h1>{sub && <p className="text-sm text-tinta/70">{sub}</p>}</header>
);
const Tabela = ({ cols, linhas, vazio }) => (
  <div className="overflow-x-auto rounded border border-tinta/15 bg-white">
    <table className="w-full text-left text-sm">
      <thead className="bg-tinta/5"><tr>{cols.map((c) => <th key={c} className="px-3 py-2 font-bold">{c}</th>)}</tr></thead>
      <tbody>
        {linhas.length === 0 && <tr><td colSpan={cols.length} className="px-3 py-6 text-center text-tinta/60">{vazio}</td></tr>}
        {linhas.map((l, i) => <tr key={i} className="border-t border-tinta/10">{l.map((c, j) => <td key={j} className="px-3 py-2">{c}</td>)}</tr>)}
      </tbody>
    </table>
  </div>
);

/* roda uma ação na API, mostra o erro e recarrega os dados */
function useAcao(recarregar) {
  const [erro, setErro] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const rodar = async (fn) => {
    setOcupado(true); setErro('');
    try { await fn(); await recarregar(); return true; }
    catch (e) { setErro(e.message); return false; }
    finally { setOcupado(false); }
  };
  return { erro, ocupado, rodar };
}
const Erro = ({ m }) => m && <p role="alert" className="mb-3 rounded bg-red-50 px-3 py-2 text-sm text-red-800">{m}</p>;
const nomeEstadia = (s, hoteis) => `${hoteis.find((h) => h.id === s.hotelId)?.name ?? 'Hotel'} · ${dt(s.arrivalDate)} a ${dt(s.departureDate)}`;

/* ---------- telas ---------- */
export function Inicio({ dados, recarregar, ir }) {
  const { erro, ocupado, rodar } = useAcao(recarregar);
  const pend = dados.fnrh.filter((f) => f.status === 'PENDING').sort((a, b) => Number(b.reviewRequired) - Number(a.reviewRequired));
  const rev = pend.filter((f) => f.reviewRequired).length;
  const nomeH = (id) => dados.guests.find((g) => g.id === id)?.fullName ?? 'Hóspede';
  return (
    <>
      <section className="relative mb-8 overflow-hidden rounded-lg">
        <img src="/img/hotel-noite.jpg" alt="Fachada do hotel ao anoitecer" className="h-56 w-full object-cover md:h-72" />
        <div className="absolute inset-0 flex flex-col justify-end bg-gradient-to-t from-noite/90 to-transparent p-6 text-white">
          <h1 className="text-3xl font-extrabold">Painel da recepção</h1>
          <p className="text-sm text-white/85">{dados.guests.length} hóspedes · {dados.stays.length} estadias · {pend.length} fichas pendentes · {rev} para revisar</p>
        </div>
      </section>
      <h2 className="mb-1 text-lg font-extrabold text-noite">Fichas para conferir</h2>
      <p className="mb-3 text-sm text-tinta/70">Check-ins feitos no totem. As marcadas como “Revisar” vêm primeiro: confira com o documento do hóspede antes de confirmar.</p>
      <Erro m={erro} />
      <Tabela cols={['Hóspede', 'Estadia', 'Situação', 'Ação']} vazio="Nenhuma ficha pendente. A lista se atualiza sozinha a cada 10 segundos."
        linhas={pend.slice(0, 8).map((f) => [nomeH(f.guestId), nomeEstadia(dados.stays.find((s) => s.id === f.stayId) ?? {}, dados.hotels), situacao(f),
          <Botao key="c" cor="latao" disabled={ocupado} onClick={() => confirmar(f, rodar)}>Confirmar ficha</Botao>])} />
      {pend.length > 8 && <button onClick={() => ir('fichas')} className="mt-2 text-sm underline">Ver todas as fichas</button>}
    </>
  );
}

export function Hospedes({ dados, recarregar }) {
  const { erro, ocupado, rodar } = useAcao(recarregar);
  const vazio = { fullName: '', cpf: '', dateOfBirth: '', documentType: 'RG', documentNumber: '', consent: false };
  const [f, setF] = useState(vazio);
  const [aberto, setAberto] = useState(false);
  const [busca, setBusca] = useState('');
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const filtrados = dados.guests.filter((g) => g.fullName.toLowerCase().includes(busca.toLowerCase()));

  const salvar = async (e) => {
    e.preventDefault();
    const ok = await rodar(() => api('/guests', { method: 'POST', body: { ...f, cpf: f.cpf.replace(/\D/g, ''), consentAccepted: f.consent } }));
    if (ok) { setF(vazio); setAberto(false); }
  };
  return (
    <>
      <Titulo t="Hóspedes" sub="CPF e documento aparecem mascarados. Os dados completos ficam cifrados no banco." />
      <div className="mb-4 flex flex-wrap gap-3">
        <input aria-label="Buscar hóspede" placeholder="Buscar pelo nome" value={busca} onChange={(e) => setBusca(e.target.value)} className={`${campo} min-w-0 flex-1`} />
        <Botao onClick={() => setAberto(!aberto)}>{aberto ? 'Fechar' : 'Novo hóspede'}</Botao>
      </div>
      {aberto && (
        <form onSubmit={salvar} className="mb-6 grid gap-4 rounded border border-tinta/15 bg-white p-5 md:grid-cols-2">
          <Rotulo t="Nome completo"><input required value={f.fullName} onChange={set('fullName')} className={campo} /></Rotulo>
          <Rotulo t="CPF (11 dígitos)"><input required inputMode="numeric" value={f.cpf} onChange={set('cpf')} className={campo} /></Rotulo>
          <Rotulo t="Data de nascimento"><input required type="date" value={f.dateOfBirth} onChange={set('dateOfBirth')} className={campo} /></Rotulo>
          <Rotulo t="Tipo de documento"><select value={f.documentType} onChange={set('documentType')} className={campo}><option>RG</option><option>CNH</option></select></Rotulo>
          <Rotulo t="Número do documento"><input required value={f.documentNumber} onChange={set('documentNumber')} className={campo} /></Rotulo>
          <label className="flex items-start gap-2 self-end text-sm"><input type="checkbox" checked={f.consent} onChange={set('consent')} className="mt-1 accent-latao" />
            O hóspede autorizou o uso dos dados para o check-in (obrigatório).</label>
          <div className="md:col-span-2"><Erro m={erro} /><Botao disabled={ocupado || !f.consent}>Cadastrar hóspede</Botao></div>
        </form>
      )}
      {!aberto && <Erro m={erro} />}
      <Tabela cols={['Nome', 'CPF', 'Nascimento', 'Documento', '']} vazio="Nenhum hóspede encontrado."
        linhas={filtrados.map((g) => [g.fullName, g.cpfMasked, dt(g.dateOfBirth), `${g.documentType} ${g.documentNumberMasked}`,
          <Botao key="x" cor="claro" disabled={ocupado} onClick={() => confirm(`Excluir ${g.fullName}?`) && rodar(() => api(`/guests/${g.id}`, { method: 'DELETE' }))}>Excluir</Botao>])} />
    </>
  );
}

export function Estadias({ dados, recarregar }) {
  const { erro, ocupado, rodar } = useAcao(recarregar);
  const [f, setF] = useState({ hotelId: '', arrivalDate: '', departureDate: '' });
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  return (
    <>
      <Titulo t="Estadias" sub="Escolha o hotel e as datas de chegada e saída." />
      <form onSubmit={(e) => { e.preventDefault(); rodar(() => api('/stays', { method: 'POST', body: f })); }} className="mb-6 grid gap-4 rounded border border-tinta/15 bg-white p-5 md:grid-cols-4">
        <Rotulo t="Hotel"><select required value={f.hotelId} onChange={set('hotelId')} className={campo}><option value="">Selecione</option>{dados.hotels.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}</select></Rotulo>
        <Rotulo t="Chegada"><input required type="date" value={f.arrivalDate} onChange={set('arrivalDate')} className={campo} /></Rotulo>
        <Rotulo t="Saída"><input required type="date" value={f.departureDate} onChange={set('departureDate')} className={campo} /></Rotulo>
        <div className="self-end"><Botao disabled={ocupado}>Criar estadia</Botao></div>
        <div className="md:col-span-4"><Erro m={erro} /></div>
      </form>
      <Tabela cols={['Hotel', 'Chegada', 'Saída', 'Situação', '']} vazio="Nenhuma estadia criada."
        linhas={dados.stays.map((s) => [dados.hotels.find((h) => h.id === s.hotelId)?.name ?? '', dt(s.arrivalDate), dt(s.departureDate), <Estado key="e" s={s.status} />,
          <Botao key="x" cor="claro" disabled={ocupado} onClick={() => confirm('Excluir esta estadia?') && rodar(() => api(`/stays/${s.id}`, { method: 'DELETE' }))}>Excluir</Botao>])} />
    </>
  );
}

export function Fichas({ dados, recarregar }) {
  const { erro, ocupado, rodar } = useAcao(recarregar);
  const [f, setF] = useState({ stayId: '', guestId: '' });
  const [filtro, setFiltro] = useState('pend');
  const [manual, setManual] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const ord = [...dados.fnrh].sort((a, b) => Number(b.reviewRequired) - Number(a.reviewRequired));
  const linhas = ord.filter((x) => filtro === 'todas' || (filtro === 'pend' ? x.status === 'PENDING' : x.status === 'PENDING' && x.reviewRequired));
  const abas = [['revisar', 'Para revisar'], ['pend', 'Pendentes'], ['todas', 'Todas']];
  return (
    <>
      <Titulo t="Fichas FNRH" sub="O recepcionista confere os dados antes de confirmar a ficha." />
      <div className="mb-6">
        <p className="mb-2 text-sm text-tinta/70">As fichas nascem sozinhas quando o totem lê o documento. Crie uma à mão só se o hóspede foi cadastrado manualmente.</p>
        <Botao cor="claro" onClick={() => setManual(!manual)}>{manual ? 'Fechar cadastro manual' : 'Criar ficha manualmente'}</Botao>
      </div>
      {manual && (
      <form onSubmit={(e) => { e.preventDefault(); rodar(() => api('/fnrh-registrations', { method: 'POST', body: f })); }} className="mb-6 grid gap-4 rounded border border-tinta/15 bg-white p-5 md:grid-cols-3">
        <Rotulo t="Estadia"><select required value={f.stayId} onChange={set('stayId')} className={campo}><option value="">Selecione</option>{dados.stays.map((s) => <option key={s.id} value={s.id}>{nomeEstadia(s, dados.hotels)}</option>)}</select></Rotulo>
        <Rotulo t="Hóspede"><select required value={f.guestId} onChange={set('guestId')} className={campo}><option value="">Selecione</option>{dados.guests.map((g) => <option key={g.id} value={g.id}>{g.fullName}</option>)}</select></Rotulo>
        <div className="self-end"><Botao disabled={ocupado}>Criar ficha</Botao></div>
        <div className="md:col-span-3"><Erro m={erro} /></div>
      </form>
      )}
      {!manual && <Erro m={erro} />}
      <div className="mb-3 flex gap-2" role="tablist">
        {abas.map(([id, nome]) => (
          <button key={id} role="tab" aria-selected={filtro === id} onClick={() => setFiltro(id)}
            className={`rounded-full px-4 py-1.5 text-sm font-bold ${filtro === id ? 'bg-noite text-white' : 'border border-tinta/25'}`}>{nome}</button>
        ))}
      </div>
      <Tabela cols={['Hóspede', 'Estadia', 'Situação', 'Rosto', 'Ação']} vazio="Nenhuma ficha neste filtro."
        linhas={linhas.map((x) => [dados.guests.find((g) => g.id === x.guestId)?.fullName ?? '', nomeEstadia(dados.stays.find((s) => s.id === x.stayId) ?? {}, dados.hotels), situacao(x), <Rosto key="r" f={x} />,
          <div key="a" className="flex flex-wrap gap-2">
            {(x.facialResult === 'REVISAR' || x.facialResult === 'DIFERENTE') && !x.facialConfirmedManually && (
              <Botao cor="claro" disabled={ocupado} onClick={() => confirmarRosto(x, rodar)}>Confirmar rosto</Botao>
            )}
            {x.status === 'PENDING' ? <Botao cor="latao" disabled={ocupado} onClick={() => confirmar(x, rodar)}>Confirmar</Botao>
              : <Botao cor="claro" disabled={ocupado} onClick={() => rodar(() => api(`/fnrh-registrations/${x.id}`, { method: 'PUT', body: { status: 'PENDING' } }))}>Voltar a pendente</Botao>}
            <Botao cor="claro" disabled={ocupado} onClick={() => confirm('Excluir esta ficha?') && rodar(() => api(`/fnrh-registrations/${x.id}`, { method: 'DELETE' }))}>Excluir</Botao>
          </div>])} />
    </>
  );
}

export function Hoteis({ dados, recarregar, admin }) {
  const { erro, ocupado, rodar } = useAcao(recarregar);
  const [nome, setNome] = useState('');
  return (
    <>
      <section className="relative mb-6 overflow-hidden rounded-lg">
        <img src="/img/hotel-dia.jpg" alt="Fachada do hotel durante o dia" className="h-40 w-full object-cover" />
        <h1 className="absolute bottom-4 left-5 text-2xl font-extrabold text-white drop-shadow">Hotéis</h1>
      </section>
      {admin ? (
        <form onSubmit={async (e) => { e.preventDefault(); if (await rodar(() => api('/hotels', { method: 'POST', body: { name: nome } }))) setNome(''); }} className="mb-4 flex gap-3">
          <input required aria-label="Nome do hotel" placeholder="Nome do hotel" value={nome} onChange={(e) => setNome(e.target.value)} className={`${campo} min-w-0 flex-1`} />
          <Botao disabled={ocupado}>Adicionar hotel</Botao>
        </form>
      ) : <p className="mb-4 text-sm text-tinta/70">Só administradores cadastram ou removem hotéis.</p>}
      <Erro m={erro} />
      <Tabela cols={admin ? ['Hotel', ''] : ['Hotel']} vazio="Nenhum hotel cadastrado."
        linhas={dados.hotels.map((h) => admin ? [h.name, <Botao key="x" cor="claro" disabled={ocupado} onClick={() => confirm(`Excluir ${h.name}?`) && rodar(() => api(`/hotels/${h.id}`, { method: 'DELETE' }))}>Excluir</Botao>] : [h.name])} />
    </>
  );
}

/* ---------- telas só do administrador ---------- */
export function Usuarios() {
  const [lista, setLista] = useState([]);
  const [f, setF] = useState({ username: '', password: '', role: 'RECEPCIONISTA' });
  const carregar = async () => setLista(await api('/users'));
  const { erro, ocupado, rodar } = useAcao(carregar);
  useEffect(() => { rodar(async () => {}); }, []);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  return (
    <>
      <Titulo t="Usuários" sub="Crie um usuário RECEPCIONISTA só para o totem, em vez de usar o admin." />
      <form onSubmit={async (e) => { e.preventDefault(); if (await rodar(() => api('/users', { method: 'POST', body: f }))) setF({ username: '', password: '', role: 'RECEPCIONISTA' }); }} className="mb-6 grid gap-4 rounded border border-tinta/15 bg-white p-5 md:grid-cols-4">
        <Rotulo t="Usuário (3 a 60 caracteres)"><input required minLength={3} value={f.username} onChange={set('username')} className={campo} /></Rotulo>
        <Rotulo t="Senha (mínimo 8)"><input required type="password" minLength={8} value={f.password} onChange={set('password')} className={campo} /></Rotulo>
        <Rotulo t="Perfil"><select value={f.role} onChange={set('role')} className={campo}><option value="RECEPCIONISTA">Recepcionista</option><option value="ADMIN">Administrador</option></select></Rotulo>
        <div className="self-end"><Botao disabled={ocupado}>Criar usuário</Botao></div>
        <div className="md:col-span-4"><Erro m={erro} /></div>
      </form>
      <Tabela cols={['Usuário', 'Perfil', 'Situação', 'Criado em', '']} vazio="Nenhum usuário."
        linhas={lista.map((u) => [u.username, u.role === 'ADMIN' ? 'Administrador' : 'Recepcionista', u.active ? 'Ativo' : 'Desativado', new Date(u.createdAt).toLocaleString('pt-BR'),
          u.active && <Botao key="x" cor="claro" disabled={ocupado} onClick={() => confirm(`Desativar ${u.username}?`) && rodar(() => api(`/users/${u.id}`, { method: 'DELETE' }))}>Desativar</Botao>])} />
    </>
  );
}

export function Auditoria() {
  const [lista, setLista] = useState([]);
  const [aviso, setAviso] = useState('');
  const carregar = async () => setLista(await api('/audit?limit=100'));
  const { erro, ocupado, rodar } = useAcao(carregar);
  useEffect(() => { rodar(async () => {}); }, []);
  return (
    <>
      <Titulo t="Auditoria e privacidade" sub="Quem leu ou alterou dados pessoais, e quando (últimos 100 registros)." />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Botao cor="claro" disabled={ocupado} onClick={() => rodar(async () => {})}>Atualizar</Botao>
        <Botao cor="claro" disabled={ocupado} onClick={async () => { if (confirm('Anonimizar os hóspedes com prazo de retenção vencido?')) { const r = await rodar(async () => { const x = await api('/privacy/retention/run', { method: 'POST' }); setAviso(`${x.anonymizedGuests} hóspede(s) anonimizado(s). Prazo de retenção: ${x.retentionDays} dias.`); }); if (!r) setAviso(''); } }}>Rodar retenção de dados</Botao>
        {aviso && <span className="text-sm font-medium text-emerald-900">{aviso}</span>}
      </div>
      <Erro m={erro} />
      <Tabela cols={['Quando', 'Usuário', 'Ação', 'Recurso', 'Identificador']} vazio="Nenhum registro."
        linhas={lista.map((a) => [new Date(a.occurredAt).toLocaleString('pt-BR'), a.username, a.action, a.resourceType, String(a.resourceId ?? '').slice(0, 8)])} />
    </>
  );
}
