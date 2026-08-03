package ua.pocketbridge.lgp500;

import android.content.Context;
import android.os.Handler;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Iterator;
import org.json.JSONObject;

/** Minimal RFC 6455 client for API-10 devices. Uses only the local ws:// PocketBridge endpoint. */
final class RealtimeInputClient {
    interface Listener {
        void onReady();
        void onClosed(String detail);
        void onMessage(JSONObject message);
    }

    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_QUEUE = 24;
    private static final int MAX_HARD_QUEUE = 32;
    private final Context context;
    private final Handler handler;
    private final Listener listener;
    private final Object stateLock = new Object();
    private final Object writeLock = new Object();
    private final ArrayDeque<InputPacket> queue = new ArrayDeque<InputPacket>();
    private final SecureRandom random = new SecureRandom();
    private volatile boolean running;
    private volatile boolean ready;
    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private Thread connectThread;
    private Thread writerThread;
    private Thread readerThread;

    RealtimeInputClient(Context context, Handler handler, Listener listener) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.listener = listener;
    }

    void connect() {
        synchronized (stateLock) {
            if (running) return;
            running = true;
            ready = false;
        }
        connectThread = new Thread(new Runnable() {
            @Override public void run() { open(); }
        }, "PocketBridge-WS-Connect");
        connectThread.start();
    }

    boolean isReady() { return ready && running; }

    boolean sendInput(String event, int dx, int dy, int delta, String value, int sequence) {
        if (!isReady()) return false;
        String kind = event == null ? "" : event;
        InputPacket packet = new InputPacket(
                kind,
                clamp(dx, -300, 300),
                clamp(dy, -300, 300),
                clamp(delta, -10, 10),
                value == null ? "" : value,
                Math.max(0, sequence));
        synchronized (queue) {
            if (!running || !ready) return false;
            if ("m".equals(kind)) {
                InputPacket last = queue.peekLast();
                if (last != null && "m".equals(last.event)) {
                    queue.removeLast();
                    packet.dx = clamp(last.dx + packet.dx, -300, 300);
                    packet.dy = clamp(last.dy + packet.dy, -300, 300);
                }
                if (queue.size() >= MAX_QUEUE && !removeOldestMovementLocked()) {
                    return false;
                }
            } else if (queue.size() >= MAX_QUEUE) {
                // Clicks and drag boundaries must never evict an older critical event.
                // When the queue contains only critical packets, report backpressure so
                // NativeCoreActivity can use the ordered HTTP fallback instead.
                removeOldestMovementLocked();
                if (queue.size() >= MAX_HARD_QUEUE) return false;
            }
            queue.addLast(packet);
            queue.notifyAll();
        }
        return true;
    }

    private boolean removeOldestMovementLocked() {
        Iterator<InputPacket> iterator = queue.iterator();
        while (iterator.hasNext()) {
            if ("m".equals(iterator.next().event)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    void close() { shutdown("З’єднання закрито", false); }

    private void open() {
        try {
            URL base = new URL(AppPreferences.baseUrl(context));
            String host = base.getHost();
            int port = base.getPort() > 0 ? base.getPort() : 80;
            Socket created = new Socket();
            created.connect(new InetSocketAddress(host, port), 3500);
            created.setTcpNoDelay(true);
            created.setKeepAlive(true);
            created.setSoTimeout(15000);
            socket = created;
            input = created.getInputStream();
            output = created.getOutputStream();

            byte[] nonce = new byte[16];
            random.nextBytes(nonce);
            String key = Base64.encodeToString(nonce, Base64.NO_WRAP);
            String path = "/api/realtime/input?role=" + AppPreferences.urlEncode(AppPreferences.deviceRole(context));
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + ":" + port + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "X-PocketBridge-Token: " + AppPreferences.token(context) + "\r\n"
                    + "User-Agent: PocketBridgeRemote/" + BuildConfig.VERSION_NAME + "\r\n\r\n";
            output.write(request.getBytes("UTF-8"));
            output.flush();

            String status = readHttpLine(input);
            if (status == null || status.indexOf(" 101 ") < 0) {
                throw new EOFException("WebSocket handshake: " + (status == null ? "без відповіді" : status));
            }
            String accept = "";
            String line;
            while ((line = readHttpLine(input)) != null && line.length() > 0) {
                int colon = line.indexOf(':');
                if (colon > 0 && "sec-websocket-accept".equals(line.substring(0, colon).trim().toLowerCase())) {
                    accept = line.substring(colon + 1).trim();
                }
            }
            String expected = Base64.encodeToString(
                    MessageDigest.getInstance("SHA-1").digest((key + MAGIC).getBytes("ISO-8859-1")),
                    Base64.NO_WRAP);
            if (!expected.equals(accept)) throw new EOFException("Некоректний WebSocket handshake");

            ready = true;
            writerThread = new Thread(new Runnable() {
                @Override public void run() { writerLoop(); }
            }, "PocketBridge-WS-Writer");
            readerThread = new Thread(new Runnable() {
                @Override public void run() { readerLoop(); }
            }, "PocketBridge-WS-Reader");
            writerThread.start();
            readerThread.start();
            postReady();
        } catch (Exception exception) {
            shutdown(message(exception), true);
        }
    }

    private void writerLoop() {
        try {
            while (running) {
                InputPacket value;
                synchronized (queue) {
                    while (running && queue.isEmpty()) queue.wait(10000L);
                    if (!running) return;
                    value = queue.pollFirst();
                }
                if (value != null) writeFrame(0x1, value.toJson().getBytes("UTF-8"));
            }
        } catch (Exception exception) {
            shutdown(message(exception), true);
        }
    }

    private void readerLoop() {
        long lastPing = System.currentTimeMillis();
        try {
            while (running) {
                try {
                    Frame frame = readFrame();
                    if (frame.opcode == 0x8) {
                        shutdown("Сервер закрив realtime-канал", true);
                        return;
                    }
                    if (frame.opcode == 0x9) {
                        writeFrame(0xA, frame.payload);
                    } else if (frame.opcode == 0x1) {
                        final JSONObject message = new JSONObject(new String(frame.payload, "UTF-8"));
                        handler.post(new Runnable() {
                            @Override public void run() { if (listener != null) listener.onMessage(message); }
                        });
                    }
                } catch (SocketTimeoutException timeout) {
                    long now = System.currentTimeMillis();
                    if (now - lastPing >= 12000L) {
                        writeFrame(0x9, new byte[0]);
                        lastPing = now;
                    }
                }
            }
        } catch (Exception exception) {
            shutdown(message(exception), true);
        }
    }

    private Frame readFrame() throws Exception {
        int first = input.read();
        if (first < 0) throw new EOFException("Realtime-канал розірвано");
        int second = input.read();
        if (second < 0) throw new EOFException("Realtime-канал розірвано");
        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) {
            length = ((long) readRequired(input) << 8) | readRequired(input);
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) length = (length << 8) | readRequired(input);
        }
        if (length > 65536L) throw new EOFException("Завеликий WebSocket-пакет");
        byte[] mask = masked ? readExact(input, 4) : null;
        byte[] payload = readExact(input, (int) length);
        if (mask != null) {
            for (int i = 0; i < payload.length; i++) payload[i] = (byte) (payload[i] ^ mask[i % 4]);
        }
        return new Frame(opcode, payload);
    }

    private void writeFrame(int opcode, byte[] payload) throws Exception {
        if (!running || output == null) return;
        byte[] value = payload == null ? new byte[0] : payload;
        if (value.length > 65535) throw new IllegalArgumentException("Пакет завеликий");
        ByteArrayOutputStream frame = new ByteArrayOutputStream(value.length + 16);
        frame.write(0x80 | (opcode & 0x0F));
        if (value.length <= 125) {
            frame.write(0x80 | value.length);
        } else {
            frame.write(0x80 | 126);
            frame.write((value.length >> 8) & 0xFF);
            frame.write(value.length & 0xFF);
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        frame.write(mask);
        for (int i = 0; i < value.length; i++) frame.write(value[i] ^ mask[i % 4]);
        synchronized (writeLock) {
            output.write(frame.toByteArray());
            output.flush();
        }
    }

    private void shutdown(final String detail, boolean notify) {
        boolean wasRunning;
        synchronized (stateLock) {
            wasRunning = running;
            running = false;
            ready = false;
        }
        synchronized (queue) {
            queue.clear();
            queue.notifyAll();
        }
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
        socket = null;
        input = null;
        output = null;
        if (notify && wasRunning) {
            handler.post(new Runnable() {
                @Override public void run() { if (listener != null) listener.onClosed(detail); }
            });
        }
    }

    private void postReady() {
        handler.post(new Runnable() {
            @Override public void run() { if (listener != null) listener.onReady(); }
        });
    }

    private static String readHttpLine(InputStream input) throws Exception {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        while (line.size() < 8192) {
            int current = input.read();
            if (current < 0) return line.size() == 0 ? null : line.toString("ISO-8859-1");
            if (previous == '\r' && current == '\n') {
                byte[] value = line.toByteArray();
                int size = Math.max(0, value.length - 1);
                return new String(value, 0, size, "ISO-8859-1");
            }
            line.write(current);
            previous = current;
        }
        throw new EOFException("Завеликий HTTP-заголовок");
    }

    private static int readRequired(InputStream input) throws Exception {
        int value = input.read();
        if (value < 0) throw new EOFException("Неповний WebSocket-пакет");
        return value;
    }

    private static byte[] readExact(InputStream input, int length) throws Exception {
        byte[] value = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(value, offset, length - offset);
            if (count < 0) throw new EOFException("Неповний WebSocket-пакет");
            offset += count;
        }
        return value;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String message(Exception exception) {
        String value = exception == null ? "" : exception.getMessage();
        return value == null || value.trim().length() == 0 ? "Realtime-канал недоступний" : value;
    }

    private static final class InputPacket {
        final String event;
        int dx;
        int dy;
        final int delta;
        final String value;
        final int sequence;

        InputPacket(String event, int dx, int dy, int delta, String value, int sequence) {
            this.event = event;
            this.dx = dx;
            this.dy = dy;
            this.delta = delta;
            this.value = value;
            this.sequence = sequence;
        }

        String toJson() throws Exception {
            JSONObject data = new JSONObject();
            data.put("e", event);
            data.put("x", dx);
            data.put("y", dy);
            data.put("d", delta);
            data.put("s", sequence);
            if (value.length() > 0) data.put("v", value);
            return data.toString();
        }
    }

    private static final class Frame {
        final int opcode;
        final byte[] payload;
        Frame(int opcode, byte[] payload) { this.opcode = opcode; this.payload = payload; }
    }
}
