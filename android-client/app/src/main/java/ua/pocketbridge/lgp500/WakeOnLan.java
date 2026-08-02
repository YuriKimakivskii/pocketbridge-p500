package ua.pocketbridge.lgp500;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

final class WakeOnLan {
    private WakeOnLan() { }

    static void send(String macAddress, String broadcastAddress, int port) throws Exception {
        String cleaned = macAddress == null ? "" : macAddress.replace(":", "").replace("-", "").trim();
        if (!cleaned.matches("(?i)[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Некоректна MAC-адреса");
        }
        byte[] mac = new byte[6];
        for (int i = 0; i < 6; i++) {
            mac[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        byte[] packet = new byte[6 + 16 * mac.length];
        for (int i = 0; i < 6; i++) {
            packet[i] = (byte) 0xff;
        }
        for (int i = 6; i < packet.length; i += mac.length) {
            System.arraycopy(mac, 0, packet, i, mac.length);
        }
        String destination = broadcastAddress == null || broadcastAddress.trim().length() == 0
                ? "255.255.255.255" : broadcastAddress.trim();
        DatagramSocket socket = new DatagramSocket();
        try {
            socket.setBroadcast(true);
            DatagramPacket datagram = new DatagramPacket(
                    packet, packet.length, InetAddress.getByName(destination), port);
            socket.send(datagram);
        } finally {
            socket.close();
        }
    }
}
