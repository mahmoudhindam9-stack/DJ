import { handleApi } from './scripts/server.js';

/**
 * Single Vercel API router. vercel.json rewrites /api/<route> to /api/index
 * and passes the original route in the __path query parameter.
 */
export default async function handler(req, res) {
  try {
    const incoming = new URL(req.url || '/api', 'http://127.0.0.1');
    const forwardedPath = incoming.searchParams.get('__path');
    if (forwardedPath !== null) {
      incoming.searchParams.delete('__path');
      const cleanPath = forwardedPath.split('/').filter(Boolean).join('/');
      const segments = cleanPath.split('/');
      if (!cleanPath || segments.some((segment) => !segment || segment === '.' || segment === '..')) {
        res.statusCode = 400;
        res.setHeader('Content-Type', 'application/json; charset=utf-8');
        res.end(JSON.stringify({ ok: false, error: 'Invalid API route.' }));
        return;
      }
      req.url = '/api/' + cleanPath + (incoming.search ? incoming.search : '');
    }

    const handled = await handleApi(req, res);
    if (!handled && !res.writableEnded) {
      res.statusCode = 404;
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      res.end(JSON.stringify({ ok: false, error: 'API endpoint not found.' }));
    }
  } catch (error) {
    if (res.headersSent) {
      res.destroy(error instanceof Error ? error : undefined);
      return;
    }
    res.statusCode = 500;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify({ ok: false, error: 'The API request failed.' }));
  }
}
