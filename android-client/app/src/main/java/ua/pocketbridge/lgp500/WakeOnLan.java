package ua.pocketbridge.lgp500;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Wake-on-LAN sender hardened for old Android Wi-Fi stacks and home routers. */
final class WakeOnLan {
    static final class Result {
        final int packetsSent;
        final String destinations;

        Result(int packetsSent, String destinations) {
            this.packetsSent = packetsSent;
            this.destinations = destinations == null ? "" : destinations;
        }

        String summary() {
            return "Надіслано " + packetsSent + " Magic Packet: " + destinations;
        }
    }

    private WakeOnLan() { }

    static Result send(Context context, String macAddress, String broadcastAddress, int port) throws Exception {
        byte[] magicPacket = buildMagicPacket(macAddress);
        Set<String> destinationSet = new LinkedHashSet<String>();
        addDestination(destinationSet, broadcastAddress);
        addDestination(destinationSet, dhcpBroadcast(context));
        addDestination(destinationSet, "255.255.255.255");
        if (destinationSet.isEmpty()) {
            throw new IllegalArgumentException("Не вдалося визначити broadcast-адресу");
        }

        List<Integer> ports = new ArrayList<Integer>();
        addPort(ports, port);
        addPort(ports, 9);
        addPort(ports, 7);

        DatagramSocket socket = new DatagramSocket();
        int sent = 0;
        StringBuilder used = new StringBuilder();
        try {
            socket.setBroadcast(true);
            for (String destination : destinationSet) {
                InetAddress address = InetAddress.getByName(destination);
                if (used.length() > 0) used.append(", ");
                used.append(destination);
                for (int repeat = 0; repeat < 4; repeat++) {
                    for (int index = 0; index < ports.size(); index++) {
                        int currentPort = ports.get(index).intValue();
                        DatagramPacket datagram = new DatagramPacket(
                                magicPacket, magicPacket.length, address, currentPort);
                        socket.send(datagram);
                        sent += 1;
                    }
                    if (repeat < 3) {
                        try { Thread.sleep(65L); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new Exception("Надсилання Wake-on-LAN перервано");
                        }
                    }
                }
            }
        } finally {
            socket.close();
        }
        return new Result(sent, used.toString());
    }

    /** Backward-compatible overload for any older call sites. */
    static Result send(String macAddress, String broadcastAddress, int port) throws Exception {
        return send(null, macAddress, broadcastAddress, port);
    }

    private static byte[] buildMagicPacket(String macAddress) {
        String cleaned = macAddress == null ? ""
                : macAddress.replace(":", "").replace("-", "").replace(".", "").trim();
        if (!cleaned.matches("(?i)[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Некоректна MAC-адреса");
        }
        byte[] mac = new byte[6];
        for (int i = 0; i < 6; i++) {
            mac[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        byte[] packet = new byte[6 + 16 * mac.length];
        for (int i = 0; i < 6; i++) packet[i] = (byte) 0xff;
        for (int i = 6; i < packet.length; i += mac.length) {
            System.arraycopy(mac, 0, packet, i, mac.length);
        }
        return packet;
    }

    private static void addPort(List<Integer> ports, int port) {
        if (port < 1 || port > 65535) return;
        Integer value = Integer.valueOf(port);
        if (!ports.contains(value)) ports.add(value);
    }

    private static void addDestination(Set<String> destinations, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (isIpv4(value)) destinations.add(value);
    }

    private static boolean isIpv4(String value) {
        if (value == null || value.length() < 7 || value.length() > 15) return false;
        String[] parts = value.split("\\.");
        if (parts.length != 4) return false;
        for (int i = 0; i < parts.length; i++) {
            try {
                int part = Integer.parseInt(parts[i]);
                if (part < 0 || part > 255) return false;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    private static String dhcpBroadcast(Context context) {
        if (context == null) return "";
        try {
            WifiManager manager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            DhcpInfo info = manager == null ? null : manager.getDhcpInfo();
            if (info == null || info.ipAddress == 0 || info.netmask == 0) return "";
            int broadcast = (info.ipAddress & info.netmask) | ~info.netmask;
            byte[] quads = new byte[4];
            for (int i = 0; i < 4; i++) {
                quads[i] = (byte) ((broadcast >> (i * 8)) & 0xff);
            }
            return InetAddress.getByAddress(quads).getHostAddress();
        } catch (Exception ignored) {
            return "";
        }
    }
}
