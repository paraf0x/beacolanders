import { McServer, SqliteClient } from 'mc-e2e';
import { execSync } from 'node:child_process';
import { unlinkSync, existsSync } from 'node:fs';

const TEST_SERVER_DIR = process.env.TEST_SERVER_DIR ?? '/Users/maksymvasyukov/purpur-test';

let server: McServer | null = null;
let refCount = 0;

/**
 * Get or start the shared server instance.
 * With fileParallelism: false, all test files share the same thread,
 * so a module-level singleton works reliably.
 */
function cleanOrphanedServer(): void {
  try {
    // Kill any java process holding port 25565
    const pids = execSync('lsof -ti :25565 2>/dev/null || true', { encoding: 'utf8' }).trim();
    if (pids) {
      for (const pid of pids.split('\n')) {
        try { process.kill(parseInt(pid), 9); } catch { /* ok */ }
      }
      // Wait for process to die and release lock
      execSync('sleep 2');
    }
    // Remove stale session locks
    for (const world of ['world', 'world_nether', 'world_the_end']) {
      const lockPath = `${TEST_SERVER_DIR}/${world}/session.lock`;
      if (existsSync(lockPath)) {
        try { unlinkSync(lockPath); } catch { /* ok */ }
      }
    }
  } catch { /* cleanup is best-effort */ }
}

export async function acquireServer(): Promise<McServer> {
  refCount++;
  if (server) return server;

  cleanOrphanedServer();

  server = new McServer({
    dir: TEST_SERVER_DIR,
    jar: 'purpur.jar',
    jvmArgs: ['-Xms512M', '-Xmx2G'],
    port: 25565,
    rcon: { port: 25575, password: 'e2e-test' },
    patchProperties: true,
    acceptEula: true,
    inheritStdio: true,
  });

  console.log('[e2e] Starting Minecraft server...');
  await server.start();
  console.log('[e2e] Server is ready.');
  return server;
}

/** Release the server. When all references are released, stop it. */
export async function releaseServer(): Promise<void> {
  refCount--;
  if (refCount <= 0 && server) {
    console.log('[e2e] Stopping server...');
    await server.stop();
    server = null;
    console.log('[e2e] Server stopped.');
  }
}

/** Get the current server instance (must call acquireServer first). */
export function getServer(): McServer {
  if (!server) throw new Error('Server not started. Call acquireServer() in beforeAll.');
  return server;
}

export function getDb(): SqliteClient {
  return getServer().sqlite('plugins/Beacolanders/storage.db');
}
