const KEY = 'fc_auth';
export const getAuth = () => JSON.parse(sessionStorage.getItem(KEY) || 'null');
export const setAuth = (a) => (a ? sessionStorage.setItem(KEY, JSON.stringify(a)) : sessionStorage.removeItem(KEY));
export const lista = (x) => (Array.isArray(x) ? x : x?.content ?? []);
export const data = (d) => (d ? d.split('-').reverse().join('/') : '');

export async function api(path, { method = 'GET', body } = {}) {
  const auth = getAuth();
  const r = await fetch('/api/v1' + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: `Bearer ${auth.accessToken}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (r.status === 401 && path !== '/auth/login') { setAuth(null); location.reload(); }
  const texto = await r.text();
  let corpo = null;
  try { corpo = texto ? JSON.parse(texto) : null; } catch { /* resposta sem JSON */ }
  if (!r.ok) {
    const msg = corpo?.message || corpo?.detail || corpo?.error
      || (r.status === 403 ? 'Seu perfil não tem permissão para esta ação.' : `Erro ${r.status}`);
    throw Object.assign(new Error(msg), { status: r.status });
  }
  return corpo;
}
