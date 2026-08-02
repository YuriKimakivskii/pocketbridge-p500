package ua.pocketbridge.lgp500;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.json.JSONObject;

final class DiscoveryClient {
    static final int DEFAULT_PORT = 8766;
    private static final byte[] REQUEST;

    static {
        try {
            REQUEST = "POCKETBRIDGE_DISCOVER_V1".getBytes("UTF-8");
        } catch (Exception ignored) {
            throw new RuntimeException(ignored);
        }
    }

    static final class Server {
        final String name;
        final String host;
        final int port;
        final String version;
        final boolean pairingEnabled;

        Server(String name, String host, int port, String version, boolean pairingEnabled) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.version = version;
            this.pairingEnabled = pairingEnabled;
        }

        @Override
        public String toString() {
            return name + " · " + host + ":" + port + " · v" + version;
        }
    }

    private DiscoveryClient() { }

    static List<Server> discover(int discoveryPort, int timeoutMs) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        ArrayList<Server> result = new ArrayList<Server>();
        HashSet<String> seen = new HashSet<String>();
        try {
            socket.setBroadcast(true);
            socket.setSoTimeout(350);
            DatagramPacket outbound = new DatagramPacket(
                    REQUEST, REQUEST.length,
                    InetAddress.getByName("255.255.255.255"), discoveryPort);
            socket.send(outbound);
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                byte[] buffer = new byte[2048];
                DatagramPacket inbound = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(inbound);
                } catch (SocketTimeoutException ignored) {
                    continue;
                }
                try {
                    String host = inbound.getAddress().getHostAddress();
                    String body = new String(inbound.getData(), 0, inbound.getLength(), "UTF-8");
                    JSONObject payload = new JSONObject(body);
                    if (!"PocketBridge".equals(payload.optString("service", ""))) {
                        continue;
                    }
                    int port = payload.optInt("http_port", 8765);
                    if (port < 1 || port > 65535) {
                        continue;
                    }
                    String key = host + ":" + port;
                    if (seen.add(key)) {
                        String name = payload.optString("name", host);
                        String version = payload.optString("server_version", "?");
                        if (name.length() > 64) {
                            name = name.substring(0, 64);
                        }
                        if (version.length() > 32) {
                            version = version.substring(0, 32);
                        }
                        result.add(new Server(
                                name,
                                host,
                                port,
                                version,
                                payload.optBoolean("pairing_enabled", false)));
                    }
                } catch (Exception ignored) {
                    // Ignore malformed or unrelated UDP replies and keep scanning.
                }
            }
            return result;
        } finally {
            socket.close();
        }
    }
}
