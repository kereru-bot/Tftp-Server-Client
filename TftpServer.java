import java.net.*;
import java.io.*;
import java.util.*;

/*
 * A server that implements TFTP for sending files
 */
class TftpServer
{
    public void start_server() {
        try {
            DatagramSocket ds = new DatagramSocket();
            System.out.println("TftpServer listening on port " + ds.getLocalPort());

            while(true) {
                byte[] buf = new byte[1472];
                DatagramPacket p = new DatagramPacket(buf, 1472);
                ds.receive(p);
                System.out.println("Request Receieved...");
                TftpServerWorker worker = new TftpServerWorker(p);
                worker.start();
            }
        }
        catch(Exception e) {
            System.err.println("Exception: " + e);
        }

        return;
    }

    public static void main(String args[])
    {
        TftpServer server = new TftpServer();
        server.start_server();
    }
}
