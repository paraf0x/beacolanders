import { describe, it, beforeAll, afterAll, afterEach } from 'vitest';
import { acquireServer, releaseServer, getServer } from './helpers.js';
import {
  tc, check, checkTruthy, checkDefined, checkContains,
  checkGte, printProtocol
} from './protocol.js';
import { delay, offlineUuid, SqliteClient } from 'mc-e2e';
import type { McBot, WindowSnapshot, RconClient } from 'mc-e2e';

let bot: McBot;
let rcon: RconClient;
let botUuid: string;

const TEST_SERVER_DIR = process.env.TEST_SERVER_DIR ?? '/Users/maksymvasyukov/purpur-test';

// ─── Raw Window Helpers (for post-click / post-async reads) ──

/** Read item name from raw mineflayer window (e.g. 'minecraft:clock') */
function rawItem(index: number): string | null {
  const slot = (bot.raw as any).currentWindow?.slots?.[index];
  return slot?.name ? `minecraft:${slot.name}` : null;
}

/** Click slot in-place (no new window expected — for scroll arrows) */
async function clickRaw(index: number): Promise<void> {
  await (bot.raw as any).clickWindow(index, 0, 0);
  await delay(400);
}

/** Open /bl and navigate to detail view */
async function openDetail(): Promise<WindowSnapshot> {
  bot.chat('/bl');
  const list = await bot.nextWindow(10_000);
  const heads = list.findSlots(s => s.item === 'minecraft:player_head');
  return list.clickSlot(heads[0].index);
}

// Slot index helpers (0-based): row R (1-indexed), col C (1-indexed) = (R-1)*9 + (C-1)
const STATS_ROW_BASE = 4 * 9;       // row 5 = index 36
const LOCATIONS_ROW_BASE = 2 * 9;   // row 3 = index 18
const SHOPS_ROW_BASE = 3 * 9;       // row 4 = index 27

// ─── DB Helpers ──────────────────────────────────────────────

function cleanDb(): void {
  try {
    const bm = new SqliteClient(`${TEST_SERVER_DIR}/plugins/BaseManager/storage.db`, { readonly: false });
    bm.run("DELETE FROM locations WHERE owner = ?", botUuid);
    bm.close();
  } catch { /* ok */ }
  try {
    const ss = new SqliteClient(`${TEST_SERVER_DIR}/plugins/ShopSearch/storage.db`, { readonly: false });
    ss.run("DELETE FROM shops WHERE owner = ?", botUuid);
    ss.run("DELETE FROM shops WHERE id = ?", TEST_SHOP_UUID);
    // Also clean up old bad test data from previous runs
    ss.run("DELETE FROM shops WHERE id LIKE 'test-%'");
    ss.close();
  } catch { /* ok */ }
}

function seedLocations(): void {
  const db = new SqliteClient(`${TEST_SERVER_DIR}/plugins/BaseManager/storage.db`, { readonly: false });
  try {
    // Location 1: with STICK icon
    db.run("INSERT INTO locations (owner, tag, name, created, isPublic, icon) VALUES (?, 'BASE', 'Test Base', '2025-01-01', 1, 'STICK')", botUuid);
    const id1 = db.get<{ id: number }>("SELECT last_insert_rowid() as id")!.id;
    db.run("INSERT INTO location_coords (locationId, world, locX, locY, locZ) VALUES (?, 'world', 100, 64, 200)", id1);
    db.run("INSERT INTO location_coords (locationId, world, locX, locY, locZ) VALUES (?, 'world_nether', 12, 128, 25)", id1);
    db.run("INSERT INTO location_members (locationId, memberUUID, memberName) VALUES (?, 'fake-uuid', 'SomeMember')", id1);

    // Location 2: no icon (null) → should fallback to lodestone
    db.run("INSERT INTO locations (owner, tag, name, created, isPublic) VALUES (?, 'FARM', 'Iron Farm', '2025-02-01', 0)", botUuid);
    const id2 = db.get<{ id: number }>("SELECT last_insert_rowid() as id")!.id;
    db.run("INSERT INTO location_coords (locationId, world, locX, locY, locZ) VALUES (?, 'world', 300, 50, 400)", id2);
  } finally { db.close(); }
}

// Fixed UUID for test shop (must be valid UUID for ShopSearch)
const TEST_SHOP_UUID = '00000000-0000-0000-0000-000000e2e001';

function seedShops(): void {
  const db = new SqliteClient(`${TEST_SERVER_DIR}/plugins/ShopSearch/storage.db`, { readonly: false });
  try {
    db.run("DELETE FROM stock WHERE shopId = ?", TEST_SHOP_UUID);
    db.run("DELETE FROM shops WHERE id = ?", TEST_SHOP_UUID);
    db.run("INSERT INTO shops (id, owner, name, locX, locY, locZ, world) VALUES (?, ?, 'Diamond Shop', 50, 65, 100, 'world')", TEST_SHOP_UUID, botUuid);
    db.run("INSERT INTO stock (shopId, lastRestock, material, price, amount) VALUES (?, '2025-01-01', 'DIAMOND', 10, 64)", TEST_SHOP_UUID);
    db.run("INSERT INTO stock (shopId, lastRestock, material, price, amount) VALUES (?, '2025-01-01', 'EMERALD', 5, 32)", TEST_SHOP_UUID);
  } finally { db.close(); }
}

// ─── Tests ───────────────────────────────────────────────────

describe('Beacolanders Player GUI', () => {
  beforeAll(async () => {
    await acquireServer();
    rcon = await getServer().rcon();
    bot = await getServer().createBot('GuiBot');
    botUuid = offlineUuid('GuiBot');
    cleanDb();

    await rcon.send('item replace entity GuiBot armor.head with minecraft:diamond_helmet');
    await rcon.send('item replace entity GuiBot armor.chest with minecraft:iron_chestplate');
    await rcon.send('item replace entity GuiBot armor.legs with minecraft:golden_leggings');
    await rcon.send('item replace entity GuiBot armor.feet with minecraft:leather_boots');
    await rcon.send('item replace entity GuiBot weapon.mainhand with minecraft:netherite_sword');
    await rcon.send('item replace entity GuiBot weapon.offhand with minecraft:shield');
    await delay(500);
  });

  afterEach(async () => {
    try { await bot.closeWindow(); } catch { /* ok */ }
    await delay(200);
  });

  afterAll(async () => {
    console.log(printProtocol());
    cleanDb();
    try { await bot?.disconnect(); } catch { /* ok */ }
    await releaseServer();
  });

  // ─── Player List ─────────────────────────────────────────

  it('TC-01: /bl opens 6-row GUI with nav bar and pagination', async () => {
    tc('TC-01');
    bot.chat('/bl');
    const win = await bot.nextWindow(10_000);

    checkContains('Title', win.title, 'Beacolanders');
    check('6 rows', 6, win.rows);
    check('Search is compass', 'minecraft:compass', win.slot(1, 1).item);
    check('Title is nether_star', 'minecraft:nether_star', win.slot(5, 1).item);
    check('Page indicator', 'minecraft:paper', win.slot(5, 6).item);
  });

  it('TC-02: /beacolanders alias works', async () => {
    tc('TC-02');
    bot.chat('/beacolanders');
    const win = await bot.nextWindow(10_000);
    checkContains('Title', win.title, 'Beacolanders');
  });

  it('TC-03: bot appears as player head', async () => {
    tc('TC-03');
    bot.chat('/bl');
    const win = await bot.nextWindow(10_000);

    const heads = win.findSlots(s => s.item === 'minecraft:player_head');
    checkGte('At least 1 head', heads.length, 1);
    const ours = heads.find(s => s.name?.includes('GuiBot'));
    checkDefined('GuiBot found', ours);
    checkGte('Head in player area', ours!.index, 9);
  });

  it('TC-04: clicking empty slot is safe', async () => {
    tc('TC-04');
    bot.chat('/bl');
    await bot.nextWindow(10_000);
    await clickRaw(8);
    checkTruthy('No crash', true);
  });

  // ─── Detail: Header ──────────────────────────────────────

  it('TC-10: detail header has back button and player head', async () => {
    tc('TC-10');
    const d = await openDetail();

    checkContains('Title has name', d.title, 'GuiBot');
    check('Back button', 'minecraft:barrier', d.slot(1, 1).item);
    check('Player head', 'minecraft:player_head', d.slot(5, 1).item);
  });

  // ─── Detail: Equipment ───────────────────────────────────

  it('TC-20: all 6 equipment slots correct', async () => {
    tc('TC-20');
    const d = await openDetail();

    check('Helmet', 'minecraft:diamond_helmet', d.slot(2, 2).item);
    check('Chestplate', 'minecraft:iron_chestplate', d.slot(3, 2).item);
    check('Leggings', 'minecraft:golden_leggings', d.slot(4, 2).item);
    check('Boots', 'minecraft:leather_boots', d.slot(5, 2).item);
    check('Main hand', 'minecraft:netherite_sword', d.slot(7, 2).item);
    check('Off hand', 'minecraft:shield', d.slot(8, 2).item);
  });

  it('TC-21: empty gear shows grey pane placeholders', async () => {
    tc('TC-21');
    await rcon.send('clear GuiBot');
    await delay(500);

    const d = await openDetail();
    const pane = 'minecraft:gray_stained_glass_pane';
    check('Empty helmet', pane, d.slot(2, 2).item);
    check('Empty chestplate', pane, d.slot(3, 2).item);
    check('Empty leggings', pane, d.slot(4, 2).item);
    check('Empty boots', pane, d.slot(5, 2).item);

    // Re-equip
    await rcon.send('item replace entity GuiBot armor.head with minecraft:diamond_helmet');
    await rcon.send('item replace entity GuiBot armor.chest with minecraft:iron_chestplate');
    await rcon.send('item replace entity GuiBot armor.legs with minecraft:golden_leggings');
    await rcon.send('item replace entity GuiBot armor.feet with minecraft:leather_boots');
    await rcon.send('item replace entity GuiBot weapon.mainhand with minecraft:netherite_sword');
    await rcon.send('item replace entity GuiBot weapon.offhand with minecraft:shield');
    await delay(300);
  });

  // ─── Detail: Stats ───────────────────────────────────────

  it('TC-30: stats row has correct materials and scroll arrows', async () => {
    tc('TC-30');
    const d = await openDetail();

    // Row 5: left grey, 7 stats, right arrow
    check('Left boundary grey', 'minecraft:gray_stained_glass_pane', d.slot(1, 5).item);
    check('Stat 1: clock', 'minecraft:clock', d.slot(2, 5).item);
    check('Stat 2: skull', 'minecraft:skeleton_skull', d.slot(3, 5).item);
    check('Stat 3: diamond_sword', 'minecraft:diamond_sword', d.slot(4, 5).item);
    check('Stat 4: leather_boots', 'minecraft:leather_boots', d.slot(5, 5).item);
    check('Stat 5: elytra', 'minecraft:elytra', d.slot(6, 5).item);
    check('Stat 6: diamond_pickaxe', 'minecraft:diamond_pickaxe', d.slot(7, 5).item);
    check('Stat 7: crafting_table', 'minecraft:crafting_table', d.slot(8, 5).item);
    check('Right arrow', 'minecraft:arrow', d.slot(9, 5).item);
    checkContains('Arrow name has +', d.slot(9, 5).name!, '+');
  });

  it('TC-31: scroll right shifts items, scroll to end, scroll back', async () => {
    tc('TC-31');
    await openDetail();

    // Initial: slot(2,5) = index 37 = clock
    check('Start: slot 37 is clock', 'minecraft:clock', rawItem(STATS_ROW_BASE + 1));

    // Scroll right: arrow at slot(9,5) = index 44
    await clickRaw(STATS_ROW_BASE + 8);
    check('After 1x right: slot 37 is skull', 'minecraft:skeleton_skull', rawItem(STATS_ROW_BASE + 1));
    check('Left arrow active', 'minecraft:arrow', rawItem(STATS_ROW_BASE));

    // Scroll to end (2 more)
    await clickRaw(STATS_ROW_BASE + 8);
    await clickRaw(STATS_ROW_BASE + 8);
    check('At end: right grey', 'minecraft:gray_stained_glass_pane', rawItem(STATS_ROW_BASE + 8));
    check('At end: last stat is rabbit_foot', 'minecraft:rabbit_foot', rawItem(STATS_ROW_BASE + 7));

    // Can't scroll past end
    await clickRaw(STATS_ROW_BASE + 8);
    check('Still at end', 'minecraft:gray_stained_glass_pane', rawItem(STATS_ROW_BASE + 8));

    // Scroll back to start
    await clickRaw(STATS_ROW_BASE);
    await clickRaw(STATS_ROW_BASE);
    await clickRaw(STATS_ROW_BASE);
    check('Back: left grey', 'minecraft:gray_stained_glass_pane', rawItem(STATS_ROW_BASE));
    check('Back: slot 37 is clock', 'minecraft:clock', rawItem(STATS_ROW_BASE + 1));

    // Can't scroll past start
    await clickRaw(STATS_ROW_BASE);
    check('Still at start', 'minecraft:gray_stained_glass_pane', rawItem(STATS_ROW_BASE));
  });

  // ─── Detail: Empty DB Rows ───────────────────────────────

  it('TC-40: no locations = None placeholder', async () => {
    tc('TC-40');
    await openDetail();
    await delay(2000);

    check('Loc placeholder is pane', 'minecraft:gray_stained_glass_pane', rawItem(LOCATIONS_ROW_BASE + 1));
  });

  it('TC-41: no shops = None placeholder', async () => {
    tc('TC-41');
    await openDetail();
    await delay(2000);

    check('Shop placeholder is pane', 'minecraft:gray_stained_glass_pane', rawItem(SHOPS_ROW_BASE + 1));
  });

  // ─── Detail: With DB Data ────────────────────────────────

  it('TC-50: seeded locations show with correct icons', async () => {
    tc('TC-50');
    seedLocations();
    seedShops();
    // Reload plugins so their in-memory caches pick up the new DB data
    await rcon.send('loc reload');
    await rcon.send('sh reload');
    await delay(1000);

    await openDetail();
    await delay(2000);

    check('Location 1 uses STICK icon', 'minecraft:stick', rawItem(LOCATIONS_ROW_BASE + 1));
    check('Location 2 fallback to lodestone', 'minecraft:lodestone', rawItem(LOCATIONS_ROW_BASE + 2));
    const third = rawItem(LOCATIONS_ROW_BASE + 3);
    checkTruthy('No 3rd location', third === null || third === 'minecraft:air');
  });

  it('TC-51: seeded shops show as chest items', async () => {
    tc('TC-51');
    // Data already seeded and plugins reloaded in TC-50
    await openDetail();
    await delay(2000);

    check('Shop is chest', 'minecraft:chest', rawItem(SHOPS_ROW_BASE + 1));
  });

  // ─── Cross-Plugin Navigation ──────────────────────────────

  it('TC-52: /loc detail opens BaseManager GUI for real location', async () => {
    tc('TC-52');

    // Use a real BaseManager location that's already in the plugin's cache
    const bmDb = new SqliteClient(`${TEST_SERVER_DIR}/plugins/BaseManager/storage.db`, { readonly: true });
    const realLoc = bmDb.get<{ id: number, name: string }>("SELECT id, name FROM locations LIMIT 1");
    bmDb.close();
    checkDefined('Real location exists in DB', realLoc);

    bot.chat(`/loc detail ${realLoc!.id}`);
    const bmWin = await bot.nextWindow(10_000);

    checkDefined('BaseManager GUI opened', bmWin);
    checkGte('BaseManager GUI has items', bmWin.filledSlots().length, 1);
    checkTruthy('Not Beacolanders GUI', !bmWin.title.includes('Beacolanders'));

    await bot.closeWindow();
  });

  // ─── Navigation ──────────────────────────────────────────

  it('TC-60: back button returns to player list', async () => {
    tc('TC-60');
    const d = await openDetail();
    const back = await d.click(1, 1);

    checkContains('Back to list', back.title, 'Beacolanders');
    checkGte('Heads visible', back.findSlots(s => s.item === 'minecraft:player_head').length, 1);
  });

  it('TC-61: non-functional slot clicks are safe', async () => {
    tc('TC-61');
    await openDetail();

    await clickRaw(1);   // empty row 1
    await clickRaw(14);  // equipment gap
    await clickRaw(48);  // empty row 6
    check('Back button still there', 'minecraft:barrier', rawItem(0));
  });
});
