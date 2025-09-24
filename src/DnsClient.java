import java.io.*;
import java.net.*;
import java.util.*;

public class DnsClient
{
    public static void main(String[] args) throws IOException
    {

        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in)); // open stream to read client's input
        DatagramSocket clientSocket = new DatagramSocket(); // establish socket

        byte[] sendData = new byte[1024];
        byte[] receiveData = new byte[1024];

        //Parse Arguments Implementation

        String sIp = args[0]; // hard coded for now I just want to get this going
        String sQueryType = args[1];
        String sServerName = args[2];

        String[] ipSplit = sIp.substring(1).split("\\."); // split with commas and remove @
        byte[] ipByteArray = new byte[ipSplit.length]; // iPV4 is always 32 bits so we'll need 4 bytes
        for (int i = 0; i < ipSplit.length; i++)
        {
            ipByteArray[i] = (byte) Integer.parseInt(ipSplit[i]);
        }
        InetAddress ipAddress = InetAddress.getByAddress(ipByteArray);

        // End of parsing args implementation note annabelle je devrais move ca dans une methode separee

        byte[] header = BuildHeader();

        // Beginning DNS Questions
        byte[] questionBytes = BuildDNSQuestion(sServerName, sQueryType);

        // header + question
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        outputStream.write(header);
        outputStream.write(questionBytes);

        sendData = outputStream.toByteArray();

        // Send the data
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, 53);
        clientSocket.send(sendPacket);
        System.out.printf("DnsClient sending request for %s\nServer: %s\nRequest Type: %s\n", sServerName, sIp, sQueryType);

        //Receive Data
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(receivePacket);

        // Interpret Data
        ParseAnswer(receiveData);

        //PRINTING RAW RESPONSE FOR TESTING PURPOSES
        int length = receivePacket.getLength();
        System.out.println("Received " + length + " bytes");
        for (int i = 0; i < length; i++) {
            System.out.printf("%02X ", receiveData[i]);
            if ((i + 1) % 16 == 0) System.out.println();
        }

        clientSocket.close();
    }

    public static byte[] BuildHeader()
    {
        byte[] header = new byte[12]; // note I could use a bytebuffer instead clem what do you think

        Random random = new Random();
        int randomID = random.nextInt(0xFFFF); // 16 bits generated num
        header[0] = (byte) ((randomID >> 8) & 0xFF);
        header[1] = (byte) (randomID & 0xFF);

        // Flags: 0x0100 → recursion desired
        header[2] = 0x01;
        header[3] = 0x00;

        header[4] = 0x00; // qdcount
        header[5] = 0x01;

        header[6] = 0x00; // an count
        header[7] = 0x00;

        header[8] = 0x00; // ns count
        header[9] = 0x00;

        header[10] = 0x00; // ar count
        header[11] = 0x00;

        return header;
    }

    public static byte[] BuildDNSQuestion(String serverName, String queryType) throws IOException {
        String[] serverNameParts = serverName.split("\\.");
        ByteArrayOutputStream question = new ByteArrayOutputStream();

        // Q_NAME
        for (String part: serverNameParts)
        {
            byte len = (byte) part.length();
            question.write(len); // length byte
            question.write(part.getBytes()); // content byte
        }
        question.write(0x00); // end of name translation

        // Q_TYPE
        if (queryType.equals("-ns")) // name server
        {
            question.write(0x00);
            question.write(0x02);
        }
        else if (queryType.equals("-mx")) // mail server
        {
            question.write(0x00);
            question.write(0x0f);
        }
        else
        {
            question.write(0x00); // by default, type A
            question.write(0x01);
        }

        // Q_CLASS
        question.write(0x00); // always 1
        question.write(0x01);

        return question.toByteArray();
    }

    public static void ParseAnswer(byte[] receiveData)
    {


    }
}

