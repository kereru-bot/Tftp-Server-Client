import java.net.*;
import java.io.*;
import java.util.*;

/**
 * A class that connects to a server using TFTP and downloads the requested file.
 * Usage is: java TftpClient (host-address) (port-number) (file-name) */
class TftpClient {

    //different packet types
    private static final byte RRQ = 1;
    private static final byte DATA = 2;
    private static final byte ACK = 3;
    private static final byte ERROR = 4;

    //length of the data buffers
    private static final int DATA_BUFF_LENGTH = 514;


    public static void main(String[] args) {

        //incase I forget how to use it
        if(args.length == 0 || args[0].compareTo("--help") == 0) {
            System.out.println("Usage: java TftpClient (host-address) (port-number) (file-name)");
            return;
        }

        int portNum = 0;
        InetAddress ip = null;
        String filename = null;

        try {
             ip = InetAddress.getByName(args[0]);

             portNum = Integer.parseInt(args[1]);

             filename = args[2];
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


    /**
     * Attempts to retrieve a file from the given address and port using TFTP
     * @param ip The ip of the server to request the file from
     * @param portNum The port number of the server to request the file from
     * @param filename The name of the file to request from the server */
    public static void retrieveFile(InetAddress ip, int portNum, String filename) {

        try {
            DatagramSocket sock = new DatagramSocket();

            //if for some reason, the client doesn't recieve any packet back
            sock.setSoTimeout(10000);

            byte[] sBytes = filename.getBytes();
            //buf must be the size of the max data that you expect to recieve or it wont work properly
            byte[] buf = new byte[DATA_BUFF_LENGTH];
            buf[0] = RRQ;

            //better than converting stuff into strings
            System.arraycopy(sBytes, 0, buf, 1, sBytes.length);


            DatagramPacket pack = new DatagramPacket(buf, buf.length, ip, portNum);
            InetAddress serverAddr = null;
            int serverPort = 0;

            FileOutputStream file;
            File f = new File(filename);

            if(f.exists()) {
                //not a dynamic solution but if fine for purposes of what I'm doing
                String[] parts = filename.split("[.]");
                parts[0] += "(1).";
                filename = parts[0] + parts[1];
                file = new FileOutputStream(filename);
            }
            else {
                file = new FileOutputStream(filename);
            }

            //starting block
            int currentBlock = 1;
            int prevBlock = 0;

            byte[] data = null;
            int type = 0;
            int blockNum = 0;
            int length = 0;

            sock.send(pack);

            while(true) {

                while(true) {
                    sock.receive(pack);
                    System.out.println("Recieved block " + currentBlock);

                    serverAddr = pack.getAddress();
                    serverPort = pack.getPort();
                    length = pack.getLength();

                    //System.out.println(length);

                    data = pack.getData();

                    type = data[0];

                    if(type == ERROR) {
                        String response = new String(data, 1, length - 2);
                        System.out.println("ERROR: " + response);
                        file.close();
                        sock.close();
                        return;
                    }
                    else if(type != DATA) {
                        System.out.println("Block recieved was improperly formatted... Terminating connection.");
                        file.close();
                        sock.close();
                        return;
                    }

                     //end of file if it's only 2 bytes
                    if(data.length == 2) {
                        System.out.println("End of file reached.");
                        return;
                    }

                    blockNum = data[1];

                    //will convert from a signed byte to unsigned
                    if(blockNum < 0) {
                        blockNum = data[1] & 0xff;
                    }

                    //duplicate block, request current block again
                    //might need updating
                    if(blockNum == (currentBlock - 1) && currentBlock != 1) {
                        sendAck((byte)currentBlock, serverAddr, serverPort);
                    }
                    else {
                        break;
                    }

                }

                if(blockNum != currentBlock) {
                    System.out.println("Invalid block of data was recieved... Teminating connection.");
                    System.out.println(currentBlock + " " + blockNum);
                    file.close();
                    sock.close();
                    return;
                }
                else {
                    file.write(data, 2, (length - 2));
                    sendAck((byte)(currentBlock + 1), serverAddr, serverPort);
                    System.out.println("Requested block: " + (currentBlock + 1));
                    prevBlock = currentBlock;
                    currentBlock++;
                }

                //reset block number
                if(currentBlock == 256) {
                    currentBlock = 1;
                }

                //end of file
                if(length < 514) {
                    break;
                }
            }

            System.out.println("Successfully recieved file: " + filename);
            //System.out.println("Finished.");
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

    /**
     * Sends an acknowledgement for a block of data to the given address
     * @param blockNum The block number that is being acknowledged
     * @param ip The address to send the acknowledgement to
     * @param portNum The port number to send the acknowledgment to */
    public static void sendAck(byte blockNum, InetAddress ip, int portNum) {
        try {
            DatagramSocket sock = new DatagramSocket();
            byte[] buf = new byte[2];
            buf[0] = ACK;
            buf[1] = blockNum;
            //System.out.println("Should be 3: " + buf[0]);
            DatagramPacket pack = new DatagramPacket(buf, buf.length, ip, portNum);
            sock.send(pack);
            sock.close();
        }
        catch(IOException ex) {
            System.err.println("IOException: " + ex);
        }
        return;
    }
}
