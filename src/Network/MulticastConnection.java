package Network;

import java.io.IOException;
import java.net.*;

public class MulticastConnection {

    public static final int DEFAULT_SOCKET_TIMEOUT = 1000;
    public static final int DEFAULT_MULTICATS_PORT = 9192;
    public static final String MULTICATS_ADDRESS_IPV4 = "239.192.0.4";
    public static final int DEFAULT_PACKAGE_SIZE = 8192;
    private MulticastSocket multicastSocket;
    private int port;
    private InetAddress groupAddress;
    private int socketTimeout;
    private final int maxPackageSize;

    public MulticastConnection() {
        this.socketTimeout = DEFAULT_SOCKET_TIMEOUT;
        this.maxPackageSize = DEFAULT_PACKAGE_SIZE;
        this.port = DEFAULT_MULTICATS_PORT;

        try {
            groupAddress = InetAddress.getByName(MULTICATS_ADDRESS_IPV4);
            multicastSocket = new MulticastSocket(port);
            multicastSocket.joinGroup(groupAddress); // TODO: replace deprecated method
            multicastSocket.setSoTimeout(socketTimeout);
        } catch (SocketException e) {
            System.err.println("Cannot create socket for port " + port + ": " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendPacket(DatagramPacket packet) {
        try {
            multicastSocket.send(packet);
        } catch (IOException e) {
            System.err.println("Cannot send packet: " + e.getMessage());
        }
    }

    public DatagramPacket receivePacket() {
        DatagramPacket receivePacket = new DatagramPacket(new byte[DEFAULT_PACKAGE_SIZE], DEFAULT_PACKAGE_SIZE);
        try {
            multicastSocket.receive(receivePacket);
            return receivePacket;
        } catch (IOException e) {
            return null;
        }
    }

    public InetAddress getLocalAddress() {
        return multicastSocket.getLocalAddress();
    }

    public int getMaxPackageSize() {
        return maxPackageSize;
    }
}