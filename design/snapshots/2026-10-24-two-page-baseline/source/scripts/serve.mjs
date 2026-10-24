import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { dirname, extname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'design');
const port = Number(process.env.AETHER_PREVIEW_PORT || 4176);
const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png' };
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://127.0.0.1');
    const requested = decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname);
    const scoped = /^\/(modern|immersive|shared)\//.test(requested) ? requested : '/immersive' + requested;
    const path = resolve(root, '.' + scoped);
    if (!path.startsWith(root + sep)) {
      response.writeHead(403).end();
      return;
    }
    if (!(await stat(path)).isFile()) throw new Error('Not a file');
    const bytes = await readFile(path);
    response.writeHead(200, { 'Content-Type': types[extname(path)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
    response.end(bytes);
  } catch {
    response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' }).end('Not found');
  }
});
server.listen(port, '127.0.0.1', () => console.log(`Aether local design preview: http://127.0.0.1:${port}/preview.html`));
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => process.exit(0)));
