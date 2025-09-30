import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class UDPClientOutputBehaviourTest {

    // ========= PUBLIC TESTS =========

    @Test
    void A_singleAnswer_printsOneIP() throws Exception {
        byte[] pkt = buildAResponse("one.example", new int[][]{{203,0,113,10}}, true, 0xA001, 60);

        String out = runOnce(pkt, "A", "127.0.0.1", "one.example");
        assertTrue(out.contains("***Answer Section (1 record)***"), "Should report 1 record in Answer section");
        assertEquals(1, countOccurrences(out, "\nIP\t "), "Should print exactly one IP line");
        assertTrue(out.contains("203.0.113.10"), "Should contain the A address");
    }

    @Test
    void A_twoAnswers_printsTwoIPs() throws Exception {
        byte[] pkt = buildAResponse("www.mcgill.ca",
                new int[][]{{132,216,177,160}, {132,216,177,161}},
                true, 0xA002, 120);

        String out = runOnce(pkt, "A", "127.0.0.1", "www.mcgill.ca");
        assertTrue(out.contains("***Answer Section (2 records)***"), "Should report 2 records in Answer section");
        assertEquals(2, countOccurrences(out, "\nIP\t "), "Should print two IP lines");
        assertTrue(out.contains("132.216.177.160"));
        assertTrue(out.contains("132.216.177.161"));
    }

    @Test
    void NS_answer_printsNSLine() throws Exception {
        byte[] pkt = buildNSResponse("example.com", "ns1.example.com", true, 0xB001, 300);

        String out = runOnce(pkt, "A", "127.0.0.1", "example.com");
        assertTrue(out.contains("***Answer Section (1 record)***"), "Should report 1 record");
        assertTrue(out.contains("\nNS \t "), "Should print an NS line (alias may be TBD until you decode)");
    }

    @Test
    void CNAME_answer_printsCNAMELine() throws Exception {
        byte[] pkt = buildCNAMEResponse("www.alias.test", "real.test", "203.0.113.200", true, 0xC001, 60);

        String out = runOnce(pkt, "A", "127.0.0.1", "www.alias.test");
        assertTrue(out.contains("***Answer Section (1 record)***"), "Should report 1 record");
        assertTrue(out.contains("\nCNAME \t "), "Should print a CNAME line (alias may be TBD until you decode)");
    }

    @Test
    void CNAME_additionalA_shouldPrintTargetIP() throws Exception {
        byte[] pkt = buildCNAMEResponse("www.alias.test", "real.test", "203.0.113.200", true, 0xC002, 60);

        String out = runOnce(pkt, "A", "127.0.0.1", "www.alias.test");
        // Expect the additional A for the canonical name to appear
        assertTrue(out.contains("203.0.113.200"),
                "Expected additional A record for canonical target to be printed (ARCOUNT parsing required)");
    }

    @Test
    void MX_answer_printsMXLine() throws Exception {
        byte[] pkt = buildMXResponse("example.org", 10, "mail.example.org", "203.0.113.25", true, 0xD001, 180);

        String out = runOnce(pkt, "A", "127.0.0.1", "example.org");
        assertTrue(out.contains("***Answer Section (1 record)***"), "Should report 1 record");
        assertTrue(out.contains("\nMX \t "), "Should print an MX line (preference may be wrong until you fix parsing)");
    }

    @Test
    void MX_additionalA_shouldPrintExchangeIP() throws Exception {
        byte[] pkt = buildMXResponse("example.org", 10, "mail.example.org", "203.0.113.25", true, 0xD002, 180);

        String out = runOnce(pkt, "A", "127.0.0.1", "example.org");
        // Expect the additional A for the exchange host to appear
        assertTrue(out.contains("203.0.113.25"),
                "Expected additional A record for MX exchange to be printed (ARCOUNT parsing required)");
    }

    // ========= HARNESS (send packet -> call your OutputBehaviour and capture stdout) =========

    private static String runOnce(byte[] dnsResponse, String sQueryType, String sIP, String sServerName) throws Exception {
        DatagramSocket recvSocket = new DatagramSocket();
        recvSocket.setSoTimeout(2000);
        int recvPort = recvSocket.getLocalPort();

        CountDownLatch sent = new CountDownLatch(1);

        Thread sender = new Thread(() -> {
            try (DatagramSocket s = new DatagramSocket()) {
                DatagramPacket pkt = new DatagramPacket(dnsResponse, dnsResponse.length,
                        InetAddress.getLoopbackAddress(), recvPort);
                // brief pause to ensure the receiver is waiting
                Thread.sleep(20);
                s.send(pkt);
                sent.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        sender.start();

        // capture stdout
        PrintStream orig = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));

        try {
            byte[] recvBuf = new byte[2048];
            // Call your function; it blocks until it receives
            UDPClient.OutputBehaviour(recvBuf, recvSocket, sQueryType, sIP, sServerName);
        } finally {
            System.setOut(orig);
            try { recvSocket.close(); } catch (Exception ignore) {}
            sent.await(300, TimeUnit.MILLISECONDS);
        }

        return baos.toString(StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String haystack, String needle) {
        int idx = 0, count = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    // ========= PACKET BUILDERS (A / NS / CNAME(+AR) / MX(+AR)) =========

    private static byte[] buildAResponse(String qname, int[][] ipAddrs, boolean nameCompressed, int id, int ttlSeconds) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write16(out, id & 0xFFFF);
        write16(out, 0x8180);                 // QR=1, RD=1, RA=1, RCODE=0
        write16(out, 1);                      // QDCOUNT
        write16(out, ipAddrs.length);         // ANCOUNT
        write16(out, 0);                      // NSCOUNT
        write16(out, 0);                      // ARCOUNT

        byte[] qnameBytes = encodeName(qname);
        out.write(qnameBytes); write16(out, 1); write16(out, 1); // QTYPE=A, QCLASS=IN

        for (int i = 0; i < ipAddrs.length; i++) {
            if (nameCompressed) { out.write(0xC0); out.write(0x0C); } else out.write(qnameBytes);
            write16(out, 1); write16(out, 1); // TYPE=A, CLASS=IN
            write32(out, ttlSeconds);
            write16(out, 4);
            int[] ip = ipAddrs[i];
            out.write(ip[0] & 0xFF); out.write(ip[1] & 0xFF); out.write(ip[2] & 0xFF); out.write(ip[3] & 0xFF);
        }
        return out.toByteArray();
    }

    private static byte[] buildNSResponse(String qname, String nsHost, boolean nameCompressed, int id, int ttlSeconds) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write16(out, id & 0xFFFF);
        write16(out, 0x8180);
        write16(out, 1); // QD
        write16(out, 1); // AN (1 NS)
        write16(out, 0); // NS
        write16(out, 0); // AR

        byte[] qnameBytes = encodeName(qname);
        byte[] nsBytes    = encodeName(nsHost);
        out.write(qnameBytes); write16(out, 1); write16(out, 1);

        if (nameCompressed) { out.write(0xC0); out.write(0x0C); } else out.write(qnameBytes);
        write16(out, 2); write16(out, 1); // NS, IN
        write32(out, ttlSeconds);
        write16(out, nsBytes.length);
        out.write(nsBytes);
        return out.toByteArray();
    }

    private static byte[] buildCNAMEResponse(String alias, String canonical, String canonicalIPv4,
                                             boolean nameCompressed, int id, int ttlSeconds) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write16(out, id & 0xFFFF);
        write16(out, 0x8180);
        write16(out, 1); // QD
        write16(out, 1); // AN (CNAME)
        write16(out, 0); // NS
        write16(out, 1); // AR (A for canonical)

        byte[] aliasBytes = encodeName(alias);
        byte[] canonBytes = encodeName(canonical);

        out.write(aliasBytes); write16(out, 1); write16(out, 1);

        if (nameCompressed) { out.write(0xC0); out.write(0x0C); } else out.write(aliasBytes);
        write16(out, 5); write16(out, 1); // CNAME, IN
        write32(out, ttlSeconds);
        write16(out, canonBytes.length);
        out.write(canonBytes);

        // Additional A for canonical
        out.write(canonBytes);
        write16(out, 1); write16(out, 1);
        write32(out, ttlSeconds);
        write16(out, 4);
        int[] ip = parseIPv4(canonicalIPv4);
        out.write(ip[0]); out.write(ip[1]); out.write(ip[2]); out.write(ip[3]);

        return out.toByteArray();
    }

    private static byte[] buildMXResponse(String qname, int pref, String exchangeHost,
                                          String exchangeIPv4, boolean nameCompressed, int id, int ttlSeconds) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write16(out, id & 0xFFFF);
        write16(out, 0x8180);
        write16(out, 1); // QD
        write16(out, 1); // AN (MX)
        write16(out, 0); // NS
        write16(out, 1); // AR (A for exchange)

        byte[] qnameBytes = encodeName(qname);
        byte[] exchBytes  = encodeName(exchangeHost);

        out.write(qnameBytes); write16(out, 1); write16(out, 1);

        if (nameCompressed) { out.write(0xC0); out.write(0x0C); } else out.write(qnameBytes);
        write16(out, 15); write16(out, 1); // MX, IN
        write32(out, ttlSeconds);

        int rdataLen = 2 + exchBytes.length;
        write16(out, rdataLen);
        write16(out, pref & 0xFFFF);   // preference
        out.write(exchBytes);          // exchange name

        // Additional A for exchange
        out.write(exchBytes);
        write16(out, 1); write16(out, 1);
        write32(out, ttlSeconds);
        write16(out, 4);
        int[] ip = parseIPv4(exchangeIPv4);
        out.write(ip[0]); out.write(ip[1]); out.write(ip[2]); out.write(ip[3]);

        return out.toByteArray();
    }

    // ========= BYTES UTILS =========

    private static byte[] encodeName(String name) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (String label : name.split("\\.")) {
            byte[] lab = label.getBytes(StandardCharsets.US_ASCII);
            if (lab.length > 63) throw new IllegalArgumentException("Label too long: " + label);
            b.write(lab.length);
            b.write(lab);
        }
        b.write(0x00);
        return b.toByteArray();
    }

    private static int[] parseIPv4(String ip) {
        String[] p = ip.split("\\.");
        return new int[]{
                Integer.parseInt(p[0]) & 0xFF,
                Integer.parseInt(p[1]) & 0xFF,
                Integer.parseInt(p[2]) & 0xFF,
                Integer.parseInt(p[3]) & 0xFF
        };
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
