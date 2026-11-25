import { useCallback, useEffect, useState } from 'react';
import { api, getAuth, setAuth, lista } from './api';
import { Login } from './Login';
import { Inicio, Hospedes, Estadias, Fichas, Hoteis, Usuarios, Auditoria } from './views';

const MENU = [['inicio', 'Início'], ['hospedes', 'Hóspedes'], ['estadias', 'Estadias'], ['fichas', 'Fichas FNRH'], ['hoteis', 'Hotéis']];
const ADMIN_MENU = [['usuarios', 'Usuários'], ['auditoria', 'Auditoria']];
const TELAS = { inicio: Inicio, hospedes: Hospedes, estadias: Estadias, fichas: Fichas, hoteis: Hoteis, usuarios: Usuarios, auditoria: Auditoria };

export default function App() {
  const [auth, setA] = useState(getAuth());
  const [tela, setTela] = useState('inicio');
  const [dados, setDados] = useState({ guests: [], stays: [], fnrh: [], hotels: [] });
  const [erro, setErro] = useState('');

  const carregar = useCallback(async () => {
    // Cada lista é carregada separada: se uma falhar, as outras continuam aparecendo.
    const rotas = [['guests', '/guests'], ['stays', '/stays'], ['fnrh', '/fnrh-registrations'], ['hotels', '/hotels']];
    const res = await Promise.allSettled(rotas.map(([, p]) => api(p).then(lista)));
    setDados((d) => {
      const novo = { ...d };
      res.forEach((r, i) => { if (r.status === 'fulfilled') novo[rotas[i][0]] = r.value; });
      return novo;
    });
    const falhas = res.map((r, i) => (r.status === 'rejected' ? `${rotas[i][1]}: ${r.reason.message}` : null)).filter(Boolean);
    setErro(falhas.join(' | '));
  }, []);
  useEffect(() => { if (auth) carregar(); }, [auth, carregar]);
  // Atualiza sozinho a cada 10 s, para as fichas novas do totem aparecerem.
  useEffect(() => {
    if (!auth) return;
    const t = setInterval(() => { if (!document.hidden) carregar(); }, 10000); // pausa com a aba escondida
    return () => clearInterval(t);
  }, [auth, carregar]);

  if (!auth) return <Login onLogin={(a) => { setAuth(a); setA(a); }} />;
  const admin = String(auth.role).includes('ADMIN');
  const Tela = TELAS[tela];
  const menu = admin ? [...MENU, ...ADMIN_MENU] : MENU;
  const aRevisar = dados.fnrh.filter((f) => f.status === 'PENDING' && f.reviewRequired).length;

  return (
    <div className="min-h-screen md:grid md:grid-cols-[15rem_1fr]">
      <aside className="flex flex-col gap-6 bg-noite p-5 text-white md:min-h-screen">
        <p className="text-xl font-extrabold">Fast Check-in</p>
        <nav className="flex gap-1 overflow-x-auto md:flex-col" aria-label="Principal">
          {menu.map(([id, nome]) => (
            <button key={id} onClick={() => setTela(id)} aria-current={tela === id ? 'page' : undefined}
              className={`whitespace-nowrap rounded px-3 py-2 text-left text-sm font-medium ${tela === id ? 'bg-latao text-noite' : 'hover:bg-white/10'}`}>
              {nome}{id === 'fichas' && aRevisar > 0 && <span className="ml-2 rounded-full bg-red-600 px-2 py-0.5 text-xs font-bold text-white">{aRevisar}</span>}
            </button>
          ))}
        </nav>
        <div className="mt-auto hidden text-sm md:block">
          <p className="text-white/70">Perfil: {admin ? 'Administrador' : 'Recepcionista'}</p>
          <button onClick={() => { setAuth(null); setA(null); }} className="mt-2 underline">Sair</button>
        </div>
      </aside>
      <main className="min-w-0 p-5 md:p-10">
        {erro && <p role="alert" className="mb-4 rounded bg-red-50 px-3 py-2 text-sm text-red-800">{erro}</p>}
        <Tela dados={dados} recarregar={carregar} admin={admin} ir={setTela} />
        <button onClick={() => { setAuth(null); setA(null); }} className="mt-8 text-sm underline md:hidden">Sair</button>
      </main>
    </div>
  );
}
