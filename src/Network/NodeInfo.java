package Network;

import java.net.InetAddress;

public class NodeInfo {
    private final InetAddress ip;
    private final int port;

    public NodeInfo(InetAddress ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public InetAddress getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    @Override
    public int hashCode() {
        return (ip.toString() + port).hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (o == null || o.getClass() != this.getClass())
            return false;

        NodeInfo hi = (NodeInfo) o;

        return hi.ip.equals(this.ip) && hi.port == this.port;
    }


    @Override
    public String toString() {
        return ip.toString() + ":" + port;
    }
}