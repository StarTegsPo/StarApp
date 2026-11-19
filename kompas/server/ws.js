// Минимальный WebSocket-сервер (RFC 6455) без внешних зависимостей:
// рукопожатие, текстовые кадры, фрагментация, ping/pong, закрытие.
import { createHash } from 'node:crypto';
import { EventEmitter } from 'node:events';

const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';
const MAX_MESSAGE = 1024 * 1024; // 1 МБ — больше по сокету не шлём, файлы идут через HTTP

export class WsConnection extends EventEmitter {
  constructor(socket) {
    super();
    this.socket = socket;
    this.buffer = Buffer.alloc(0);
    this.fragments = [];
    this.closed = false;
    this.alive = true;
    socket.setNoDelay(true);
    socket.on('data', (chunk) => this.#onData(chunk));
    socket.on('end', () => this.#finish()); // http-сервер держит сокеты в режиме allowHalfOpen
    socket.on('close', () => this.#finish());
    socket.on('error', () => this.#finish());
  }

  send(data) {
    if (this.closed) return;
    const payload = Buffer.from(typeof data === 'string' ? data : JSON.stringify(data));
    this.socket.write(frame(0x1, payload));
  }

  ping() {
    if (!this.closed) this.socket.write(frame(0x9, Buffer.alloc(0)));
  }

  close(code = 1000) {
    if (this.closed) return;
    const p = Buffer.alloc(2);
    p.writeUInt16BE(code);
    try { this.socket.end(frame(0x8, p)); } catch { /* сокет уже закрыт */ }
    this.#finish();
  }

  #finish() {
    if (this.closed) return;
    this.closed = true;
    this.socket.destroy();
    this.emit('close');
  }

  #onData(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (this.buffer.length >= 2) {
      const b0 = this.buffer[0];
      const b1 = this.buffer[1];
      const fin = (b0 & 0x80) !== 0;
      const opcode = b0 & 0x0f;
      const masked = (b1 & 0x80) !== 0;
      let len = b1 & 0x7f;
      let offset = 2;
      if (len === 126) {
        if (this.buffer.length < 4) return;
        len = this.buffer.readUInt16BE(2);
        offset = 4;
      } else if (len === 127) {
        if (this.buffer.length < 10) return;
        const big = this.buffer.readBigUInt64BE(2);
        if (big > BigInt(MAX_MESSAGE)) return this.close(1009);
        len = Number(big);
        offset = 10;
      }
      if (len > MAX_MESSAGE) return this.close(1009);
      if (!masked) return this.close(1002); // клиент обязан маскировать кадры
      if (this.buffer.length < offset + 4 + len) return;
      const mask = this.buffer.subarray(offset, offset + 4);
      const payload = Buffer.from(this.buffer.subarray(offset + 4, offset + 4 + len));
      for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
      this.buffer = this.buffer.subarray(offset + 4 + len);
      this.#onFrame(fin, opcode, payload);
      if (this.closed) return;
    }
  }

  #onFrame(fin, opcode, payload) {
    this.alive = true;
    switch (opcode) {
      case 0x0: // продолжение
      case 0x1: // текст
      case 0x2: { // бинарные — принимаем как текст
        this.fragments.push(payload);
        const total = this.fragments.reduce((s, b) => s + b.length, 0);
        if (total > MAX_MESSAGE) return this.close(1009);
        if (fin) {
          const text = Buffer.concat(this.fragments).toString('utf8');
          this.fragments = [];
          let msg;
          try { msg = JSON.parse(text); } catch { return; }
          this.emit('message', msg);
        }
        break;
      }
      case 0x8: this.close(1000); break;
      case 0x9: if (!this.closed) this.socket.write(frame(0xa, payload)); break;
      case 0xa: break; // pong
      default: this.close(1002);
    }
  }
}

function frame(opcode, payload) {
  const len = payload.length;
  let header;
  if (len < 126) {
    header = Buffer.from([0x80 | opcode, len]);
  } else if (len < 65536) {
    header = Buffer.alloc(4);
    header[0] = 0x80 | opcode; header[1] = 126; header.writeUInt16BE(len, 2);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x80 | opcode; header[1] = 127; header.writeBigUInt64BE(BigInt(len), 2);
  }
  return Buffer.concat([header, payload]);
}

/** Завершает HTTP upgrade и возвращает соединение (или null, если запрос не WebSocket). */
export function acceptUpgrade(req, socket) {
  const key = req.headers['sec-websocket-key'];
  if (!key || String(req.headers.upgrade).toLowerCase() !== 'websocket') {
    socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');
    return null;
  }
  const accept = createHash('sha1').update(key + GUID).digest('base64');
  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
    'Upgrade: websocket\r\n' +
    'Connection: Upgrade\r\n' +
    `Sec-WebSocket-Accept: ${accept}\r\n\r\n`,
  );
  return new WsConnection(socket);
}
