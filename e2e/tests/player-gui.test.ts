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
const TEST_SHOP_NAME = 'E2eTestShop';

// ─── Raw Window Helpers ──────────────────────────────────────

function rawItem(index: number): string | null {
  const slot = (bot.raw as any).currentWindow?.slots?.[index];
  return slot?.name ? `minecraft:${slot.name}` : null;
}

async function clickRaw(index: number): Promise<void> {
  await (bot.raw as any).clickWindow(index, 0, 0);
  await delay(400);
}

async function openDetail(): Promise<WindowSnapshot> {
  bot.chat('/bl');
  const list = await bot.nextWindow(10_000);
  const heads = list.findSlots(s => s.item === 'minecraft:player_head');
  return list.clickSlot(heads[0].index);
}

const STATS_ROW_BASE = 4 * 9;
const LOCATIONS_ROW_BASE = 2 * 9;
const SHOPS_ROW_BASE = 3 * 9;

// ─── DB Helpers (locations only — shops created via commands) ─

function cleanLocations(): void {
  try {
    const bm = new SqliteClient(`${TEST_SERVER_DIR}/plugins/BaseManager/storage.db`, { readonly: false });
    bm.run("DELETE FROM locations WHERE owner = ?", botUuid);
    bm.close();
  } catch { /* ok */ }
}

function seedLocations(): void {
  const db = new SqliteClient(`${TEST_SERVER_DIR}/plugins/BaseManager/storage.db`, { readonly: false });
  try {
    db.run("INSERT INTO locations (owner, tag, name, created, isPublic, icon) VALUES (?, 'BASE', 'Test Base', '2025-01-01', 1, 'STICK')", botUuid);
    const id1 = db.get<{ id: number }>("SELECT last_insert_rowid() as id")!.id;
    db.run("INSERT INTO location_coords (locationId, world, locX, locY, locZ) VALUES (?, 'world', 100, 64, 200)", id1);
    db.run("INSERT INTO location_coords (locationId, world, locX, locY, locZ) VALUES (?, 'world_nether', 12, 128, 25)", id1);
    db.run("INSERT INTO location_members (locationId, memberUUID, memberName) VALUES (?, 'fake-uuid', 'SomeMember')", id1);

    db.run("INSERT INTO locations (owner, tag, name, created, isPublic) VALUES (?, 'FARM', 'Iron Farm', '2025-02-01', 0)", botUuid);
    const id2 = db.get<{ id: number }>("SELECT last_insert_rowid() as id")!.id;
    db.run("INSERT INTO location_coords (locationId, world, locX, locY, locZ) VALUES (?, 'world', 300, 50, 400)", id2);
  } finally { db.close(); }
}

// ─── Shop Helpers (via plugin commands — stays in cache) ─────

async function createTestShop(): Promise<void> {
  // Delete if exists from a previous run
  bot.clearMessages();
  bot.chat(`/sh delete ${TEST_SHOP_NAME}`);
  await delay(500);

  // Create shop at coords 50 65 100
  bot.chat(`/sh create ${TEST_SHOP_NAME} 50 65 100`);
  await delay(500);

  // Add stock via restock command
  bot.chat(`/sh restock ${TEST_SHOP_NAME} new diamond 64 150`);
  await delay(300);
  bot.chat(`/sh restock ${TEST_SHOP_NAME} new emerald 32 50`);
  await delay(300);
}

async function deleteTestShop(): Promise<void> {
  try {
    bot.chat(`/sh delete ${TEST_SHOP_NAME}`);
    await delay(500);
  } catch { /* ok */ }
}

// ─── Tests ───────────────────────────────────────────────────

describe('Beacolanders Player GUI', () => {
  beforeAll(async () => {
    await acquireServer();
    rcon = await getServer().rcon();
    bot = await getServer().createBot('GuiBot');
    botUuid = offlineUuid('GuiBot');
    cleanLocations();

    // Equip bot
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
    cleanLocations();
    await deleteTestShop();
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

    check('Start: first stat is clock', 'minecraft:clock', rawItem(STATS_ROW_BASE + 1));

    await clickRaw(STATS_ROW_BASE + 8);
    check('After 1x right: first stat is skull', 'minecraft:skeleton_skull', rawItem(STATS_ROW_BASE + 1));
    check('Left arrow active', 'minecraft:arrow', rawItem(STATS_ROW_BASE));

    await clickRaw(STATS_ROW_BASE + 8);
    await clickRaw(STATS_ROW_BASE + 8);
    check('At end: right grey', 'minecraft:gray_stained_glass_pane', rawItem(STATS_ROW_BASE + 8));
    check('At end: last stat is rabbit_foot', 'minecraft:rabbit_foot', rawItem(STATS_ROW_BASE + 7));

    await clickRaw(STATS_ROW_BASE + 8);
    check('Past end: still grey', 'minecraft:gray_stained_glass_pane', rawItem(STATS_ROW_BASE + 8));

    await clickRaw(STATS_ROW_BASE);
    await clickRaw(STATS_ROW_BASE);
    await clickRaw(STATS_ROW_BASE);
    check('Back: left grey', 'minecraft:gray_stained_glass_pane', rawItem(STATS_ROW_BASE));
    check('Back: first stat is clock', 'minecraft:clock', rawItem(STATS_ROW_BASE + 1));

    await clickRaw(STATS_ROW_BASE);
    check('Past start: still grey', 'minecraft:gray_stained_glass_pane', rawItem(STATS_ROW_BASE));
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
    await delay(500);

    await openDetail();
    await delay(2000);

    check('Location 1 uses STICK icon', 'minecraft:stick', rawItem(LOCATIONS_ROW_BASE + 1));
    check('Location 2 fallback to lodestone', 'minecraft:lodestone', rawItem(LOCATIONS_ROW_BASE + 2));
    const third = rawItem(LOCATIONS_ROW_BASE + 3);
    checkTruthy('No 3rd location', third === null || third === 'minecraft:air');
  });

  it('TC-51: shop created via command shows as chest in detail', async () => {
    tc('TC-51');

    // Create shop via ShopSearch commands (data goes into plugin cache)
    await createTestShop();

    await openDetail();
    await delay(2000);

    check('Shop is chest', 'minecraft:chest', rawItem(SHOPS_ROW_BASE + 1));
  });

  // ─── Cross-Plugin Navigation ──────────────────────────────

  it('TC-52: /loc detail opens BaseManager detail GUI', async () => {
    tc('TC-52');

    // Use a real BaseManager location already in cache
    const bmDb = new SqliteClient(`${TEST_SERVER_DIR}/plugins/BaseManager/storage.db`, { readonly: true });
    const realLoc = bmDb.get<{ id: number }>("SELECT id FROM locations LIMIT 1");
    bmDb.close();
    checkDefined('Real location exists', realLoc);

    bot.chat(`/loc detail ${realLoc!.id}`);
    const bmWin = await bot.nextWindow(10_000);

    checkDefined('BaseManager GUI opened', bmWin);
    checkGte('BaseManager GUI has items', bmWin.filledSlots().length, 1);
    checkTruthy('Not Beacolanders GUI', !bmWin.title.includes('Beacolanders'));
  });

  it('TC-53: /sh detail opens ShopSearch inventory GUI', async () => {
    tc('TC-53');

    // Use the shop we created via commands in TC-51
    bot.chat(`/sh detail ${TEST_SHOP_NAME}`);
    const ssWin = await bot.nextWindow(10_000);

    checkDefined('ShopSearch GUI opened', ssWin);
    checkGte('ShopSearch GUI has items', ssWin.filledSlots().length, 1);
    checkTruthy('Not Beacolanders GUI', !ssWin.title.includes('Beacolanders'));
  });

  it('TC-54: clicking shop in Beacolanders opens ShopSearch GUI', async () => {
    tc('TC-54');

    await openDetail();
    await delay(2000);

    check('Shop exists', 'minecraft:chest', rawItem(SHOPS_ROW_BASE + 1));

    // Click — triggers performCommand("sh detail E2eTestShop --back beacolanders")
    const winPromise = bot.nextWindow(10_000);
    await (bot.raw as any).clickWindow(SHOPS_ROW_BASE + 1, 0, 0);
    const ssWin = await winPromise;

    checkDefined('ShopSearch GUI opened via click', ssWin);
    checkTruthy('Not Beacolanders title', !ssWin.title.includes('Beacolanders'));
  });

  it('TC-55: back button in ShopSearch returns to Beacolanders', async () => {
    tc('TC-55');

    await openDetail();
    await delay(2000);

    // Click shop → opens ShopSearch with --back beacolanders
    let winPromise = bot.nextWindow(10_000);
    await (bot.raw as any).clickWindow(SHOPS_ROW_BASE + 1, 0, 0);
    const ssWin = await winPromise;
    checkTruthy('In ShopSearch GUI', !ssWin.title.includes('Beacolanders'));

    // ShopSearch back button: ARROW at slot (1, last row) = index 45
    const backIdx = (ssWin.rows - 1) * 9;
    check('Back button is arrow', 'minecraft:arrow', ssWin.slotAt(backIdx).item);

    // Click back → performCommand("beacolanders") → opens Beacolanders
    winPromise = bot.nextWindow(10_000);
    await (bot.raw as any).clickWindow(backIdx, 0, 0);
    const blWin = await winPromise;

    checkContains('Back to Beacolanders', blWin.title, 'Beacolanders');
    checkGte('Player heads visible', blWin.findSlots(s => s.item === 'minecraft:player_head').length, 1);
  });

  it('TC-56: back button in BaseManager returns to Beacolanders', async () => {
    tc('TC-56');

    const bmDb = new SqliteClient(`${TEST_SERVER_DIR}/plugins/BaseManager/storage.db`, { readonly: true });
    const realLoc = bmDb.get<{ id: number }>("SELECT id FROM locations LIMIT 1");
    bmDb.close();
    checkDefined('Real location exists', realLoc);

    // Open BaseManager detail with --back beacolanders
    bot.chat(`/loc detail ${realLoc!.id} --back beacolanders`);
    const bmWin = await bot.nextWindow(10_000);
    checkTruthy('In BaseManager GUI', !bmWin.title.includes('Beacolanders'));

    // BaseManager back button: ARROW at (1,6) = index 45
    const backIdx = 5 * 9;
    check('Back button is arrow', 'minecraft:arrow', bmWin.slotAt(backIdx).item);

    // Click back → returns to Beacolanders
    const winPromise = bot.nextWindow(10_000);
    await (bot.raw as any).clickWindow(backIdx, 0, 0);
    const blWin = await winPromise;

    checkContains('Back to Beacolanders', blWin.title, 'Beacolanders');
    checkGte('Player heads visible', blWin.findSlots(s => s.item === 'minecraft:player_head').length, 1);
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

  // ─── Leaderboards ────────────────────────────────────────────

  it('TC-70: /bl top opens leaderboard selector GUI', async () => {
    tc('TC-70');
    bot.chat('/bl top');
    const win = await bot.nextWindow(10_000);

    checkContains('Title contains Leaderboards', win.title, 'Leaderboards');
    check('Back button is barrier', 'minecraft:barrier', win.slot(1, 1).item);
    check('First stat category is clock', 'minecraft:clock', win.slotAt(9).item);
  });

  it('TC-71: clicking stat in selector opens leaderboard', async () => {
    tc('TC-71');
    bot.chat('/bl top');
    const selector = await bot.nextWindow(10_000);
    const lb = await selector.clickSlot(9);

    checkContains('Title contains Leaderboard', lb.title, 'Leaderboard');
    check('Back button present', 'minecraft:barrier', lb.slot(1, 1).item);
    check('You head is PLAYER_HEAD', 'minecraft:player_head', lb.slotAt(49).item);
  });

  it('TC-72: /bl top deaths opens specific leaderboard', async () => {
    tc('TC-72');
    bot.chat('/bl top deaths');
    const win = await bot.nextWindow(10_000);

    checkContains('Title contains Deaths', win.title, 'Deaths');
    check('Stat icon is skeleton_skull', 'minecraft:skeleton_skull', win.slot(5, 1).item);
  });

  it('TC-73: gold ingot in player list opens leaderboards', async () => {
    tc('TC-73');
    bot.chat('/bl');
    const list = await bot.nextWindow(10_000);

    check('Slot 2 is gold_ingot', 'minecraft:gold_ingot', list.slotAt(2).item);
    const lb = await list.clickSlot(2);
    checkContains('New window title contains Leaderboards', lb.title, 'Leaderboards');
  });

  // ─── Achievements ─────────────────────────────────────────────

  it('TC-80: /bl achievements opens achievements GUI', async () => {
    tc('TC-80');
    bot.chat('/bl achievements');
    const win = await bot.nextWindow(10_000);

    checkContains('Title contains Achievements', win.title, 'Achievements');
    check('Back button present', 'minecraft:barrier', win.slot(1, 1).item);
    checkDefined('At least 1 item at slotAt(9)', win.slotAt(9));
  });

  it('TC-81: emerald in player list opens achievements', async () => {
    tc('TC-81');
    bot.chat('/bl');
    const list = await bot.nextWindow(10_000);

    check('Slot 6 is emerald', 'minecraft:emerald', list.slotAt(6).item);
    const ach = await list.clickSlot(6);
    checkContains('New window title contains Achievements', ach.title, 'Achievements');
  });

  it('TC-82: achievement items present with correct structure', async () => {
    tc('TC-82');
    bot.chat('/bl achievements');
    const win = await bot.nextWindow(10_000);

    const first = win.slotAt(9);
    checkDefined('First achievement is defined', first);
    checkTruthy('First achievement has an item', first.item !== null);
    checkDefined('First achievement has a name', first.name);
  });
});
