import java.net.*;
import java.io.*;
import java.util.*;


class TftpServerWorker extends Thread
{
    private DatagramPacket req;
    private InetAddress senderAddr;
    private int senderPort;


    private static final byte RRQ = 1;
    private static final byte DATA = 2;
    private static final byte ACK = 3;
    private static final byte ERROR = 4;
    private static final int READ_LENGTH = 512;
    private static final int DATA_BUFF_LENGTH = 514;
    private static final int TIMEOUT_LENGTH = 1000;


    private void sendError(String message) {
        try {
            message = ERROR + message;
            byte[] buf = message.getBytes();
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

    private void sendFile(String filename)
    {
        try {

            //open a new datagram socket
            //DatagramSocket sock = new DatagramSocket();

            int bytesRead = 0;

            //used to indicate if the file length is a multiple of 512 or not
            int finalBytesRead = 0;
            //File file = new File(filename);
            System.out.println(new File(filename).getAbsolutePath());
            FileInputStream reader = new FileInputStream(filename);
            //dont leave like this, it's broken
            //









            int blockNum = 1;
            //since it exists, send
            byte[] buf = new byte[DATA_BUFF_LENGTH];

            byte[] currentBlock = new byte[DATA_BUFF_LENGTH];
            byte[] nextBlock = new byte[DATA_BUFF_LENGTH];

            DatagramSocket sock = new DatagramSocket();

            //set timeout length for sock.receive();


            sock.setSoTimeout(TIMEOUT_LENGTH);
            //maybe i can read in 512 bytes twice and store how much it read


            //if -1 then there is no more data
            //will input data stating at the third byte in the array
            while((bytesRead = reader.read(buf, 2, READ_LENGTH)) != -1) {
                finalBytesRead = bytesRead;

                while(true) {

                    DatagramPacket block = new DatagramPacket(buf, buf.length, senderAddr, senderPort);

                    //will attempt to send a packet 5 times
                    //if no response is received, will close connection
                    while(true) {
                        int attempts = 1;
                        sock.send(block);

                        try {
                            //await a response
                            sock.receive(block);
                            break;
                        }
                        catch(SocketTimeoutException ex) {
                            //try resending 5 times
                            if(attempts == 5) {
                                //stop trying
                                //close connection
                                String error = "Connection timed out.";
                                sendError(error);
                                sock.close();
                                return;
                            }

                            attempts++;
                        }
                    }
                    //if nothing is recieved, socket timeout exception will occur

                    //check if it's an acknowledgement
                    byte[] data = block.getData();
                    String response = new String(data);
                    String rType = response.substring(0,1);
                    String bNum = response.substring(1);

                    if(Integer.decode(rType) != ACK) {
                        //not an acknowledgement
                        //send error
                        //close connection
                        response = "Improperly formatted acknowledgement.";
                        sendError(response);
                        reader.close();
                        sock.close();
                        return;
                    }

                    //if acknowledgement is asking for next block
                    if(Integer.decode(bNum) == (blockNum + 1)) {
                        break;
                    }
                    else if(Integer.decode(bNum) != blockNum) {
                        response = "Incorrect block number received.";
                        sendError(response);
                        reader.close();
                        sock.close();
                        return;
                    }
                    //if ack is current block
                    //no nothing

                }

                //check block num
                //if block number is current block, resent it
                //if block number is next block, continue

                blockNum++;
            }
            if(finalBytesRead == 512) {
                //send a packet with 0 bytes to indicate finishing
                buf = new byte[0];
                DatagramPacket block = new DatagramPacket(buf, buf.length, senderAddr, senderPort);
                sock.send(block);
            }
            //at this point, it should be finished sending

            //be tidy
            reader.close();
            sock.close();
        }
        catch(FileNotFoundException ex) {
            //send error packet back, saying file not found
            //not sure if error will work properly if concatenated
            System.out.println(filename + " was not found.");
            String response = filename + " was not found.";
            sendError(response);
            //find way to close socket and reader here
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
        String request = new String(data);

        String type = request.substring(0,1);

        if(Integer.decode(type) != RRQ) {
            //not a properly formatted request
            String response = "Improperly formatted Read Request.";
            sendError(response);
            return;
        }

        String fName = request.substring(1);

        if(fName.length() == 0) {
            //error, nothing sent
            String response = "No file requested. (Read Request was empty)";
            sendError(response);
            return;
        }

        //excludes the first byte
        sendFile(fName);

        /*
         * parse the request packet, ensuring that it is a RRQ
         * and then call sendfile
         */

        return;
    }

    public TftpServerWorker(DatagramPacket req) {
        this.req = req;
        this.senderAddr = req.getAddress();
        this.senderPort = req.getPort();
    }
}
