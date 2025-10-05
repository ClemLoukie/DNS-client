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

    public static void main(String[] args) throws IOException
    {
        DatagramSocket clientSocket = new DatagramSocket(); // establish socket

        byte[] sendData = new byte[1024];
        byte[] receiveData = new byte[1024];


        String message =  ParseArguments(args, clientSocket); // parse arguments read flags

        if (!message.isEmpty())
        {
            System.out.println("ERROR\t" + message);
        }
        else
        {
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
                    System.out.printf("Response received after %f seconds (%d retries)\n", elapsedTime, tryCount);
                    break;
                }
                catch (SocketTimeoutException e)
                {
                    tryCount++;
                    System.out.printf("Timeout reached, retry %d\n", tryCount);
                }
                catch (IOException e)
                {
                    tryCount++;
                    System.out.printf("IO exception occured, retry %d\n", tryCount);
                }
            }

            if (tryCount == iMaxRetries)
            {
                System.out.printf("Request failed after %d tries.\n", tryCount);
            }
            else
            {
                InterpretResponse(receiveData);
            }
        }
        clientSocket.close();
    }

    public static String ParseArguments(String[] args, DatagramSocket clientSocket) throws UnknownHostException, SocketException, IllegalArgumentException
    {
        if (args.length < 2)
        {
            return "Incorrect number of arguments. The ip address and domain name must be provided.";
        }
        else if (args.length > 9)
        {
            return "Too many arguments.";
        }

        Boolean hasMxFlag = false;
        Boolean hasNsFlag = false;

        for (int i = 0; i < args.length; i++)
        {
            switch (args[i])
            {
                case "-t":
                    try
                    {
                        iTimeout = Integer.parseInt(args[++i]); // skip flag arg
                    }
                    catch(NumberFormatException e)
                    {
                        return "Illegal argument for the timeout value. Value must be an integer.";
                    }
                    break;
                case "-r":
                    try
                    {
                        iMaxRetries = Integer.parseInt(args[++i]);
                    }
                    catch(NumberFormatException e)
                    {
                        return "Illegal argument for the maximum number of retries. Value must be an integer.";
                    }
                    break;
                case "-p":
                    try
                    {
                        iPort = Integer.parseInt(args[++i]);
                    }
                    catch(NumberFormatException e)
                    {
                        return "Illegal argument for the port number. Value must be an integer.";
                    }
                    break;
                case "-mx":
                    sType = "MX";
                    hasMxFlag = true;
                    if (hasNsFlag)
                    {
                        return "Cannot specify both -mx and -ns query types.";
                    }
                    break;
                case "-ns":
                    sType = "NS";
                    hasNsFlag = true;
                    if (hasMxFlag)
                    {
                        return "Cannot specify both -mx and -ns query types.";
                    }
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

        try
        {
            clientSocket.setSoTimeout(iTimeout * 1000); // convert to milliseconds
        }
        catch (SocketException e)
        {
            return "Error occurred while creating or accessing the socket.";
        }
        catch (IllegalArgumentException e)
        {
            return "Timeout value cannot be negative.";
        }

        if (sIp != null)
        {
            String[] ipSplit = sIp.substring(1).split("\\."); // split with commas and remove @
            byte[] ipByteArray = new byte[ipSplit.length]; // iPV4 is always 32 bits so we'll need 4 bytes
            for (int i = 0; i < ipSplit.length; i++)
            {
                ipByteArray[i] = (byte) Integer.parseInt(ipSplit[i]);
            }
            try
            {
                ipAddress = InetAddress.getByAddress(ipByteArray);
            }
            catch (UnknownHostException e)
            {
                return "Unknown host.";
            }
        }
        else if (sIp == null)
        {
            return "No server IP address provided.";
        }
        else if (sServerName == null)
        {
            return "No server name provided.";
        }
        return "";
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
        if (queryType.equals("NS")) // name server
        {
            question.write(0x00);
            question.write(0x02);
        }
        else if (queryType.equals("MX")) // mail server
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

    public static void InterpretResponse(byte[] receiveData) throws IOException {

        // error handling
        if (receiveData.length < 12)
        {
            throw new IOException("Header missing from DNS Response.");
        }

        int rCode = receiveData[3] & 0x0F;
        if (rCode != 0)
        {
            if (rCode == 3)
            {
                System.out.println("NOTFOUND");
                return;
            }
            String errorMsg;
            switch (rCode)
            {
                case 1:
                    errorMsg = "Format error: the name server was unable to interpret the query.";
                    break;
                case 2:
                    errorMsg = "Server failure: the name server was unable to process this query due to a problem with the name server.";
                    break;
                case 4:
                    errorMsg = "Not implemented: the name server does not support the requested kind of query.";
                    break;
                case 5:
                    errorMsg = "Refused: the name server refuses to perform the requested operation for policy reasons.";
                    break;
                default:
                    errorMsg = "Unknown DNS error code: " + rCode;
                    break;
            }
            System.out.println("ERROR\t" + errorMsg);
            return;
        }

        //byte[] receiveRecords = Arrays.copyOfRange(receiveData, 12, receiveData.length); // Reads EVERYTHING except first head
        int index = 12; // index used to traverse DNS. starts after the header

        // step 4: print
        int ANCOUNT = ((receiveData[6] & 0xFF) << 8) | (receiveData[7] & 0xFF); // number of records + to change
        if (ANCOUNT >= 1) {
            if (ANCOUNT == 1) System.out.println("***Answer Section (" + ANCOUNT + " record)***");
            else System.out.println("***Answer Section (" + ANCOUNT + " records)***");

            // We must reach the records section
            while (receiveData[index++] != 0);
            index += 2; // skip qtype3

            int QCLASS = ((receiveData[index] & 0xFF) << 8) | (receiveData[index+1] & 0xFF);
            if (QCLASS != 1) { // shoudl only be 1
                System.out.println("ERROR\t" + "Unknown response class. Only QCLASS 1 is supported.");
                return;
            }
            index += 2; // skip qclass

            //index += 2 + 2 ; // account for QTYPE and QCLASS. This is set to the index of the first record

            for (int x = 0; x < ANCOUNT; x++) {
                // We must move past the name and ACCOUNT FOR COMPRESSION
                /*
                 * • a sequence of labels ending with a zero octet;
                 * • a pointer; -> starts with 11xxxxxx (top 2 bits
                 * • a sequence of labels ending with a pointer.
                 */

                /*
                 * Since
                 * labels may have varying length, each label is preceded by a single byte giving the number of ASCII
                 * characters used in the label, and then each character is coded using 8-bit ASCII. To signal the end
                 * of a domain name, one last byte is written with value 0.
                 */

                // skip name field
                if ((receiveData[index] & 0xC0) == 0xC0) index += 2; // if NAME is pointer
                else{
                    while (receiveData[index] != 0 && (receiveData[index] & 0xC0) != 0xC0){
                        int label_length = receiveData[index] & 0xFF; //
                        index+= 1 + label_length;
                    }
                    if (receiveData[index] == 0) index++; // if sequence of labels ending with a zero octet
                    else index +=2; // if sequence of labels ending with a pointer
                }

                int type = ((receiveData[index++] & 0xFF) << 8) | (receiveData[index++] & 0xFF); // because 16 bit

                // index set to CLASS

                String alias = "TBD";

                String auth_status;
                if ((receiveData[2] & 0x04) == 0x04) auth_status = "auth" ;
                else auth_status = "nonauth"; // contained in header as 3 LSbit in second byte

                index += 2;
                long seconds_cache;
                seconds_cache = (((long) (receiveData[index++] & 0xFF) << 24) | ((long) (receiveData[index++] & 0xFF) << 16) | ((long) (receiveData[index++] & 0xFF) << 8) | ((receiveData[index++] & 0xFF))); // four octets

                int RDLENGTH = ((receiveData[index++] & 0xFF) << 8) | (receiveData[index++] & 0xFF); // because 16 bit

                switch (type) {
                    case 1:
                        /***
                         * If TYPE is 0x0001, for an A (IP address) record,
                         * then RDATA (index+8) is the IP address (four octets).
                         */

                        int[] ip_adress = new int[4];
                        ip_adress[0] = receiveData[index] & 0xFF;
                        ip_adress[1] = receiveData[index+1] & 0xFF;
                        ip_adress[2] = receiveData[index+2] & 0xFF;
                        ip_adress[3] = receiveData[index+3] & 0xFF;

                        System.out.println("IP\t "+ ip_adress[0] + "." + ip_adress[1] + "." + ip_adress[2] + "." + ip_adress[3] + " \t "+ seconds_cache +" \t " + auth_status);
                        break;

                    case 2:
                        /*
                         * If the TYPE is 0x0002, for a NS (name server) record,
                         * then this is the name of the server specified using the same format as the QNAME field.
                         * Here we start in RDATA
                         */

                        System.out.println("NS \t "+ QNAME(index,receiveData) +" \t " + seconds_cache + " \t " + auth_status);
                        break;

                    case 5:
                        /*
                         * If the TYPE
                         * is 0x005, for CNAME records, then this is the name of the alias.
                         */
                        System.out.println("CNAME \t "+ QNAME(index,receiveData) +" \t " + seconds_cache + " \t " + auth_status);
                        break;

                    case 15:
                        /*
                         * If the type is 0x000f for MX (mail
                         * server) records, then RDATA has the format
                         */
                        int pref = ((receiveData[index] & 0xFF) << 8) | (receiveData[index + 1] & 0xFF);
                        System.out.println("MX \t " + QNAME(index + 2,receiveData) +" \t " + pref + " \t "+ seconds_cache +" \t "+ auth_status);
                    break;
                }
                index += RDLENGTH; // index must maintain RDstart
            }
        }

        // step 5: print additional records (make this a function - for report -)
        int ARCOUNT = ((receiveData[10] & 0xFF) << 8) | (receiveData[11] & 0xFF);
        if (ARCOUNT >= 1) {
            if (ARCOUNT == 1) System.out.println("***Additional Section (" + ARCOUNT + " record)***");
            else System.out.println("***Additional Section (" + ARCOUNT + " records)***");

            for (int x = 0; x < ARCOUNT; x++) {
                // We must move past the name and ACCOUNT FOR COMPRESSION
                /*
                 * • a sequence of labels ending with a zero octet;
                 * • a pointer; -> starts with 11xxxxxx (top 2 bits
                 * • a sequence of labels ending with a pointer.
                 */
                /*
                 * Since
                 * labels may have varying length, each label is preceded by a single byte giving the number of ASCII
                 * characters used in the label, and then each character is coded using 8-bit ASCII. To signal the end
                 * of a domain name, one last byte is written with value 0.
                 */
                if ((receiveData[index] & 0xC0) == 0xC0) index += 2; // if NAME is pointer
                else{
                    while (receiveData[index] != 0 && (receiveData[index] & 0xC0) != 0xC0){
                        int label_length = receiveData[index] & 0xFF; //
                        index+= 1 + label_length;
                    }
                    if (receiveData[index] == 0) index++; // if sequence of labels ending with a zero octet
                    else index +=2; // if sequence of labels ending with a pointer
                }

                int type = ((receiveData[index++] & 0xFF) << 8) | (receiveData[index++] & 0xFF); // because 16 bit

                // index set to CLASS

                String alias = "TBD";

                String auth_status;
                if ((receiveData[2] & 0x04) == 0x04) auth_status = "auth" ;
                else auth_status = "nonauth"; // contained in header as 3 LSbit in second byte

                index += 2;
                long seconds_cache;
                seconds_cache = (((long) (receiveData[index++] & 0xFF) << 24) | ((long) (receiveData[index++] & 0xFF) << 16) | ((long) (receiveData[index++] & 0xFF) << 8) | ((receiveData[index++] & 0xFF))); // four octets

                int RDLENGTH = ((receiveData[index++] & 0xFF) << 8) | (receiveData[index++] & 0xFF); // because 16 bit

                switch (type) {
                    case 1:
                        /***
                         * If TYPE is 0x0001, for an A (IP address) record,
                         * then RDATA (index+8) is the IP address (four octets).
                         */

                        int[] ip_adress = new int[4];
                        ip_adress[0] = receiveData[index] & 0xFF;
                        ip_adress[1] = receiveData[index+1] & 0xFF;
                        ip_adress[2] = receiveData[index+2] & 0xFF;
                        ip_adress[3] = receiveData[index+3] & 0xFF;

                        System.out.println("IP\t "+ ip_adress[0] + "." + ip_adress[1] + "." + ip_adress[2] + "." + ip_adress[3] + " \t "+ seconds_cache +" \t " + auth_status);
                        break;

                    case 2:
                        /*
                         * If the TYPE is 0x0002, for a NS (name server) record,
                         * then this is the name of the server specified using the same format as the QNAME field.
                         * Here we start in RDATA
                         */

                        System.out.println("NS \t "+ QNAME(index,receiveData) +" \t " + seconds_cache + " \t " + auth_status);
                        break;

                    case 5:
                        /*
                         * If the TYPE
                         * is 0x005, for CNAME records, then this is the name of the alias.
                         */
                        System.out.println("CNAME \t "+ QNAME(index,receiveData) +" \t " + seconds_cache + " \t " + auth_status);
                        break;

                    case 15:
                        /*
                         * If the type is 0x000f for MX (mail
                         * server) records, then RDATA has the format
                         */
                        int pref = ((receiveData[index] & 0xFF) << 8) | (receiveData[index + 1] & 0xFF);
                        System.out.println("MX \t " + QNAME(index + 2,receiveData) +" \t " + pref + " \t "+ seconds_cache +" \t "+ auth_status);
                        break;
                }
                index += RDLENGTH; // index must maintain RDstart
            }
        }
    }

    /*
    This function reads bits formated like QNAME
     */
    public static String QNAME(int index, byte[] receiveData){
        int local_index = index; // initialize the traversal at the current index
        StringBuilder name = new StringBuilder();

        while ( ((receiveData[local_index] & 0xC0) == 0xC0) || (receiveData[local_index] != 0) ){ // while we have pointer or haven't met null terminator

            if ((receiveData[local_index] & 0xC0) == 0xC0){ // if you meet a pointer
                local_index = ( ((receiveData[local_index] & 0x3F) << 8) | (receiveData[local_index+1] & 0xFF) ) ;  // set index to pointer location (16 bit sequence
            }

            while ( receiveData[local_index] != 0 && (receiveData[local_index] & 0xC0) != 0xC0 ){
                int label_length = receiveData[local_index++] & 0xFF; //
                for (int y = 0; y < label_length; y++){
                    name.append((char) (receiveData[local_index++] & 0xFF));
                }
                if (receiveData[local_index] != 0) name.append(".");
            }
        }

        return name.toString();
    }
}

