/*
 * Minimal Redis-compatible pub/sub fixture for VelocityNavigator integration
 * tests. It implements the RESP commands used by the proxy and universal
 * backend bridge: AUTH, PING, SUBSCRIBE, UNSUBSCRIBE, PUBLISH, and QUIT.
 */

'use strict';

const net = require('node:net');

const host = process.env.VN_REDIS_HOST || '127.0.0.1';
const port = Number(process.env.VN_REDIS_PORT || process.argv[2] || '16379');
const clients = new Set();

function parseValue(buffer, offset = 0) {
    if (offset >= buffer.length) return null;
    const type = String.fromCharCode(buffer[offset]);
    const end = buffer.indexOf('\r\n', offset + 1);
    if (end < 0) return null;
    const header = buffer.toString('utf8', offset + 1, end);
    let cursor = end + 2;

    if (type === '$') {
        const length = Number(header);
        if (!Number.isInteger(length) || length < -1) throw new Error('invalid bulk length');
        if (length === -1) return { value: null, next: cursor };
        if (buffer.length < cursor + length + 2) return null;
        const value = buffer.toString('utf8', cursor, cursor + length);
        if (buffer[cursor + length] !== 13 || buffer[cursor + length + 1] !== 10) {
            throw new Error('invalid bulk terminator');
        }
        return { value, next: cursor + length + 2 };
    }

    if (type === '*') {
        const count = Number(header);
        if (!Number.isInteger(count) || count < 0) throw new Error('invalid array length');
        const values = [];
        for (let i = 0; i < count; i += 1) {
            const parsed = parseValue(buffer, cursor);
            if (!parsed) return null;
            values.push(parsed.value);
            cursor = parsed.next;
        }
        return { value: values, next: cursor };
    }

    if (type === '+' || type === ':') return { value: header, next: cursor };
    throw new Error(`unsupported RESP type ${type}`);
}

function bulk(value) {
    const bytes = Buffer.from(String(value), 'utf8');
    return Buffer.concat([Buffer.from(`$${bytes.length}\r\n`), bytes, Buffer.from('\r\n')]);
}

function array(values) {
    return Buffer.concat([Buffer.from(`*${values.length}\r\n`), ...values.map(bulk)]);
}

function execute(client, values) {
    if (!Array.isArray(values) || values.length === 0) throw new Error('expected command array');
    const command = String(values[0]).toUpperCase();
    const args = values.slice(1).map(String);
    switch (command) {
        case 'AUTH':
            client.socket.write('+OK\r\n');
            break;
        case 'PING':
            client.socket.write(args.length ? bulk(args[0]) : '+PONG\r\n');
            break;
        case 'SUBSCRIBE':
            for (const channel of args) {
                client.channels.add(channel);
                client.socket.write(array(['subscribe', channel, String(client.channels.size)]));
            }
            break;
        case 'UNSUBSCRIBE':
            for (const channel of (args.length ? args : [...client.channels])) {
                client.channels.delete(channel);
                client.socket.write(array(['unsubscribe', channel, String(client.channels.size)]));
            }
            break;
        case 'PUBLISH': {
            if (args.length !== 2) throw new Error('PUBLISH needs channel and message');
            const [channel, message] = args;
            let receivers = 0;
            for (const subscriber of clients) {
                if (subscriber.channels.has(channel)) {
                    subscriber.socket.write(array(['message', channel, message]));
                    receivers += 1;
                }
            }
            client.socket.write(`:${receivers}\r\n`);
            process.stdout.write(`${JSON.stringify({ event: 'publish', channel, receivers, bytes: Buffer.byteLength(message) })}\n`);
            break;
        }
        case 'QUIT':
            client.socket.end('+OK\r\n');
            break;
        default:
            client.socket.write(`-ERR unsupported command ${command}\r\n`);
    }
}

const server = net.createServer((socket) => {
    const client = { socket, channels: new Set(), buffer: Buffer.alloc(0) };
    clients.add(client);
    socket.setNoDelay(true);
    socket.on('data', (chunk) => {
        client.buffer = Buffer.concat([client.buffer, chunk]);
        try {
            while (client.buffer.length) {
                const parsed = parseValue(client.buffer);
                if (!parsed) break;
                client.buffer = client.buffer.subarray(parsed.next);
                execute(client, parsed.value);
            }
        } catch (error) {
            socket.write(`-ERR ${error.message}\r\n`);
            socket.destroy();
        }
    });
    socket.on('close', () => clients.delete(client));
    socket.on('error', () => clients.delete(client));
});

server.listen(port, host, () => {
    process.stdout.write(`${JSON.stringify({ event: 'ready', host, port })}\n`);
});

function shutdown() {
    for (const client of clients) client.socket.destroy();
    server.close(() => process.exit(0));
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
