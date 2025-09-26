import java.io.*;
import java.net.*;
import java.util.*;

public class UDPClient {
    public static void main(String[] args) throws IOException {

        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in)); // open stream to read client's input
        DatagramSocket clientSocket = new DatagramSocket(); // establish socket - like mailbox

        byte[] sendData = new byte[1024];
        byte[] receiveData = new byte[1024]; // where we recieve the response

        //Parse Arguments Implementation

        String[] sArgs = args[0].split("\\s+"); // splitting the params with spacing
        String sIp = sArgs[2]; // hard coded for now I just want to get this going
        String sQueryType = sArgs[3];
        String sServerName = sArgs[4];

        String[] ipSplit = sIp.substring(1).split("\\."); // split with commas and remove @
        byte[] ipByteArray = new byte[ipSplit.length]; // iPV4 is always 32 bits so we'll need 4 bytes
        for (int i = 0; i < ipSplit.length; i++)
        {
            ipByteArray[i] = (byte) Integer.parseInt(ipSplit[i]);
        }
        InetAddress ipAddress = InetAddress.getByAddress(ipByteArray);

        // End of parsing args implementation note annabelle je devrais move ca dans une methode separee

        // Build header
        byte[] header = new byte[12];

        Random random = new Random();
        int randomID = random.nextInt(0xFFFF); // 16 bits generated num
        header[0] = (byte) ((randomID >> 8) & 0xFF);
        header[1] = (byte) (randomID & 0xFF);

        //gonna hard code this for now just to test if it works, but tmrw I will change to byte buffer because this is ridiculous LOL
        header[0] = header[1] = header[3] = header[4] = header[6] = header[7] = header[8] = header[9] = header[10] = header[11] = 0x00; // deepest apologies I will change that tmrw but now I am DEAD
        header[2] = header[5] = 0x01; // rd and qdcount are 1

        clientSocket.close();

    }

    public static void OutputBehaviour(byte[] receiveData, DatagramSocket clientSocket, String sQueryType, String sIP, String sServerName) throws IOException {

        // step 1: receive response from server into a datagram packet
        DatagramPacket p = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(p);

        // step 2: summarize query that has been sent (data on our side)
        System.out.println("DnsClient sending request for " + sServerName); // [name]
        System.out.println("Server: " + sIP); // [server IP]
        System.out.println("Request type: " + sQueryType); // Qtype - [A | MX | NS]

        // step 3: redirect to STDOUT
        // I will establish a timer once the sending is set
        System.out.println("Response received after [time] seconds ([num-retries] retries)");

        // step 4: print
        int ANCOUNT = ((receiveData[6] & 0xFF) << 8) | (receiveData[7] & 0xFF);
        if (ANCOUNT >= 1) {
            System.out.println("***Answer Section (" + ANCOUNT + " records)***");
            for (int x = 0; x < ANCOUNT; x++) {
                int type = p.getOffset();
                switch (type) {
                    case 1:
                        System.out.println("IP\t [ip address] \t [seconds can cache] \t [auth | nonauth]");
                        break;
                    case 2:
                        System.out.println("MX \t [alias] \t [pref] \t [seconds can cache] \t [auth | nonauth]");
                        break;
                    case 15:
                        System.out.println("NS \t [alias] \t [seconds can cache] \t [auth | nonauth]");
                        break;
                }
            }
        }

        // step 5: print additional records
        int ARCOUNT = ((receiveData[10] & 0xFF) << 8) | (receiveData[11] & 0xFF);
        if (ARCOUNT >= 1) {
            System.out.println("***Additional Section (" + ARCOUNT + " records)***");
            for (int x = 0; x < ARCOUNT; x++) {

            }
        }

        if (ANCOUNT+ARCOUNT == 0){ // or RCODE ==3
            System.out.println("NOTFOUND");
        }

        //step 6: Error handling
    }


}

