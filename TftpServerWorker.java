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
            //byte[] buf = new byte[message.getBytes().length + 1];
            //byte[0] = ERROR;
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

    private void sendFile(String filename)
    {
        try {

            //open a new datagram socket
            //DatagramSocket sock = new DatagramSocket();

            int bytesRead = 0;

            //used to indicate if the file length is a multiple of 512 or not
            int finalBytesRead = 0;

            FileInputStream reader = new FileInputStream(filename.trim());
            //maybe works now?










            int currentBlock = 1;
            //since it exists, send
            byte[] buf = new byte[DATA_BUFF_LENGTH];

            //what is this about?
            //byte[] currentBlock = new byte[DATA_BUFF_LENGTH];
            //byte[] nextBlock = new byte[DATA_BUFF_LENGTH];

            DatagramSocket sock = new DatagramSocket();

            //set timeout length for sock.receive();


            sock.setSoTimeout(TIMEOUT_LENGTH);
            //maybe i can read in 512 bytes twice and store how much it read


            //if -1 then there is no more data
            //will input data stating at the third byte in the array
            while((bytesRead = reader.read(buf, 0, READ_LENGTH)) != -1) {
                byte[] read = new byte[bytesRead + 2];
                //System.out.println("Bytes read: " + bytesRead);
                finalBytesRead = bytesRead;
                read[0] = DATA;
                read[1] = (byte)currentBlock;
                System.arraycopy(buf, 0, read, 2, bytesRead);

                //System.out.println(new String(read, 0, read.length));

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
                    //if nothing is recieved, socket timeout exception will occur

                    //check if it's an acknowledgement
                    byte[] data = block.getData();
                    int rType = data[0];
                    System.out.println("rType: " + rType);
                    int bNum = data[1];
                    System.out.println("bNum: " + bNum);

                    String response = null;

                    if(rType != ACK) {
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
                    if(Integer.compareUnsigned(bNum,(currentBlock + 1)) == 0) {
                        break;
                    }
                    else if((bNum % 128) == 0) {
                        //reset block counter
                        currentBlock = 0;
                        break;
                    }
                    else if(Integer.compareUnsigned(bNum,currentBlock) != 0) {
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

                currentBlock++;
            }
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

            System.out.println("Succesfully sent file.");
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
        int type = data[0];
        String request = new String(data);

        if(type != RRQ) {
            //not a properly formatted request
            String response = "improperly formatted Read Request.";
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
