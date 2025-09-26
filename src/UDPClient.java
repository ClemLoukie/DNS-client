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

        // step 0: variable initiation TO BE MOVED
        long timer = System.nanoTime();
        timer -= System.nanoTime();

        int num_retries = 0;
        // TO THE SEND AREA

        // step 1: receive response from server into a datagram packet
        DatagramPacket p = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(p);

        // step 2: summarize query that has been sent (data on our side)
        System.out.println("DnsClient sending request for " + sServerName); // [name]
        System.out.println("Server: " + sIP); // [server IP]
        System.out.println("Request type: " + sQueryType); // Qtype - [A | MX | NS]

        // step 3: redirect to STDOUT
        // I will establish a timer once the sending is set
        System.out.println("Response received after "+ timer+" seconds (" + num_retries + " retries)");

        // step 4: print
        int ANCOUNT = ((receiveData[6] & 0xFF) << 8) | (receiveData[7] & 0xFF); // chat
        if (ANCOUNT >= 1) {
            System.out.println("***Answer Section (" + ANCOUNT + " records)***");
            // We must reach the records section
            byte[] receiveRecords = Arrays.copyOfRange(receiveData, 12, receiveData.length);
            int index = 0;
            while (receiveRecords[index] != 0) index++;
            index += 4 + 1; // account for QTYPE and QCLASS + start of first record
            for (int x = 0; x < ANCOUNT; x++) {
                // We must move past the name and ACCOUNT FOR COMPRESSION
                /***
                 * • a sequence of labels ending with a zero octet;
                 * • a pointer; -> starts with 11xxxxxx (top 2 bits
                 * • a sequence of labels ending with a pointer.
                 */
                if ((receiveRecords[index] >>>6)  == 3) index += 2;
                else{
                    while (receiveRecords[index] != 0 || (receiveRecords[index] >>>6)  != 3)){
                        index++;
                    }
                    if (receiveRecords[index] == 0) index++;
                    else index +=2;
                }

                int type = ((receiveRecords[index++] & 0xFF) << 8) | (receiveRecords[index++] & 0xFF); // because 16 bit
                long seconds_cache;
                String auth_status;
                switch (type) {
                    case 1:
                        /***
                         * If TYPE is 0x0001, for an A (IP address) record,
                         * then TTL (index +4)
                         * then RDATA (index+8) is the IP address (four octets).
                         */
                        index += 2;
                        seconds_cache = (((long) (receiveRecords[index++] & 0xFF) << 24) | ((long) (receiveRecords[index++] & 0xFF) << 16) | ((long) (receiveRecords[index++] & 0xFF) << 8) | ((receiveRecords[index++] & 0xFF))); // four octets

                        index += 2;
                        int[] ip_adress = new int[4];
                        ip_adress[0] = receiveRecords[index] & 0xFF;
                        ip_adress[1] = receiveRecords[++index] & 0xFF;
                        ip_adress[2] = receiveRecords[++index] & 0xFF;
                        ip_adress[3] = receiveRecords[++index] & 0xFF;

                        if ((receiveRecords[2] & 0x04) == 0x04) auth_status = "auth" ;
                        else auth_status = "nonauth"; // contained in header as 3 LSbit in second byte

                        System.out.println("IP\t "+ ip_adress[0] + "." + ip_adress[1] + "." + ip_adress[2] + "." + ip_adress[3] + " \t "+ seconds_cache +" \t " + auth_status);
                        break;
                        // then exit strategy
                    case 2:
                        /***
                         * If the TYPE is 0x0002, for a NS (name server) record,
                         * then this is the name of the server specified using the same format as the QNAME field.
                         */
                        index += 2;
                        seconds_cache = (((long) (receiveRecords[index++] & 0xFF) << 24) | ((long) (receiveRecords[index++] & 0xFF) << 16) | ((long) (receiveRecords[index++] & 0xFF) << 8) | ((receiveRecords[index++] & 0xFF))); // four octets

                        if ((receiveData[2] & 0x04) == 0x04) auth_status = "auth" ;
                        else auth_status = "nonauth"; // contained in header as 3 LSbit in second byte

                        int pref = ((receiveRecords[index++] & 0xFF) << 8) | (receiveRecords[index] & 0xFF);
                        System.out.println("MX \t [alias] \t " + pref + " \t "+ seconds_cache +" \t "+ auth_status);
                        break;
                    case 5:
                        /***
                         * If the TYPE
                         * is 0x005, for CNAME records, then this is the name of the alias.
                         */
                    case 15:
                        /***
                         * If the type is 0x000f for MX (mail
                         * server) records, then RDATA has the format
                         */
                        System.out.println("NS \t [alias] \t [seconds can cache] \t [auth | nonauth]");
                        break;
                }
            }
        }

        // step 5: print additional records
        int ARCOUNT = ((receiveData[10] & 0xFF) << 8) | (receiveData[11] & 0xFF); // chat
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

