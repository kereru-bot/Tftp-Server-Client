import java.net.*;
import java.io.*;
import java.util.*;

class TftpClient {

    private static final byte RRQ = 1;
    private static final byte DATA = 2;
    private static final byte ACK = 3;
    private static final byte ERROR = 4;

    private static final int DATA_BUFF_LENGTH = 512;


    public static void main(String[] args) {

        //incase I forget how to use it
        if(args.length == 0 || args[0].compareTo("--help") == 0) {
            System.out.println("Usage: java TftpClient (host-address) (port-number) (file-name)");
            return;
        }

        int portNum = 0;
        InetAddress ip = null;
        String filename = null;
       //argument 1 ip address
       //argument 2 port number
       //argument 3 file name

        try {
             ip = InetAddress.getByName(args[0]);

             portNum = Integer.parseInt(args[1]);

             filename = args[2];

            //retrieveFile(ip, portNum, filename);
        }
        catch(UnknownHostException ex) {
            System.err.println("Unknown host, try a different address?");
            return;
        }
        catch(NumberFormatException ex) {
            System.err.println("Port number must be a... number");
            return;
        }

        retrieveFile(ip, portNum, filename);


    }



    public static void retrieveFile(InetAddress ip, int portNum, String filename) {

        try {
            DatagramSocket sock = new DatagramSocket();
            sock.setSoTimeout(10000);

            byte[] sBytes = filename.getBytes();
            byte[] buf = new byte[DATA_BUFF_LENGTH];
            buf[0] = RRQ;

            System.arraycopy(sBytes, 0, buf, 1, sBytes.length);

            //length of this packet dictates how much data I can receive, maybe make the buffer bigger?
            DatagramPacket pack = new DatagramPacket(buf, buf.length, ip, portNum);
            InetAddress serverAddr = null;
            int serverPort = 0;

            FileOutputStream file = new FileOutputStream("filename.txt");
            int currentBlock = 1;

            while(true) {
                byte[] data = null;
                int type = 0;
                int blockNum = 0;
                int length = 0;

                sock.send(pack);

                while(true) {
                    sock.receive(pack);

                    serverAddr = pack.getAddress();
                    serverPort = pack.getPort();
                    length = pack.getLength();

                    System.out.println(length);

                    data = pack.getData();

                    type = data[0];
                    blockNum = data[1];

                    if(blockNum == currentBlock - 1) {
                        //duplicate block, request current block again
                        sendAck((byte)currentBlock, serverAddr, serverPort);
                    }

                    if(blockNum != currentBlock) {
                        System.err.println("Invalid block of data was recieved... Teminating connection.");
                        sock.close();
                        return;
                    }

                    break;
                }
                //assuming that the correct block was given


                //end of file
                if(data.length == 0) {
                    break;
                }

                if(type == ERROR) {
                    String response = new String(data, 2, length - 2);
                    System.err.println("ERROR: " + response);
                    sock.close();
                    return;
                }
                else if(type != DATA) {
                    System.err.println("Block recieved was improperly formatted... Terminating connection.");
                    sock.close();
                    return;
                    //imporperly formatted block

                }

                file.write(data, 2, (length - 2));
                //System.out.println(Integer.decode(new String(data, 0, 1)));
                //System.out.println(new String(data));

                blockNum++;

                //end of file
                if(length < 514) {
                    break;
                }
            }

            sock.close();
            file.close();

        }
        catch(SocketTimeoutException ex) {
            System.err.println("Connection timed out...");
        }
        catch(IOException ex) {
            System.err.println("IOException: " + ex);
        }

        return;
    }

    public static void sendAck(byte blockNum, InetAddress ip, int portNum) {
        try {
            DatagramSocket sock = new DatagramSocket();
            byte[] buf = new byte[2];
            buf[0] = ACK;
            buf[1] = blockNum;
            DatagramPacket pack = new DatagramPacket(buf, buf.length, ip, portNum);
            sock.send(pack);
        }
        catch(IOException ex) {
            System.err.println("IOException: " + ex);
        }
        return;
    }
}
