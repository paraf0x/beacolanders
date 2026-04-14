import { describe, it, beforeAll, afterAll } from 'vitest';
import { acquireServer, releaseServer, getServer } from './helpers.js';
import { tc, check, checkTruthy, checkDefined, checkContains, checkGte, printProtocol } from './protocol.js';
import type { McBot, WindowSnapshot, RconClient } from 'mc-e2e';
import { delay } from 'mc-e2e';

let bot: McBot;
let rcon: RconClient;

describe('Beacolanders Player GUI', () => {
  beforeAll(async () => {
    await acquireServer();
    bot = await getServer().createBot('GuiTester');
    rcon = await getServer().rcon();
  });

  afterAll(async () => {
    console.log(printProtocol());
    try { await bot?.disconnect(); } catch { /* ok */ }
    await releaseServer();
  });

  it('TC-01: /bl opens player list GUI', async () => {
    tc('TC-01');

    bot.chat('/bl');
    const win = await bot.nextWindow(10_000);

    checkDefined('Window opened', win);
    checkContains('Title contains Beacolanders', win.title, 'Beacolanders');
    check('Window has 6 rows', 6, win.rows);

    const searchSlot = win.slot(1, 1);
    checkDefined('Search button exists', searchSlot.item);
    check('Search button is compass', 'minecraft:compass', searchSlot.item);

    const titleSlot = win.slot(5, 1);
    checkDefined('Title item exists', titleSlot.item);
    check('Title item is nether_star', 'minecraft:nether_star', titleSlot.item);

    const heads = win.findSlots(s => s.item === 'minecraft:player_head');
    checkGte('At least 1 player head', heads.length, 1);

    const ourHead = heads.find(s => s.name?.includes('GuiTester'));
    checkDefined('Our bot head found', ourHead);

    await bot.closeWindow();
  });

  it('TC-02: /beacolanders alias works', async () => {
    tc('TC-02');

    bot.chat('/beacolanders');
    const win = await bot.nextWindow(10_000);

    checkDefined('Window opened', win);
    checkContains('Title contains Beacolanders', win.title, 'Beacolanders');

    await bot.closeWindow();
  });

  it('TC-03: clicking player head opens detail view', async () => {
    tc('TC-03');

    bot.chat('/bl');
    const listWin = await bot.nextWindow(10_000);

    const heads = listWin.findSlots(s => s.item === 'minecraft:player_head');
    checkGte('Player heads exist', heads.length, 1);

    const head = heads[0];
    const detailWin = await listWin.clickSlot(head.index);

    checkDefined('Detail window opened', detailWin);
    checkTruthy('Detail title not empty', detailWin.title.length > 0);
    check('Detail has 6 rows', 6, detailWin.rows);

    const backSlot = detailWin.slot(1, 1);
    checkDefined('Back button exists', backSlot.item);
    check('Back button is barrier', 'minecraft:barrier', backSlot.item);

    const headSlot = detailWin.slot(5, 1);
    checkDefined('Player head exists', headSlot.item);
    check('Player head is player_head', 'minecraft:player_head', headSlot.item);

    await bot.closeWindow();
  });

  it('TC-04: equipment row shows gear slots', async () => {
    tc('TC-04');

    await rcon.send('item replace entity GuiTester armor.head with minecraft:diamond_helmet');
    await rcon.send('item replace entity GuiTester armor.chest with minecraft:iron_chestplate');
    await delay(500);

    bot.chat('/bl');
    const listWin = await bot.nextWindow(10_000);

    const heads = listWin.findSlots(s => s.item === 'minecraft:player_head');
    const head = heads.find(s => s.name?.includes('GuiTester')) ?? heads[0];
    const detailWin = await listWin.clickSlot(head.index);

    const helmetSlot = detailWin.slot(2, 2);
    check('Helmet is diamond_helmet', 'minecraft:diamond_helmet', helmetSlot.item);

    const chestSlot = detailWin.slot(3, 2);
    check('Chestplate is iron_chestplate', 'minecraft:iron_chestplate', chestSlot.item);

    await bot.closeWindow();
  });

  it('TC-05: stats row is populated', async () => {
    tc('TC-05');

    bot.chat('/bl');
    const listWin = await bot.nextWindow(10_000);

    const heads = listWin.findSlots(s => s.item === 'minecraft:player_head');
    const head = heads.find(s => s.name?.includes('GuiTester')) ?? heads[0];
    const detailWin = await listWin.clickSlot(head.index);

    const statsRow = [];
    for (let x = 1; x <= 9; x++) {
      const s = detailWin.slot(x, 5);
      if (s.item) statsRow.push(s);
    }
    checkGte('Stats row has items', statsRow.length, 1);

    const clockSlot = statsRow.find(s => s.item === 'minecraft:clock');
    checkDefined('Playtime clock found in stats', clockSlot);

    // Check scroll arrow exists (10 stats > 7 visible)
    const rightArrow = detailWin.slot(9, 5);
    if (rightArrow.item === 'minecraft:arrow') {
      checkTruthy('Right arrow shows remaining count', rightArrow.name?.includes('+'));
    }

    await bot.closeWindow();
  });

  it('TC-06: back button returns to player list', async () => {
    tc('TC-06');

    bot.chat('/bl');
    const listWin = await bot.nextWindow(10_000);

    const heads = listWin.findSlots(s => s.item === 'minecraft:player_head');
    const head = heads[0];
    const detailWin = await listWin.clickSlot(head.index);

    // Click back button — opens a new inventory
    const backWin = await detailWin.click(1, 1);

    checkDefined('Back returned a window', backWin);
    checkContains('Back to player list', backWin.title, 'Beacolanders');

    const headsAgain = backWin.findSlots(s => s.item === 'minecraft:player_head');
    checkGte('Player heads visible again', headsAgain.length, 1);

    await bot.closeWindow();
  });
});
