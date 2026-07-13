/*
 * End-to-end Java inventory GUI smoke test for the local Paper + Velocity stack.
 *
 * Install the runtime-only dependency first:
 *   npm install --prefix test_env/node-player mineflayer@4.37.1
 *
 * Then run:
 *   node scripts/integration/NodePlayerGuiTest.js
 */

'use strict';

const path = require('node:path');

const projectRoot = path.resolve(__dirname, '..', '..');
const runtimeModules = path.join(projectRoot, 'test_env', 'node-player', 'node_modules');
let mineflayer;

try {
    mineflayer = require(require.resolve('mineflayer', { paths: [runtimeModules] }));
} catch (error) {
    console.error(JSON.stringify({
        result: 'FAIL',
        reason: 'mineflayer is not installed in test_env/node-player',
        install: 'npm install --prefix test_env/node-player mineflayer@4.37.1'
    }));
    process.exit(2);
}

const host = process.env.VN_TEST_HOST || '127.0.0.1';
const port = Number(process.env.VN_TEST_PORT || '25565');
const timeoutMs = Number(process.env.VN_TEST_TIMEOUT_MS || '45000');
const username = `VNGui${Date.now().toString(36).slice(-6)}`;

const state = {
    connected: false,
    spawned: false,
    inventoryOpened: false,
    selectionClicked: false,
    selectionResult: false,
    window: null,
    messages: [],
    failure: null
};

let bot;
let completed = false;
let deadline;

function output(result, extra = {}) {
    console.log(JSON.stringify({
        result,
        host,
        port,
        username,
        ...state,
        ...extra
    }));
}

function finish(result, exitCode, extra) {
    if (completed) return;
    completed = true;
    process.exitCode = exitCode;
    clearTimeout(deadline);
    output(result, extra);
    if (bot && bot.end) {
        try {
            bot.end('VelocityNavigator GUI smoke test complete');
        } catch (_) {
            // The process exit below is still deterministic if the socket is already closed.
        }
    }
    // Keep this timer referenced. An unreferenced timer can allow Node to end
    // naturally with status 0 before a failed smoke test reports its status.
    setTimeout(() => process.exit(exitCode), 500);
}

function textOf(message) {
    if (typeof message === 'string') return message;
    if (message?.type === 'string' && typeof message.value === 'string') return message.value;
    if (message?.type === 'compound' && typeof message.value?.text?.value === 'string') {
        return message.value.text.value;
    }
    if (message && typeof message.text === 'string') {
        const extras = Array.isArray(message.extra) ? message.extra.map(textOf).join('') : '';
        return message.text + extras;
    }
    try {
        const rendered = message.toString();
        return rendered === '[object Object]' ? JSON.stringify(message) : rendered;
    } catch (_) {
        return String(message);
    }
}

try {
    bot = mineflayer.createBot({
        host,
        port,
        username,
        auth: 'offline',
        version: '1.21.11',
        checkTimeoutInterval: timeoutMs
    });
} catch (error) {
    state.failure = `could not create player: ${error.message}`;
    finish('FAIL', 1);
}

deadline = setTimeout(() => {
    state.failure = 'timed out before the complete GUI open/click/result flow finished';
    finish('FAIL', 1);
}, timeoutMs);

bot.once('login', () => {
    state.connected = true;
});

bot.once('spawn', () => {
    state.spawned = true;
    // `menu` overrides use_menu_for_lobby=false and explicitly exercises the
    // Java inventory selector path rather than the normal auto-route path.
    setTimeout(() => bot.chat('/lobby menu'), 750);
});

bot.on('messagestr', (message) => {
    const text = textOf(message);
    state.messages.push(text);
    state.messages = state.messages.slice(-12);
    if (state.selectionClicked && /already connected/i.test(text)) {
        state.selectionResult = true;
        finish('PASS', 0, {
            assertions: [
                'Node player logged in through Velocity',
                'Node player spawned on the Paper backend',
                'Velocity sent an OpenMenu bridge packet and Paper opened a Bukkit inventory',
                'Node player clicked the lobby item and Paper sent the selection back to Velocity',
                'Velocity processed the selection and returned the expected same-server result'
            ]
        });
    }
});

bot.once('windowOpen', async (window) => {
    state.inventoryOpened = true;
    const selectable = window.slots.find((item) => item && item.name !== 'gray_stained_glass_pane');
    state.window = {
        title: textOf(window.title),
        type: window.type,
        slotCount: window.slots.length,
        selectableItem: selectable ? {
            slot: selectable.slot,
            name: selectable.name,
            displayName: textOf(selectable.displayName),
            count: selectable.count
        } : null
    };
    if (!selectable) {
        state.failure = 'the backend inventory opened without a selectable lobby item';
        finish('FAIL', 1, { windowTitle: textOf(window.title) });
        return;
    }

    try {
        await bot.clickWindow(selectable.slot, 0, 0);
        state.selectionClicked = true;
    } catch (error) {
        state.failure = `could not click GUI slot ${selectable.slot}: ${error.message}`;
        finish('FAIL', 1, { windowTitle: textOf(window.title), selectedSlot: selectable.slot });
    }
});

bot.on('kicked', (reason) => {
    state.failure = `kicked: ${textOf(reason)}`;
    finish('FAIL', 1);
});

bot.on('error', (error) => {
    if (completed) return;
    state.failure = `network error: ${error.message}`;
    finish('FAIL', 1);
});

bot.on('end', (reason) => {
    if (completed) return;
    state.failure = `connection ended unexpectedly: ${textOf(reason)}`;
    finish('FAIL', 1);
});
