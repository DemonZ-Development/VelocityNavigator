/*
 * Real two-player party smoke test for the local Velocity + Paper stack.
 * Uses the Mineflayer dependency installed for NodePlayerGuiTest.js.
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
const timeoutMs = Number(process.env.VN_TEST_TIMEOUT_MS || '45000');
const suffix = Date.now().toString(36).slice(-5);
const leaderName = `VNLead${suffix}`;
const memberName = `VNMem${suffix}`;
const chatMarker = `stable-${suffix}`;
const shortcutMarker = `shortcut-${suffix}`;
const bots = [];
const transcripts = new Map();

function textOf(message) {
    try {
        return typeof message === 'string' ? message : message.toString();
    } catch (_) {
        return String(message);
    }
}

function createPlayer(username) {
    const bot = mineflayer.createBot({
        host,
        port,
        username,
        auth: 'offline',
        version: '1.21.11',
        checkTimeoutInterval: timeoutMs
    });
    const messages = [];
    transcripts.set(username, messages);
    bot.on('messagestr', (message) => {
        messages.push(textOf(message));
        if (messages.length > 30) messages.shift();
    });
    bots.push(bot);
    return bot;
}

function waitForSpawn(bot, username) {
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error(`${username} did not spawn`)), timeoutMs);
        bot.once('spawn', () => {
            clearTimeout(timer);
            resolve();
        });
        bot.once('kicked', (reason) => {
            clearTimeout(timer);
            reject(new Error(`${username} was kicked: ${textOf(reason)}`));
        });
        bot.once('error', (error) => {
            clearTimeout(timer);
            reject(new Error(`${username} connection error: ${error.message}`));
        });
    });
}

function waitForMessage(bot, username, pattern, action) {
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
            bot.removeListener('messagestr', listener);
            reject(new Error(`${username} did not receive ${pattern}`));
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

async function closePlayers() {
    for (const bot of bots) {
        try {
            bot.end('VelocityNavigator party smoke test complete');
        } catch (_) {
            // A failed connection may already be closed.
        }
    }
    await delay(500);
}

async function run() {
    const leader = createPlayer(leaderName);
    await waitForSpawn(leader, leaderName);
    // Paper's default same-IP connection throttle rejects two test accounts
    // that authenticate in the same instant. Both remain online together after
    // this stagger, so the party interaction is still genuinely concurrent.
    await delay(5000);
    const member = createPlayer(memberName);
    await waitForSpawn(member, memberName);
    await delay(750);

    await Promise.all([
        waitForMessage(leader, leaderName, new RegExp(`Invited ${memberName} to your party`, 'i'),
            () => leader.chat(`/party invite ${memberName}`)),
        waitForMessage(member, memberName, new RegExp(`${leaderName} invited you to a party`, 'i'), () => {})
    ]);

    await Promise.all([
        waitForMessage(member, memberName, /You joined the party/i, () => member.chat('/party accept')),
        waitForMessage(leader, leaderName, new RegExp(`${memberName} joined your party`, 'i'), () => {})
    ]);
    await delay(350);

    const leaderStatus = await waitForMessage(leader, leaderName, /Party \(2\):/i, () => leader.chat('/party status'));
    if (!leaderStatus.includes(leaderName) || !leaderStatus.includes(memberName)) {
        throw new Error(`party status did not contain both members: ${leaderStatus}`);
    }
    await delay(1000);

    await Promise.all([
        waitForMessage(leader, leaderName, new RegExp(`\\[Party] ${leaderName}: ${chatMarker}`, 'i'),
            () => leader.chat(`/party chat ${chatMarker}`)),
        waitForMessage(member, memberName, new RegExp(`\\[Party] ${leaderName}: ${chatMarker}`, 'i'), () => {})
    ]);
    await delay(1000);

    await Promise.all([
        waitForMessage(leader, leaderName, new RegExp(`\\[Party] ${leaderName}: ${shortcutMarker}`, 'i'),
            () => leader.chat(`/p ${shortcutMarker}`)),
        waitForMessage(member, memberName, new RegExp(`\\[Party] ${leaderName}: ${shortcutMarker}`, 'i'), () => {})
    ]);
    await delay(1000);

    await waitForMessage(member, memberName, /Party updated/i, () => member.chat('/party leave'));
    await delay(1000);
    await waitForMessage(member, memberName, /You are not in a party/i, () => member.chat('/party status'));

    const remainingStatus = await waitForMessage(leader, leaderName, /Party \(1\):/i, () => leader.chat('/party status'));
    if (!remainingStatus.includes(leaderName) || remainingStatus.includes(memberName)) {
        throw new Error(`party leave was not reflected in leader status: ${remainingStatus}`);
    }
    await delay(1000);

    await waitForMessage(leader, leaderName, /The party was disbanded/i, () => leader.chat('/party disband'));

    console.log(JSON.stringify({
        result: 'PASS',
        host,
        port,
        leader: leaderName,
        member: memberName,
        assertions: [
            'two Node players joined through Velocity',
            'party invitation reached both players',
            'member accepted and both-member status was correct',
            'canonical and /p shortcut party chat reached leader and member',
            'member leave removed party membership',
            'leader status updated and disband completed'
        ]
    }));
}

(async () => {
    try {
        await run();
        await closePlayers();
        process.exit(0);
    } catch (error) {
        console.error(JSON.stringify({
            result: 'FAIL',
            host,
            port,
            leader: leaderName,
            member: memberName,
            reason: error.message,
            transcripts: Object.fromEntries(transcripts)
        }));
        await closePlayers();
        process.exit(1);
    }
})();
