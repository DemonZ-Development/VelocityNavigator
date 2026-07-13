/*
 * Real capacity-queue smoke test for the local Velocity + Paper stack.
 * The proxy fixture must provide a capped lobby and a separate holding server.
 */

'use strict';

const path = require('node:path');

const projectRoot = path.resolve(__dirname, '..', '..');
const runtimeModules = path.join(projectRoot, 'test_env', 'node-player', 'node_modules');
let mineflayer;

try {
    mineflayer = require(require.resolve('mineflayer', { paths: [runtimeModules] }));
} catch (_) {
    console.error(JSON.stringify({
        result: 'FAIL',
        reason: 'mineflayer is not installed in test_env/node-player',
        install: 'npm install --prefix test_env/node-player mineflayer@4.37.1'
    }));
    process.exit(2);
}

const host = process.env.VN_TEST_HOST || '127.0.0.1';
const port = Number(process.env.VN_TEST_PORT || '25565');
const timeoutMs = Number(process.env.VN_TEST_TIMEOUT_MS || '30000');
const username = `VNQueue${Date.now().toString(36).slice(-5)}`;
const messages = [];
let queueCommandTree = { received: false, nodeCount: 0, matches: [] };

function textOf(message) {
    try {
        return typeof message === 'string' ? message : message.toString();
    } catch (_) {
        return String(message);
    }
}

function waitForMessage(bot, pattern, action = () => {}) {
    return new Promise((resolve, reject) => {
        const existing = messages.find((message) => pattern.test(message));
        if (existing) {
            resolve(existing);
            return;
        }
        const timer = setTimeout(() => {
            bot.removeListener('messagestr', listener);
            reject(new Error(`Did not receive ${pattern}`));
        }, timeoutMs);
        const listener = (message) => {
            const text = textOf(message);
            if (!pattern.test(text)) return;
            clearTimeout(timer);
            bot.removeListener('messagestr', listener);
            resolve(text);
        };
        bot.on('messagestr', listener);
        action();
    });
}

function delay(milliseconds) {
    return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function run() {
    const bot = mineflayer.createBot({
        host,
        port,
        username,
        auth: 'offline',
        version: '1.21.11',
        checkTimeoutInterval: timeoutMs
    });
    bot._client.on('declare_commands', (packet) => {
        const nodes = Array.isArray(packet.nodes) ? packet.nodes : [];
        queueCommandTree = {
            received: true,
            nodeCount: nodes.length,
            rootIndex: packet.rootIndex,
            rootNode: nodes[packet.rootIndex],
            matches: nodes.map((node, index) => ({ index, node }))
                .filter(({ node }) => /queue|leave/i.test(JSON.stringify(node)))
        };
    });
    bot.on('messagestr', (message) => {
        messages.push(textOf(message));
        if (messages.length > 40) messages.shift();
    });

    try {
        await new Promise((resolve, reject) => {
            const timer = setTimeout(() => reject(new Error('Player did not spawn on the holding server')), timeoutMs);
            bot.once('spawn', () => {
                clearTimeout(timer);
                resolve();
            });
            bot.once('kicked', (reason) => reject(new Error(`Kicked: ${textOf(reason)}`)));
            bot.once('error', reject);
        });

        await delay(1500);
        await waitForMessage(bot, /position.*1\/1/i, () => bot.chat('/queue'));
        await delay(750);
        await waitForMessage(bot, /left (?:the )?(?:lobby )?queue/i, () => bot.chat('/queue leave'));
        await delay(750);
        await waitForMessage(bot, /not (?:currently )?(?:queued|in (?:the )?(?:lobby )?queue)/i, () => bot.chat('/queue'));

        console.log(JSON.stringify({
            result: 'PASS',
            host,
            port,
            username,
            assertions: [
                'full initial-join pool routed the player to the holding server',
                'the player entered the queue at position 1',
                '/queue reported the live position and size',
                '/queue leave removed the player',
                '/queue confirmed the player was no longer queued'
            ]
        }));
    } finally {
        bot.end('VelocityNavigator queue smoke test complete');
    }
}

run().then(() => process.exit(0)).catch((error) => {
    console.error(JSON.stringify({
        result: 'FAIL',
        host,
        port,
        username,
        reason: error.message,
        messages,
        queueCommandTree
    }));
    process.exit(1);
});
