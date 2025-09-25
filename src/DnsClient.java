import java.io.*;
import java.net.*;
import java.util.*;

public class DnsClient
{
    // Request Attributes
    static int iTimeout = 5;
    static int iMaxRetries = 3;
    static int iPort = 53;
    static String sType = "A";
    static String sIp = null;
    static String sServerName = null;
    static InetAddress ipAddress = null;

    // Response Attributes
    static int iRCode = 0;
    static int answerCount = 0;
    static int additionRecordsCount = 0;
    static byte[] qName;
    static byte[] qType = new byte[2];
    static byte[] qClass = new byte[2];

    public static void main(String[] args) throws IOException
    {
        DatagramSocket clientSocket = new DatagramSocket(); // establish socket

        byte[] sendData = new byte[1024];
        byte[] receiveData = new byte[1024];

        ParseArguments(args, clientSocket); // parse arguments read flags

        byte[] queryHeader = BuildQueryHeader(); //build query header

        byte[] questionBytes = BuildDNSQuestion(sServerName, sType); // build DNS question

        // header + question
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        outputStream.write(queryHeader);
        outputStream.write(questionBytes);

        sendData = outputStream.toByteArray();

        // Send the data
        int tryCount = 0; // keep track of number of tries
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, iPort);
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);  //Receive Data

        while (tryCount < iMaxRetries)
        {
            try
            {
                double t1 = System.currentTimeMillis();
                clientSocket.send(sendPacket);
                System.out.printf("DnsClient sending request for %s\nServer: %s\nRequest Type: %s\n", sServerName, sIp, sType);
                clientSocket.receive(receivePacket);
                double t2 = System.currentTimeMillis();
                double elapsedTime = (t2 - t1) / 1000; // convert to seconds
                System.out.printf("Response received after %f seconds (%d retries)", elapsedTime, tryCount);
                break;
            }
            catch (SocketTimeoutException e)
            {
                tryCount++;
                System.out.printf("Timeout reached, retry %d\n", tryCount);
            }
        }

        if (tryCount == iMaxRetries)
        {
            System.out.printf("Request failed after %d tries.", tryCount);
        }
        else
        {
            // Interpret Data TO-DO
            InterpretResponse(receiveData);
        }

        //PRINTING RAW RESPONSE FOR TESTING PURPOSES
        int length = receivePacket.getLength();
        System.out.println("Received " + length + " bytes");
        for (int i = 0; i < length; i++) {
            System.out.printf("%02X ", receiveData[i]);
            if ((i + 1) % 16 == 0)
            {
                System.out.println();
            }
        }

        clientSocket.close();
    }

    public static void ParseArguments(String[] args, DatagramSocket clientSocket) throws UnknownHostException, SocketException {
        for (int i = 0; i < args.length; i++)
        {
            switch (args[i])
            {
                case "-t":
                    iTimeout = Integer.parseInt(args[++i]); // skip flag arg
                    break;
                case "-r":
                    iMaxRetries = Integer.parseInt(args[++i]);
                    break;
                case "-p":
                    iPort = Integer.parseInt(args[++i]);
                    break;
                case "-mx":
                    sType = "-mx";
                    break;
                case "-ns":
                    sType = "-ns";
                    break;
                default:
                    if (args[i].startsWith("@") && sIp == null)
                    {
                        sIp = args[i];
                    }
                    else if (sServerName == null)
                    {
                        sServerName = args[i];
                    }
            }
        }

        clientSocket.setSoTimeout(iTimeout * 1000); // convert to milliseconds

        if (sIp != null)
        {
            String[] ipSplit = sIp.substring(1).split("\\."); // split with commas and remove @
            byte[] ipByteArray = new byte[ipSplit.length]; // iPV4 is always 32 bits so we'll need 4 bytes
            for (int i = 0; i < ipSplit.length; i++)
            {
                ipByteArray[i] = (byte) Integer.parseInt(ipSplit[i]);
            }
            ipAddress = InetAddress.getByAddress(ipByteArray);
        }
        // here I shoulkd throw an exception if no ip is provided
    }

    public static byte[] BuildQueryHeader()
    {
        byte[] header = new byte[12]; // note I could use a bytebuffer instead clem what do you think

        // ID                QR OP   AA TC RD RA Z   RCODE QDCOUNT          ANCOUNT          NSCOUNT          ARCOUNT
        // xxxxxxxx xxxxxxxx 0  0000 0  0  1  0  000 0000  0000000000000001 0000000000000000 0000000000000000 0000000000000000
        // xxxxxxxx xxxxxxxx 00000001 00000000 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000

        Random random = new Random();
        int randomID = random.nextInt(0xFFFF); // 16 bits generated num
        header[0] = (byte) ((randomID >> 8) & 0xFF);
        header[1] = (byte) (randomID & 0xFF);

        header[2] = 0x01; // we want recursion
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

    public static void InterpretResponse(byte[] receiveData)
    {
        // Header

        // ID                QR OP   AA TC RD RA Z   RCODE QDCOUNT          ANCOUNT          NSCOUNT          ARCOUNT
        // xxxxxxxx xxxxxxxx 0  0000 0  0  1  0  000 0000  0000000000000001 0000000000000000 0000000000000000 0000000000000000
        byte[] responseHeader = Arrays.copyOfRange(receiveData, 0, 12);
        Boolean bIsAuthoritative = (responseHeader[2] & 0x04) != 0; // AA flag in 3rd byte 3rd bit
        Boolean bWasTruncated = (responseHeader[2] & 0x02) != 0; // TXC flag 3rd byte 2nd bit
        iRCode = responseHeader[3] & 0x0F; // error message
        answerCount = ((responseHeader[6] & 0xFF) << 8) | (responseHeader[7] & 0xFF); // combine 7 & 8 bytes
        additionRecordsCount = ((responseHeader[10] & 0xFF) << 8) | (responseHeader[11] & 0xFF); // combine 9 and 10th bytes

        // Question
    }
}

