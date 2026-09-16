package com.enterprise.tunnel.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * VLESS v0 协议纯 Java 编解码器
 */
public class VlessCodec {

    public static class TargetDestination {
        public String host;
        public int port;
        public byte[] payload;
    }

    public static byte[] uuidToBytes(String uuidStr) {
        UUID uuid = UUID.fromString(uuidStr.trim());
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    public static TargetDestination parseRequest(byte[] data, byte[] expectedUuidBytes) throws Exception {
        if (data.length < 18) {
            throw new IllegalArgumentException("VLESS packet too short: " + data.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);

        // 1. Version (1 byte)
        byte version = buffer.get();
        if (version != 0) {
            throw new IllegalArgumentException("Unsupported VLESS version: " + version);
        }

        // 2. UUID (16 bytes)
        byte[] clientUuid = new byte[16];
        buffer.get(clientUuid);
        if (!Arrays.equals(clientUuid, expectedUuidBytes)) {
            throw new SecurityException("UUID authentication failed");
        }

        // 3. Addon length (1 byte)
        int addonLength = buffer.get() & 0xFF;
        if (addonLength > 0) {
            buffer.position(buffer.position() + addonLength);
        }

        // 4. Command (1 byte): 0x01 = TCP, 0x02 = UDP
        byte command = buffer.get();
        if (command != 1 && command != 2) {
            throw new IllegalArgumentException("Unsupported command: " + command);
        }

        // 5. Target Port (2 bytes, Big-Endian)
        int port = buffer.getShort() & 0xFFFF;

        // 6. Address Type (1 byte): 0x01 = IPv4, 0x02 = Domain, 0x03 = IPv6
        byte addrType = buffer.get();
        String host;

        if (addrType == 1) { // IPv4 (4 bytes)
            byte[] ipBytes = new byte[4];
            buffer.get(ipBytes);
            host = (ipBytes[0] & 0xFF) + "." + (ipBytes[1] & 0xFF) + "." + (ipBytes[2] & 0xFF) + "." + (ipBytes[3] & 0xFF);
        } else if (addrType == 2) { // Domain (1 byte length + domain bytes)
            int domainLen = buffer.get() & 0xFF;
            byte[] domainBytes = new byte[domainLen];
            buffer.get(domainBytes);
            host = new String(domainBytes, StandardCharsets.UTF_8);
        } else if (addrType == 3) { // IPv6 (16 bytes)
            byte[] ipv6Bytes = new byte[16];
            buffer.get(ipv6Bytes);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i += 2) {
                if (i > 0) sb.append(":");
                int val = ((ipv6Bytes[i] & 0xFF) << 8) | (ipv6Bytes[i + 1] & 0xFF);
                sb.append(Integer.toHexString(val));
            }
            host = sb.toString();
        } else {
            throw new IllegalArgumentException("Unknown address type: " + addrType);
        }

        // 7. Extract remaining initial payload
        int remaining = buffer.remaining();
        byte[] payload = null;
        if (remaining > 0) {
            payload = new byte[remaining];
            buffer.get(payload);
        }

        TargetDestination dest = new TargetDestination();
        dest.host = host;
        dest.port = port;
        dest.payload = payload;
        return dest;
    }

    public static byte[] createResponseHeader() {
        return new byte[]{0x00, 0x00}; // Version 0, 0 Addons
    }
}
