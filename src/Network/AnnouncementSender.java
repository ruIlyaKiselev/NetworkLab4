package Network;

import com.google.protobuf.InvalidProtocolBufferException;
import Protobuf.SnakesProto;
import Logic.GameLogic;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class AnnouncementSender extends Thread  {
    private MulticastConnection multicastConnection = new MulticastConnection();
    private boolean hasToSend = false;

    private GameLogic snakeGame;
    private SnakesProto.GameConfig gameConfig;

    private final int timeout = 5000;
    private Timer timer = new Timer();

    private final HashMap<NodeInfo, Long> lastUpdate;
    private final ConcurrentHashMap<NodeInfo, SnakesProto.GameMessage.AnnouncementMsg> sessionInfoMap;

    public AnnouncementSender(ConcurrentHashMap<NodeInfo, SnakesProto.GameMessage.AnnouncementMsg> sessionInfoMap) throws IOException {
        lastUpdate = new HashMap<>();
        this.sessionInfoMap = sessionInfoMap;
    }

    @Override
    public void run() {
        DatagramPacket datagramPacket;
        while (!Thread.interrupted()) {
            datagramPacket = multicastConnection.receivePacket();
            processMessage(datagramPacket);
            checkMap();
        }

        timer.cancel();
    }

    private void checkMap() {
        long timeNow = System.currentTimeMillis();
        lastUpdate.entrySet().removeIf(entry -> timeNow - entry.getValue() > timeout);
        sessionInfoMap.entrySet().removeIf(entry -> !lastUpdate.containsKey(entry.getKey()));
    }

    private SnakesProto.GameMessage getMessage() {
        SnakesProto.GameMessage.Builder gameMessageBuilder = SnakesProto.GameMessage.newBuilder();

        SnakesProto.GameMessage.AnnouncementMsg.Builder annonMesBuilder = SnakesProto.GameMessage.AnnouncementMsg.newBuilder();
        annonMesBuilder.setConfig(gameConfig);
        SnakesProto.GamePlayers.Builder gamePlayersBuilder = SnakesProto.GamePlayers.newBuilder();

        for (Map.Entry<Integer, SnakesProto.GamePlayer> entry : snakeGame.getPlayers().entrySet()) {
            gamePlayersBuilder.addPlayers(entry.getValue());
        }

        annonMesBuilder.setPlayers(gamePlayersBuilder);
        gameMessageBuilder.setAnnouncement(annonMesBuilder);
        gameMessageBuilder.setMsgSeq(1);

        return gameMessageBuilder.build();
    }


    public void sendAnnouncementMessage(GameLogic snakeGame, SnakesProto.GameConfig gameConfig) {
        synchronized (this) {
            if (timer != null) {
                timer.cancel();
                timer = null;
            }

            timer = new Timer();

            this.snakeGame = snakeGame;
            this.gameConfig = gameConfig;

            hasToSend = true;

            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (!hasToSend) return;
                    SnakesProto.GameMessage message = getMessage();
                    byte[] messageByte = message.toByteArray();
                    try {
                        multicastConnection.sendPacket(new DatagramPacket(messageByte, messageByte.length,
                                InetAddress.getByName(MulticastConnection.MULTICATS_ADDRESS_IPV4),
                                MulticastConnection.DEFAULT_MULTICATS_PORT));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, 0, 1000);
        }
    }

    private void processMessage(DatagramPacket datagramPacket) {
        try {
            if (datagramPacket != null) {
                byte[] bytesFromMessage = new byte[datagramPacket.getLength()];
                System.arraycopy(datagramPacket.getData(), 0, bytesFromMessage,
                        0, datagramPacket.getLength());

                SnakesProto.GameMessage snakesProto = SnakesProto.GameMessage.parseFrom(bytesFromMessage);
                if (!snakesProto.hasAnnouncement()) {
                    return;
                }

                SnakesProto.GameMessage.AnnouncementMsg announcementMsg = snakesProto.getAnnouncement();
                NodeInfo nodeInfo = null;

                for (SnakesProto.GamePlayer player : announcementMsg.getPlayers().getPlayersList()) {
                    if (player.getRole() == SnakesProto.NodeRole.MASTER) {
                        nodeInfo = new NodeInfo(datagramPacket.getAddress(), player.getPort());
                    }
                }

                if (nodeInfo == null) {
                    return;
                }

                sessionInfoMap.put(nodeInfo, announcementMsg);
                lastUpdate.put(nodeInfo, System.currentTimeMillis());
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    public void closeSender() {
        synchronized (this) {
            if (timer != null) {
                hasToSend = false;
                timer.cancel();
                timer = null;
            }
        }
    }
}