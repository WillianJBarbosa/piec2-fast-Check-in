import { useState } from 'react';
import { api } from './api';

export function Login({ onLogin }) {
  const [f, setF] = useState({ username: '', password: '' });
  const [erro, setErro] = useState('');
  const [carregando, setCarregando] = useState(false);

  async function entrar(e) {
    e.preventDefault();
    setCarregando(true); setErro('');
    try { onLogin(await api('/auth/login', { method: 'POST', body: f })); }
    catch (err) { setErro(err.status === 401 ? 'Usuário ou senha incorretos.' : err.message); }
    finally { setCarregando(false); }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.2fr_1fr]">
      <div className="relative hidden lg:block">
        <img src="/img/saguao-totem.jpg" alt="Hóspede usando o totem de check-in no saguão do hotel" className="absolute inset-0 h-full w-full object-cover object-[65%_50%]" />
      </div>
      <form onSubmit={entrar} className="flex flex-col justify-center gap-5 px-8 py-12 sm:px-16">
        <h1 className="text-3xl font-extrabold text-noite">Fast Check-in</h1>
        <p className="text-sm text-tinta/70">Entre com o usuário da recepção ou da administração.</p>
        {erro && <p role="alert" className="rounded bg-red-50 px-3 py-2 text-sm text-red-800">{erro}</p>}
        <label className="grid gap-1 text-sm font-medium">Usuário
          <input required autoFocus value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })}
            className="rounded border border-tinta/20 bg-white px-3 py-2 focus:outline-2 focus:outline-latao" />
        </label>
        <label className="grid gap-1 text-sm font-medium">Senha
          <input required type="password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })}
            className="rounded border border-tinta/20 bg-white px-3 py-2 focus:outline-2 focus:outline-latao" />
        </label>
        <button disabled={carregando} className="rounded bg-noite px-4 py-2.5 font-bold text-white hover:bg-noite/90 disabled:opacity-60">
          {carregando ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </div>
  );
}
