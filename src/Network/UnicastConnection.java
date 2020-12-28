package Network;

import java.io.IOException;
import java.net.*;

public class UnicastConnection {

    public static final int DEFAULT_SOCKET_TIMEOUT = 1000;
    public static final int DEFAULT_PACKAGE_SIZE = 1024;

    private DatagramSocket datagramSocket;
    private int socketTimeout;
    private final int maxPackageSize;

    public UnicastConnection() {
        this.socketTimeout = DEFAULT_SOCKET_TIMEOUT;
        this.maxPackageSize = DEFAULT_PACKAGE_SIZE;

        try {
            datagramSocket = new DatagramSocket();
            datagramSocket.setSoTimeout(socketTimeout);
        } catch (SocketException e) {
            System.err.println("Cannot create socket for port " + datagramSocket.getPort() + ": " + e.getMessage());
        }
    }

    public void sendPacket(DatagramPacket packet) {
        try {
            datagramSocket.send(packet);
        } catch (IOException e) {
            System.err.println("Cannot send packet: " + e.getMessage());
        }
    }

    public DatagramPacket receivePacket() {
        DatagramPacket receivePacket = new DatagramPacket(new byte[DEFAULT_PACKAGE_SIZE], DEFAULT_PACKAGE_SIZE);
        try {
            datagramSocket.receive(receivePacket);
            return receivePacket;
        } catch (IOException e) {
            return null;
        }
    }

    public InetAddress getLocalAddress() {
        return datagramSocket.getLocalAddress();
    }

    public int getLocalPort() {
        return datagramSocket.getLocalPort();
    }

    public int getMaxPackageSize() {
        return maxPackageSize;
    }

    public void setSoTimeout(int timeout) {
        try {
            datagramSocket.setSoTimeout(timeout);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }
}