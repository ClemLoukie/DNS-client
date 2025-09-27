import java.net.*;
import java.io.*;

public class DNSOutputTest {
    public static void main(String[] args) throws Exception {
        // 1) Craft a real DNS response (A record for www.mcgill.ca) from your lab handout
        // Hex dump:
        // 82 7a 81 00 00 01 00 01 00 00 00 00
        // 03 77 77 77 06 6d 63 67 69 6c 6c 02 63 61 00 00 01 00 01
        // c0 0c 00 01 00 01 00 00 04 13 00 04 84 d8 b1 a0
        byte[] dnsResponse = new byte[] {
                (byte)0x82,(byte)0x7a,(byte)0x81,(byte)0x00,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
                (byte)0x03,(byte)0x77,(byte)0x77,(byte)0x77,(byte)0x06,(byte)0x6d,(byte)0x63,(byte)0x67,(byte)0x69,(byte)0x6c,(byte)0x6c,(byte)0x02,(byte)0x63,(byte)0x61,(byte)0x00,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x01,
                (byte)0xc0,(byte)0x0c,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x00,(byte)0x04,(byte)0x13,(byte)0x00,(byte)0x04,(byte)0x84,(byte)0xd8,(byte)0xb1,(byte)0xa0
        };

        // 2) Create the receiving socket your OutputBehaviour will read from
        DatagramSocket recvSocket = new DatagramSocket();             // binds to an ephemeral local port
        int recvPort = recvSocket.getLocalPort();

        // 3) Fire the crafted response to that port from a sender thread
        Thread sender = new Thread(() -> {
            try (DatagramSocket s = new DatagramSocket()) {
                DatagramPacket pkt = new DatagramPacket(
                        dnsResponse, dnsResponse.length,
                        InetAddress.getByName("127.0.0.1"), recvPort
                );
                // small pause to ensure the receiver is waiting (optional)
                Thread.sleep(50);
                s.send(pkt);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        sender.start();

        // 4) Prepare the receive buffer and call your output function
        byte[] receiveData = new byte[1024];
        // These are just the strings your function prints in its header
        String sQueryType = "A";
        String sIP        = "127.0.0.1";
        String sServerName= "www.mcgill.ca";

        // Call your method; it will block until the sender delivers the packet
        UDPClient.OutputBehaviour(receiveData, recvSocket, sQueryType, sIP, sServerName);

        recvSocket.close();
    }
}
