import { useEffect, useState } from 'react';

export async function adminRequest(path, { body, signal } = {}) {
  const csrf = document.getElementById('admin-root').dataset.csrf;
  const multipart = body instanceof FormData;
  const response = await fetch(path, {
    method: body ? 'POST' : 'GET',
    credentials: 'same-origin',
    headers: body ? { 'X-BYgram-CSRF': csrf, ...(multipart ? {} : { 'Content-Type': 'application/json' }) } : {},
    body: body && !multipart ? JSON.stringify(body) : body,
    signal,
  });
  if (response.status === 401) {
    location.reload();
    throw new Error('Войдите через Telegram');
  }
  const result = await response.json().catch(() => null);
  if (!response.ok || !result?.ok) throw new Error(result?.error || 'Не удалось выполнить запрос');
  return result.data;
}

export function useAdminResource(path, revision = 0) {
  const key = path + '/' + revision;
  const [result, setResult] = useState({ key: '', data: null, error: '' });
  useEffect(() => {
    const controller = new AbortController();
    adminRequest(path, { signal: controller.signal })
      .then(data => setResult({ key, data, error: '' }))
      .catch(error => {
        if (!controller.signal.aborted) setResult({ key, data: null, error: error.message });
      });
    return () => controller.abort();
  }, [key, path]);
  return result.key === key ? result : { data: null, error: '' };
}
