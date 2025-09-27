import java.net.*;
import java.io.*;
import java.util.*;

public class DNSOutputSuite {

    public static void main(String[] args) throws Exception {
        DatagramSocket recvSocket = new DatagramSocket(); // receiver your OutputBehaviour uses
        int recvPort = recvSocket.getLocalPort();

        // === Test cases ===
        List<byte[]> cases = new ArrayList<>();

        // 1) www.mcgill.ca -> 132.216.177.160, 132.216.177.161 (both answers compressed)
        cases.add(buildAResponse("www.mcgill.ca",
                new int[][] { {132,216,177,160}, {132,216,177,161} },
                NameEncoding.COMPRESSED_ALL, 0x1234, 120));

        // 2) www.example.com -> 93.184.216.34, 93.184.216.35 (first answer full name, second compressed)
        cases.add(buildAResponse("www.example.com",
                new int[][] { {93,184,216,34}, {93,184,216,35} },
                NameEncoding.MIXED, 0x2233, 30));

        // 3) api.test.local -> 10.0.0.10, 10.0.0.11, 10.0.0.12 (no compression anywhere)
        cases.add(buildAResponse("api.test.local",
                new int[][] { {10,0,0,10}, {10,0,0,11}, {10,0,0,12} },
                NameEncoding.NO_COMPRESSION, 0x3344, 5));

        // Fire each response and invoke your output function
        String sQueryType = "A";
        for (int i = 0; i < cases.size(); i++) {
            byte[] dnsResponse = cases.get(i);
            final int idx = i;

            Thread sender = new Thread(() -> {
                try (DatagramSocket s = new DatagramSocket()) {
                    DatagramPacket pkt = new DatagramPacket(
                            dnsResponse, dnsResponse.length,
                            InetAddress.getByName("127.0.0.1"), recvPort
                    );
                    Thread.sleep(50);
                    s.send(pkt);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            sender.start();

            // Prepare buffer and labels for OutputBehaviour
            byte[] receiveData = new byte[1024];

            // Infer name & server/IP to print
            String name = switch (i) {
                case 0 -> "www.mcgill.ca";
                case 1 -> "www.example.com";
                default -> "api.test.local";
            };
            String sIP = "127.0.0.1"; // just for the header print
            UDPClient.OutputBehaviour(receiveData, recvSocket, sQueryType, sIP, name);
        }

        recvSocket.close();
    }

    enum NameEncoding { COMPRESSED_ALL, MIXED, NO_COMPRESSION }

    private static byte[] buildAResponse(String qname, int[][] ipAddrs, NameEncoding enc, int id, int ttlSeconds) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // --- Header (12 bytes) ---
        // ID
        write16(out, id & 0xFFFF);
        // Flags: QR=1 (response), OPCODE=0, AA=0, TC=0, RD=1, RA=1, Z=0, RCODE=0 -> 0x8180
        write16(out, 0x8180);
        // QDCOUNT=1
        write16(out, 1);
        // ANCOUNT = number of A RRs
        write16(out, ipAddrs.length);
        // NSCOUNT=0, ARCOUNT=0
        write16(out, 0);
        write16(out, 0);

        // Remember position 0x0C as base of QNAME for compression
        int qnameOffset = 12;

        // --- Question ---
        byte[] qnameBytes = encodeName(qname);
        out.write(qnameBytes);
        write16(out, 1); // QTYPE = A
        write16(out, 1); // QCLASS = IN

        // --- Answers ---
        for (int i = 0; i < ipAddrs.length; i++) {
            // NAME
            switch (enc) {
                case COMPRESSED_ALL -> { // pointer to QNAME at 0x0C
                    out.write(0xC0);
                    out.write(0x0C);
                }
                case NO_COMPRESSION -> {
                    out.write(qnameBytes);
                }
                case MIXED -> {
                    if (i == 0) {
                        out.write(qnameBytes); // first full
                    } else {
                        out.write(0xC0);
                        out.write(0x0C);      // then compressed
                    }
                }
            }

            // TYPE=A, CLASS=IN
            write16(out, 1);
            write16(out, 1);

            // TTL
            write32(out, ttlSeconds);

            // RDLENGTH=4
            write16(out, 4);

            // RDATA=IPv4
            int[] ip = ipAddrs[i];
            out.write(ip[0] & 0xFF);
            out.write(ip[1] & 0xFF);
            out.write(ip[2] & 0xFF);
            out.write(ip[3] & 0xFF);
        }

        return out.toByteArray();
    }

    private static byte[] encodeName(String name) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (String label : name.split("\\.")) {
            byte[] lab = label.getBytes("US-ASCII");
            if (lab.length > 63) throw new IllegalArgumentException("Label too long: " + label);
            b.write(lab.length);
            b.write(lab);
        }
        b.write(0x00); // terminator
        return b.toByteArray();
    }

    private static void write16(OutputStream out, int v) throws IOException {
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private static void write32(OutputStream out, int v) throws IOException {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }
}
