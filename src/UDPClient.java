import java.io.*;
import java.net.*;
import java.util.*;

public class UDPClient {
    public static void main(String[] args) throws IOException {

        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in)); // open stream to read client's input
        DatagramSocket clientSocket = new DatagramSocket(); // establish socket

        byte[] sendData = new byte[1024];
        byte[] receiveData = new byte[1024];

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
}

