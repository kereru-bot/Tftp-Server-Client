import java.net.*;
import java.io.*;
import java.util.*;

class TftpClient {

    private static final byte RRQ = 1;
    private static final byte DATA = 2;
    private static final byte ACK = 3;
    private static final byte ERROR = 4;


    public static void main(String[] args) {

        //incase I forget how to use it
        if(args.length == 0 || args[0].compareTo("--help") == 0) {
            System.out.println("Usage: java TftpClient (host-address) (port-number) (file-name)");
            return;
        }

       //argument 1 ip address
       //argument 2 port number
       //argument 3 file name

        try {
            InetAddress ip = InetAddress.getByName(args[0]);

            int portNum = Integer.parseInt(args[1]);

            String filename = args[2];

            retrieveFile(ip, portNum, filename);
        }
        catch(UnknownHostException ex) {
            System.err.println("Unknown host, try a different address?");
            return;
        }
        catch(NumberFormatException ex) {
            System.err.println("Port number must be a... number");
            return;
        }




    }



    public static void retrieveFile(InetAddress ip, int portNum, String filename) {


        try {
            DatagramSocket sock = new DatagramSocket();
            String request = RRQ + filename;
            byte[] bytes = request.getBytes();
            DatagramPacket pack = new DatagramPacket(bytes, bytes.length, ip, portNum);

            FileOutputStream file = new FileOutputStream("filename");
            int blockNum = 1;

            while(true) {
                sock.send(pack);

                sock.receive(pack);

                byte[] data = pack.getData();

                System.out.println(Integer.decode(new String(data, 0, 1)));
                System.out.println(new String(data));

                blockNum++;
            }

        }
        catch(IOException ex) {
            System.err.println("IOException: " + ex);
        }
    }

}
