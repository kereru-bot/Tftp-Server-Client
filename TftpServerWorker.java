import java.net.*;
import java.io.*;
import java.util.*;

/**
 * A class that can send a file using TFTP, given a request datagram packet
 *  */
class TftpServerWorker extends Thread
{
    private DatagramPacket req;
    private InetAddress senderAddr;
    private int senderPort;


    private static final byte RRQ = 1;
    private static final byte DATA = 2;
    private static final byte ACK = 3;
    private static final byte ERROR = 4;

    //bytes to read from a file at a time
    private static final int READ_LENGTH = 512;
    //max amount of room needed in the buffer
    private static final int DATA_BUFF_LENGTH = 514;
    private static final int TIMEOUT_LENGTH = 1000;

    /*
     * Sends an error to the client with the given message
     * @param message The error message to send to the client
     */
    private void sendError(String message) {
        try {
            byte[] buf = new byte[message.getBytes().length + 1];
            buf[0] = ERROR;
            byte[] sBytes = message.getBytes();
            System.arraycopy(sBytes, 0, buf, 1, sBytes.length);

            DatagramSocket sock = new DatagramSocket();
            DatagramPacket response = new DatagramPacket(buf, buf.length, senderAddr, senderPort);
            sock.send(response);
            sock.close();
        }
        catch(IOException ex) {
            System.err.println("IOException: " + ex);
        }
        return;
    }

    /**
     * Attempts to send a file to the sender address and port for this request
     * @param filename The name of the file to be sent */
    private void sendFile(String filename)
    {
        try {
            int bytesRead = 0;

            //used to indicate if the file length is a multiple of 512 or not
            int finalBytesRead = 0;

            //File f = new File(filename.trim());
            //if(!f.exists()) {
             //   System.out.println(filename + " was not found.");
             //   String response = filename + " was not found.";
             //   sendError(response);
             //   return;
            //}
            FileInputStream reader = new FileInputStream(filename.trim());
            int currentBlock = 1;
            int prevBlock = 0;
            byte[] buf = new byte[DATA_BUFF_LENGTH];
            DatagramSocket sock = new DatagramSocket();

            //set timeout length for the socket
            sock.setSoTimeout(TIMEOUT_LENGTH);

            //if -1 then there is no more data
            while((bytesRead = reader.read(buf, 0, READ_LENGTH)) != -1) {
                byte[] read = new byte[bytesRead + 2];
                //System.out.println("Bytes read: " + bytesRead);
                finalBytesRead = bytesRead;
                read[0] = DATA;
                read[1] = (byte)currentBlock;
                if(prevBlock == 255) {
                    read[1] = (byte)prevBlock;
                }
                System.arraycopy(buf, 0, read, 2, bytesRead);

                //while attempting to send and recieve a packet
                while(true) {

                    //System.out.println(read.length);
                    DatagramPacket block = new DatagramPacket(read, read.length, senderAddr, senderPort);

                    int attempts = 1;
                    //will attempt to send a packet 5 times
                    //if no response is received, will close connection
                    while(true) {
                        sock.send(block);
                        System.out.println("Sent block " + currentBlock);
                        try {
                            //await a response
                            sock.receive(block);
                            System.out.println("Recieved response for block " + currentBlock);
                            break;
                        }
                        catch(SocketTimeoutException ex) {
                            //try resending 5 times
                            if(attempts == 5) {
                                //stop trying
                                //close connection
                                String error = "Connection timed out.";
                                System.out.println(error);
                                sendError(error);
                                sock.close();
                                return;
                            }
                            attempts++;
                        }
                    }

                    byte[] data = block.getData();
                    int rType = data[0];
                    System.out.println("rType: " + rType);
                    int bNum = data[1];
                    System.out.println("bNum: " + bNum);

                    String response = null;

                    //check if it's an acknowledgement
                    if(rType != ACK) {
                        //not an acknowledgement, send error and close connection
                        response = "Improperly formatted acknowledgement.";
                        sendError(response);
                        reader.close();
                        sock.close();
                        return;
                    }

                    //to convert it to an unsigned int
                    if(bNum < 0) {
                        bNum = bNum & 0xff;
                    }
                    //if acknowledgement is asking for next block
                    if(bNum == (currentBlock + 1)) {
                        break;
                    }
                    else if(currentBlock == 255) {
                        //reset block counter
                        prevBlock = currentBlock;
                        currentBlock = 0;
                        break;
                    }
                    else if(bNum != currentBlock) {
                        response = "Incorrect block number received.";
                        sendError(response);
                        reader.close();
                        sock.close();
                        return;
                    }
                    //if ack is current block, do nothing to resend file
                }
                prevBlock = currentBlock;
                currentBlock++;
            }

            System.out.println(finalBytesRead);
            if(finalBytesRead == 512) {
                //send a packet with 0 bytes to indicate finishing
                buf = new byte[3];
                buf[0] = DATA;
                buf[1] = (byte)currentBlock;
                buf[2] = 0;
                DatagramPacket block = new DatagramPacket(buf, buf.length, senderAddr, senderPort);
                sock.send(block);
            }
            //at this point, it should be finished sending

            System.out.println("Succesfully sent file: " + filename);
            //be tidy
            reader.close();
            sock.close();
        }
        catch(FileNotFoundException ex) {
            System.out.println(filename + " was not found.");
            String response = filename + " was not found.";
            sendError(response);
            return;
        }
        catch(IOException ex) {
            System.err.println("IOException: " + ex);
            return;
        }

        return;
    }

    public void run()
    {
        int length = req.getLength();
        byte[] data = req.getData();
        int type = data[0];
        String request = new String(data);

        if(type != RRQ) {
            String response = "improperly formatted Read Request.";
            sendError(response);
            return;
        }

        String fName = request.substring(1);

        if(fName.length() == 0) {
            String response = "No file requested. (Read Request was empty)";
            sendError(response);
            return;
        }
        sendFile(fName);

        return;
    }

    /*
     * Creates a TftpServerWorked with the given request packet
     * and uses the address + port in the packet to send data
     * to the client
     * @param req The request packet
     */
    public TftpServerWorker(DatagramPacket req) {
        this.req = req;
        this.senderAddr = req.getAddress();
        this.senderPort = req.getPort();
    }
}
