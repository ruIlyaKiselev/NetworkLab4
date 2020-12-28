package Network;

import Protobuf.SnakesProto;
import Logic.Snake;
import Graphic.ErrorBox;
import Graphic.GameWindow;
import com.google.protobuf.InvalidProtocolBufferException;
import Logic.GameLogic;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkLogic {
    private UnicastConnection unicastConnection = new UnicastConnection();

    private final GameLogic gameLogic;
    private final GameWindow gameWindow;

    private final ConcurrentHashMap<NodeInfo, ConcurrentHashMap<Long, SnakesProto.GameMessage>> messages
            = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<NodeInfo, Long> nodesTimeout = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, NodeInfo> playersIds = new ConcurrentHashMap<>();
    private final MessageCounter msgSeqGenerator = new MessageCounter();
    private final ConcurrentHashMap<NodeInfo, SnakesProto.NodeRole> playersRoles = new ConcurrentHashMap<>();

    private NodeInfo master = null;
    private int masterId = -1;
    private NodeInfo deputy = null;
    private int deputyId = -1;
    private int myId;
    private int pingDelay;
    private int nodeTimeout;

    private boolean becomingViewer = false;
    private boolean wantToExit = false;

    private Timer sender;
    private Timer nodesTimeoutChecker;
    private Thread receiver;

    private SnakesProto.NodeRole nodeRole;

    private final ConcurrentHashMap<NodeInfo, ConcurrentHashMap<Long, Long>> lastIds = new ConcurrentHashMap<>();

    public NetworkLogic(GameLogic gameLogic, SnakesProto.GameConfig gameConfig, SnakesProto.NodeRole nodeRole) {
        this.nodeRole = nodeRole;
        this.gameLogic = gameLogic;
        gameWindow = gameLogic.getGameWindow();

        Init(gameConfig);
    }

    private void Init(SnakesProto.GameConfig gameConfig) {
        pingDelay = gameConfig.getPingDelayMs();
        nodeTimeout = gameConfig.getNodeTimeoutMs();

        receiver = new Thread(() ->
        {
            unicastConnection.setSoTimeout(pingDelay);

            while (!Thread.interrupted()) {
                DatagramPacket datagramPacket;
                datagramPacket = unicastConnection.receivePacket();
                if (datagramPacket != null) {
                    proccessedMessage(datagramPacket);
                }
            }
        });

        receiver.start();

        sender = new Timer();
        sender.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        if (nodeRole == SnakesProto.NodeRole.MASTER) {
                            for (Map.Entry<Integer, NodeInfo> entry : playersIds.entrySet()) {
                                if (entry.getKey() == myId) {
                                    continue;
                                }

                                if (!messages.containsKey(entry.getValue())) {
                                    messages.put(entry.getValue(), new ConcurrentHashMap<>());
                                }
                                if (messages.get(entry.getValue()).size() == 0) {
                                    SnakesProto.GameMessage newPing = createPing();
                                    messages.get(entry.getValue()).put(newPing.getMsgSeq(), newPing);
                                }
                            }
                        } else {
                            if (master == null) {
                                return;
                            }
                            if (!messages.containsKey(master))
                                messages.put(master, new ConcurrentHashMap<>());
                            if (messages.get(master).size() == 0) {
                                SnakesProto.GameMessage newPing = createPing();
                                messages.get(master).put(newPing.getMsgSeq(), newPing);
                            }
                        }

                        for (Map.Entry<NodeInfo, ConcurrentHashMap<Long, SnakesProto.GameMessage>>
                                firstEntry : messages.entrySet()) {

                            for (Map.Entry<Long, SnakesProto.GameMessage>
                                    secondEntry : firstEntry.getValue().entrySet()) {

                                byte[] message = secondEntry.getValue().toByteArray();
                                unicastConnection.sendPacket(new DatagramPacket(message, 0, message.length,
                                        firstEntry.getKey().getIp(), firstEntry.getKey().getPort()));
                            }
                        }

                    }
                }, 0, pingDelay);

        nodesTimeoutChecker = new Timer();
        nodesTimeoutChecker.scheduleAtFixedRate(new TimerTask() {
            private final ArrayList<NodeInfo> timeoutedHosts = new ArrayList<>();

            @Override
            public void run() {
                boolean hasDeputy = false;
                long timeNow = System.currentTimeMillis();
                for (Map.Entry<NodeInfo, Long> entry : nodesTimeout.entrySet()) {
                    if (playersRoles.get(entry.getKey()) == SnakesProto.NodeRole.DEPUTY) {
                        hasDeputy = true;
                    }
                    if (timeNow - entry.getValue() > nodeTimeout) {
                        timeoutedHosts.add(entry.getKey());
                    }
                }

                boolean masterDeadFlag = false;
                boolean deputyDeadFlag = false;

                for (NodeInfo hi : timeoutedHosts) {
                    SnakesProto.NodeRole killedNodeRole = playersRoles.get(hi);

                    if (nodeRole == SnakesProto.NodeRole.MASTER) {
                        int hiId = findPlayerIdByHostInfo(hi);
                        if (hiId != -1) {
                            HashMap<Integer, Snake> snakes = gameLogic.getSnakes();
                            if (snakes.containsKey(hiId)) {
                                snakes.get(hiId).setSnakeState(SnakesProto.GameState.Snake.SnakeState.ZOMBIE);
                            }
                        }

                        gameLogic.getPlayers().remove(hiId);
                    }

                    playersRoles.remove(hi);
                    lastIds.remove(hi);
                    nodesTimeout.remove(hi);

                    if (killedNodeRole != SnakesProto.NodeRole.MASTER) {
                        messages.remove(hi);
                    } else {
                        masterDeadFlag = true;
                    }

                    if (killedNodeRole == SnakesProto.NodeRole.DEPUTY) {
                        deputyDeadFlag = true;
                    }
                }

                NodeInfo prevMaster = master;

                if (masterDeadFlag) {
                    if (nodeRole == SnakesProto.NodeRole.VIEWER && !hasDeputy) {
                        gameWindow.terminate();
                    } else if (nodeRole == SnakesProto.NodeRole.DEPUTY) {
                        becameMaster();
                    } else if (!deputyDeadFlag) {
                        findNewMaster();
                    }

                    if (master == null) {
                        for (Map.Entry<Long, SnakesProto.GameMessage> entry : messages.get(prevMaster).entrySet()) {
                            if (entry.getValue().hasSteer()) {
                                gameLogic.changeSnakeDir(myId, entry.getValue().getSteer().getDirection());
                            }
                        }
                    } else {
                        messages.put(master, messages.get(prevMaster));
                    }

                    messages.remove(prevMaster);
                }

                if (nodeRole == SnakesProto.NodeRole.MASTER && deputyDeadFlag) {
                    changeDeputy();
                }

                timeoutedHosts.clear();

                final long timeNow2 = System.currentTimeMillis();
                for (Map.Entry<NodeInfo, ConcurrentHashMap<Long, Long>> a : lastIds.entrySet()) {
                    a.getValue().entrySet().removeIf(b -> (b.getValue() - timeNow2) > nodeTimeout);
                }

            }
        }, 0, nodeTimeout);

    }

    private void proccessedMessage(DatagramPacket datagramPacket) {
        try {
            SnakesProto.GameMessage mess =
                    SnakesProto.GameMessage.parseFrom(ByteBuffer.wrap(datagramPacket.getData(), 0,
                            datagramPacket.getLength()));

            NodeInfo sender = new NodeInfo(datagramPacket.getAddress(), datagramPacket.getPort());

            if (mess.hasPing()) {
                if (!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq())) {
                    if (!lastIds.containsKey(sender)) {
                        lastIds.put(sender, new ConcurrentHashMap<>());
                    }

                    lastIds.get(sender).put(mess.getMsgSeq(), System.currentTimeMillis());
                }

                sendAck(mess, sender);

            } else if (mess.hasSteer()) {
                if (!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq())) {
                    if (!lastIds.containsKey(sender)) {
                        lastIds.put(sender, new ConcurrentHashMap<>());
                    }

                    lastIds.get(sender).put(mess.getMsgSeq(), System.currentTimeMillis());

                    gameLogic.changeSnakeDir(mess.getSenderId(), mess.getSteer().getDirection());
                }

                sendAck(mess, sender);
            } else if (mess.hasAck()) {
                if (!messages.containsKey(sender)) {
                    return;
                }

                if (!messages.get(sender).containsKey(mess.getMsgSeq())) {
                    return;
                }

                nodesTimeout.put(sender, System.currentTimeMillis());

                SnakesProto.GameMessage messThatAcked = messages.get(sender).get(mess.getMsgSeq());

                if (messThatAcked.hasJoin()) {
                    master = sender;
                    playersRoles.put(sender, SnakesProto.NodeRole.MASTER);
                    playersIds.put(mess.getSenderId(), master);
                    gameLogic.getGameWindow().setPlayerID(mess.getReceiverId());

                    SnakesProto.GameMessage newPingMsg = createPing();

                    sendAndStoreMessage(master, newPingMsg);
                    masterId = mess.getSenderId();

                    myId = mess.getReceiverId();

                } else if (messThatAcked.hasRoleChange()) {
                    SnakesProto.GameMessage.RoleChangeMsg rlChgMsg = messThatAcked.getRoleChange();

                    if (becomingViewer && messThatAcked.getSenderId() == myId
                            && rlChgMsg.hasSenderRole()
                            && rlChgMsg.getSenderRole() == SnakesProto.NodeRole.VIEWER) {

                        becomingViewer = false;

                        if (wantToExit) {
                            gameWindow.terminate();
                        }
                    }
                }

                messages.get(sender).remove(mess.getMsgSeq());

            } else if (mess.hasState()) {
                if (nodeRole == SnakesProto.NodeRole.MASTER) {
                    sendAck(mess, sender);
                    return;
                }

                if (!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq())) {
                    if (!lastIds.containsKey(sender)) {
                        lastIds.put(sender, new ConcurrentHashMap<>());
                    }

                    lastIds.get(sender).put(mess.getMsgSeq(), System.currentTimeMillis());

                    SnakesProto.GameState gameState = mess.getState().getState();

                    if (gameState.getStateOrder() < gameLogic.getGameStateCounter()) {
                        sendAck(mess, sender);
                        return;
                    }
                    gameLogic.loadState(gameState, sender);
                    gameLogic.getGameWindow().repaint();


                    Map<Integer, Boolean> hasPlayer = new HashMap<>();

                    for (SnakesProto.GamePlayer player : gameState.getPlayers().getPlayersList()) {
                        if (player.getId() == myId) {
                            continue;
                        }

                        NodeInfo hi;
                        if (!playersIds.containsKey(player.getId())) {
                            if (!player.getIpAddress().equals("")) {
                                try {
                                    hi = new NodeInfo(InetAddress.getByName(player.getIpAddress()), player.getPort());
                                } catch (UnknownHostException e) {
                                    System.out.println("Cannot decode ip from: " + player.getIpAddress());
                                    continue;
                                }
                            } else {
                                hi = sender;
                            }
                        } else {
                            hi = playersIds.get(player.getId());
                        }

                        playersIds.put(player.getId(), hi);
                        playersRoles.put(hi, player.getRole());
                        if (player.getRole() == SnakesProto.NodeRole.DEPUTY) {
                            deputyId = player.getId();
                            deputy = hi;
                        } else if (player.getRole() == SnakesProto.NodeRole.MASTER) {
                            masterId = player.getId();
                            master = hi;
                        }


                        hasPlayer.put(player.getId(), true);
                    }

                    Iterator<Map.Entry<Integer, NodeInfo>> it = playersIds.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Integer, NodeInfo> entry = it.next();
                        if (hasPlayer.containsKey(entry.getKey()) && !hasPlayer.get(entry.getKey())) {
                            playersIds.remove(entry.getKey());
                            playersRoles.remove(entry.getValue());
                            messages.remove(entry.getValue());
                            nodesTimeout.remove(entry.getValue());
                        }
                    }

                    boolean meDead = gameLogic.isDead(myId);

                    if (meDead && (nodeRole != SnakesProto.NodeRole.VIEWER)) {
                        SnakesProto.GameMessage.RoleChangeMsg.Builder roleChangeMsg =
                                SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                                        .setReceiverRole(SnakesProto.NodeRole.MASTER)
                                        .setSenderRole(SnakesProto.NodeRole.VIEWER);

                        SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                                .setRoleChange(roleChangeMsg)
                                .setSenderId(myId)
                                .setReceiverId(masterId)
                                .setMsgSeq(msgSeqGenerator.getCounter())
                                .build();

                        sendAndStoreMessage(master, message);
                    }

                }

                sendAck(mess, sender);

            } else if (mess.hasJoin()) {
                if (!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq())) {
                    if (!lastIds.containsKey(sender)) {
                        lastIds.put(sender, new ConcurrentHashMap<>());
                    }

                    lastIds.get(sender).put(mess.getMsgSeq(), System.currentTimeMillis());

                    SnakesProto.GameMessage.JoinMsg joinMsg = mess.getJoin();


                    SnakesProto.PlayerType newPlayerType = SnakesProto.PlayerType.HUMAN;
                    if (joinMsg.hasPlayerType()) {
                        newPlayerType = joinMsg.getPlayerType();
                    }


                    SnakesProto.NodeRole newNodeRole = SnakesProto.NodeRole.VIEWER;
                    if (!joinMsg.hasOnlyView() || !joinMsg.getOnlyView()) {
                        newNodeRole = SnakesProto.NodeRole.NORMAL;
                    }

                    int newPlayerId = gameLogic.addPlayer(mess.getJoin().getName(),
                            newNodeRole, newPlayerType, sender.getIp().getHostAddress(), sender.getPort());

                    if (messages.get(sender) == null) {
                        messages.put(sender, new ConcurrentHashMap<>());
                    }

                    if (newPlayerId == -1) {
                        SnakesProto.GameMessage errorMes = SnakesProto.GameMessage.newBuilder()
                                .setError(SnakesProto.GameMessage.ErrorMsg.newBuilder()
                                        .setErrorMessage("No place for you"))
                                .build();

                        messages.get(sender).put(msgSeqGenerator.getCounter(), errorMes);
                    } else {

                        playersRoles.put(sender, newNodeRole);

                        playersIds.put(newPlayerId, sender);

                        sendAck(mess, sender);

                        if (deputy == null) {
                            deputy = sender;
                            deputyId = newPlayerId;

                            SnakesProto.GameMessage roleChangeMess = SnakesProto.GameMessage.newBuilder()
                                    .setMsgSeq(msgSeqGenerator.getCounter())
                                    .setReceiverId(newPlayerId)
                                    .setSenderId(myId)
                                    .setRoleChange(SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                                            .setSenderRole(SnakesProto.NodeRole.MASTER)
                                            .setReceiverRole(SnakesProto.NodeRole.DEPUTY))
                                    .build();

                            playersRoles.put(sender, SnakesProto.NodeRole.DEPUTY);
                            gameLogic.getPlayers().put(newPlayerId,
                                    gameLogic.getPlayers().get(newPlayerId).toBuilder().
                                            setRole(SnakesProto.NodeRole.DEPUTY).build());

                            sendAndStoreMessage(sender, roleChangeMess);
                        }
                    }
                } else if (playersRoles.containsKey(sender)) {
                    sendAck(mess, sender);
                }

            } else if (mess.hasError()) {
                if (!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq())) {
                    if (!lastIds.containsKey(sender)) {
                        lastIds.put(sender, new ConcurrentHashMap<>());
                    }

                    lastIds.get(sender).put(mess.getMsgSeq(), System.currentTimeMillis());

                    ErrorBox.display(mess.getError().getErrorMessage());

                    gameWindow.terminate();
                }

                sendAck(mess, sender);
            } else if (mess.hasRoleChange()) {
                if (!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq())) {
                    if (mess.hasReceiverId() && mess.hasSenderId()) {

                        SnakesProto.GameMessage.RoleChangeMsg roleChangeMsg = mess.getRoleChange();

                        var players = gameLogic.getPlayers();

                        if (roleChangeMsg.hasReceiverRole() && players.containsKey(mess.getReceiverId())) {
                            NodeInfo messReceiver = playersIds.get(mess.getReceiverId());
                            if (messReceiver != null) {
                                playersRoles.put(messReceiver, roleChangeMsg.getReceiverRole());
                            }

                            if (mess.getReceiverId() == myId) changeRole(roleChangeMsg.getReceiverRole());

                            if (nodeRole == SnakesProto.NodeRole.MASTER) {
                                players.put(mess.getReceiverId(), players.get(mess.getReceiverId()).toBuilder().setRole(roleChangeMsg.getReceiverRole()).build());
                                changeDeputy();
                            }
                        } else {
                            return;
                        }

                        if (roleChangeMsg.hasSenderRole() && players.containsKey(mess.getSenderId())) {
                            NodeInfo messSender = playersIds.get(mess.getSenderId());
                            if (messSender != null) {
                                playersRoles.put(messSender, roleChangeMsg.getSenderRole());
                            }

                            if (nodeRole == SnakesProto.NodeRole.MASTER) {
                                players.put(mess.getSenderId(), players.get(mess.getSenderId()).toBuilder().setRole(roleChangeMsg.getSenderRole()).build());
                                if (roleChangeMsg.getSenderRole() == SnakesProto.NodeRole.VIEWER) {
                                    changeDeputy();
                                }
                            }

                            if (roleChangeMsg.getSenderRole() == SnakesProto.NodeRole.MASTER) {
                                master = messSender;
                            }
                            if (roleChangeMsg.getSenderRole() == SnakesProto.NodeRole.DEPUTY) {
                                deputy = messSender;
                            }
                        }

                        if (!lastIds.containsKey(sender)) {
                            lastIds.put(sender, new ConcurrentHashMap<>());
                        }

                        lastIds.get(sender).put(mess.getMsgSeq(), System.currentTimeMillis());

                    }

                }

                sendAck(mess, sender);
            }

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    private SnakesProto.GameMessage createAck(SnakesProto.GameMessage gameMessage, int receiverId) {
        return SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(gameMessage.getMsgSeq())
                .setAck(SnakesProto.GameMessage.AckMsg.newBuilder().build())
                .setSenderId(myId)
                .setReceiverId(receiverId)
                .build();
    }

    public void sendSteer(int senderId, SnakesProto.Direction dir) {
        if (master == null) return;

        SnakesProto.GameMessage steerMessage = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(msgSeqGenerator.getCounter())
                .setSteer(SnakesProto.GameMessage.SteerMsg
                        .newBuilder()
                        .setDirection(dir))
                .setSenderId(senderId)
                .build();

        if (!messages.containsKey(master)) {
            messages.put(master, new ConcurrentHashMap<>());
        }

        byte[] steerMsg = steerMessage.toByteArray();
        DatagramPacket steerDp = new DatagramPacket(steerMsg, 0, steerMsg.length,
                master.getIp(), master.getPort());

        unicastConnection.sendPacket(steerDp);
        messages.get(master).put(steerMessage.getMsgSeq(), steerMessage);
    }

    public void sendJoin(NodeInfo nodeInfo, String name) {
        SnakesProto.GameMessage joinMsg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(msgSeqGenerator.getCounter())
                .setJoin(SnakesProto.GameMessage.JoinMsg
                        .newBuilder()
                        .setName(name))
                .build();

        byte[] joinMsgByte = joinMsg.toByteArray();
        DatagramPacket datagramPacket = new DatagramPacket(joinMsgByte, 0, joinMsgByte.length,
                nodeInfo.getIp(), nodeInfo.getPort());

        unicastConnection.sendPacket(datagramPacket);


        if (!messages.containsKey(nodeInfo)) {
            messages.put(nodeInfo, new ConcurrentHashMap<>());
        }

        messages.get(nodeInfo).put(joinMsg.getMsgSeq(), joinMsg);
    }

    public void disableMessageManager() {
        receiver.interrupt();
        nodesTimeoutChecker.cancel();
        sender.cancel();
    }

    public void sendState() {
        List<Integer> killedSnakes = gameLogic.getDeadSnakes();

        boolean masterDead = false;
        boolean deputyDead = false;

        for (int id : killedSnakes) {
            if (id == myId) {
                masterDead = true;
            } else if (deputy != null && id == findPlayerIdByHostInfo(deputy)) {
                deputyDead = true;
            } else {
                NodeInfo hi = playersIds.get(id);
                SnakesProto.NodeRole nr = playersRoles.get(hi);
            }
        }

        if (masterDead) {
            becameViewer();
        } else {
            if (deputyDead) {
                changeDeputy();
            }
        }

        SnakesProto.GameState gameState = gameLogic.generateNewState();


        SnakesProto.GameMessage.StateMsg.Builder stateMsgBuilder = SnakesProto.GameMessage.StateMsg.newBuilder()
                .setState(gameState);

        for (Map.Entry<Integer, NodeInfo> entry : playersIds.entrySet()) {
            if (entry.getKey() == myId) continue;

            SnakesProto.GameMessage stateMsg = SnakesProto.GameMessage.newBuilder()
                    .setState(stateMsgBuilder)
                    .setMsgSeq(msgSeqGenerator.getCounter())
                    .build();

            sendAndStoreMessage(entry.getValue(), stateMsg);

        }
    }

    public NodeInfo getHostInfo(int pi) {
        return playersIds.get(pi);
    }

    public int addMe(String name, SnakesProto.NodeRole _nodeRole, SnakesProto.PlayerType _playerType) {
        int newId = gameLogic.addPlayer(name, _nodeRole, _playerType, "", unicastConnection.getLocalPort());
        if (newId > 0) {
            playersIds.put(newId, new NodeInfo(unicastConnection.getLocalAddress(), unicastConnection.getLocalPort()));
        }

        myId = newId;
        return newId;
    }

    private void sendAck(SnakesProto.GameMessage gameMessage, NodeInfo hi) {
        int receiverId = findPlayerIdByHostInfo(hi);
        if (receiverId == -1) {
            return;
        }
        SnakesProto.GameMessage ack = createAck(gameMessage, receiverId);

        sendMessage(hi, ack);
    }

    private SnakesProto.GameMessage createPing() {
        SnakesProto.GameMessage pingMsg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(msgSeqGenerator.getCounter())
                .setPing(SnakesProto.GameMessage.PingMsg.newBuilder().build())
                .build();

        return pingMsg;
    }

    private void sendMessage(NodeInfo receiver, SnakesProto.GameMessage message) {
        byte[] messByte = message.toByteArray();
        DatagramPacket ackDp = new DatagramPacket(messByte, 0, messByte.length,
                receiver.getIp(), receiver.getPort());

        unicastConnection.sendPacket(ackDp);
    }

    private void sendAndStoreMessage(NodeInfo receiver, SnakesProto.GameMessage message) {
        if (!messages.containsKey(receiver))
            messages.put(receiver, new ConcurrentHashMap<>());

        sendMessage(receiver, message);

        messages.get(receiver).put(message.getMsgSeq(), message);
    }

    private void changeDeputy() {
        if (playersRoles.size() == 0) return;

        deputy = null;
        deputyId = -1;

        for (Map.Entry<NodeInfo, SnakesProto.NodeRole> entry : playersRoles.entrySet()) {
            if (entry.getValue() == SnakesProto.NodeRole.DEPUTY) {
                deputy = entry.getKey();
                deputyId = findPlayerIdByHostInfo(entry.getKey());
                return;
            }
            if (entry.getValue() == SnakesProto.NodeRole.NORMAL) {
                int receiverId = findPlayerIdByHostInfo(entry.getKey());
                if (receiverId == -1) {
                    continue;
                }

                SnakesProto.GameMessage roleChangeMsg = createRoleChangeMessage(receiverId, myId,
                        SnakesProto.NodeRole.DEPUTY, SnakesProto.NodeRole.MASTER);

                HashMap<Integer, SnakesProto.GamePlayer> players = gameLogic.getPlayers();
                players.put(receiverId, players.get(receiverId).toBuilder().setRole(SnakesProto.NodeRole.DEPUTY).build());
                playersRoles.put(entry.getKey(), SnakesProto.NodeRole.DEPUTY);
                sendAndStoreMessage(entry.getKey(), roleChangeMsg);

                break;
            }
        }
    }

    private SnakesProto.GameMessage createRoleChangeMessage(int receiverId, int senderId,
                                                            SnakesProto.NodeRole receiverRole,
                                                            SnakesProto.NodeRole senderRole) {
        return SnakesProto.GameMessage.newBuilder()
                .setRoleChange(SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                        .setReceiverRole(receiverRole)
                        .setSenderRole(senderRole))
                .setSenderId(senderId)
                .setReceiverId(receiverId)
                .setMsgSeq(msgSeqGenerator.getCounter())
                .build();
    }


    private int findPlayerIdByHostInfo(NodeInfo hostInfo) {
        int playerId = -1;
        for (Map.Entry<Integer, NodeInfo> entry : playersIds.entrySet()) {
            if (entry.getValue().equals(hostInfo)) {
                playerId = entry.getKey();
                break;
            }
        }

        return playerId;
    }


    public void safeExit() {
        if (nodeRole == SnakesProto.NodeRole.VIEWER)
            gameWindow.terminate();

        wantToExit = true;
        becameViewer();
    }

    public void becameViewer() {
        if (nodeRole == SnakesProto.NodeRole.VIEWER) return;

        SnakesProto.GameMessage.Builder roleChangeMsg =
                SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(msgSeqGenerator.getCounter())
                .setSenderId(myId);

        SnakesProto.GameMessage.RoleChangeMsg.Builder rlChgMsgBuilder
                = SnakesProto.GameMessage.RoleChangeMsg.newBuilder();

        rlChgMsgBuilder.setSenderRole(SnakesProto.NodeRole.VIEWER);
        rlChgMsgBuilder.setReceiverRole(SnakesProto.NodeRole.MASTER);

        roleChangeMsg.setRoleChange(rlChgMsgBuilder);


        becomingViewer = true;

        if (nodeRole == SnakesProto.NodeRole.MASTER) {
            if (deputy != null) {
                roleChangeMsg.setReceiverId(deputyId);
                sendAndStoreMessage(deputy, roleChangeMsg.build());
            } else {
                gameWindow.terminate();
            }
        } else {
            roleChangeMsg.setReceiverId(masterId);
            sendAndStoreMessage(master, roleChangeMsg.build());
        }

        changeRole(SnakesProto.NodeRole.VIEWER);

    }

    private void becameMaster() {
        changeRole(SnakesProto.NodeRole.MASTER);

        master = null;
        masterId = -1;

        SnakesProto.GameMessage.Builder gameMessageBuilder = SnakesProto.GameMessage.newBuilder();

        SnakesProto.GameMessage.RoleChangeMsg.Builder roleChgMsgBuilder = SnakesProto.GameMessage.RoleChangeMsg.newBuilder();

        roleChgMsgBuilder.setSenderRole(SnakesProto.NodeRole.MASTER);

        gameMessageBuilder.setSenderId(myId);

        for (Map.Entry<Integer, NodeInfo> entry : playersIds.entrySet()) {

            gameMessageBuilder.setReceiverId(entry.getKey());

            if (!playersRoles.containsKey(entry.getValue())) continue;

            roleChgMsgBuilder.setReceiverRole(playersRoles.get(entry.getValue()));

            if (deputy == null && playersRoles.get(entry.getValue()) != SnakesProto.NodeRole.VIEWER) {
                deputy = entry.getValue();
                deputyId = entry.getKey();
                roleChgMsgBuilder.setReceiverRole(SnakesProto.NodeRole.DEPUTY);
            }

            gameMessageBuilder.setMsgSeq(msgSeqGenerator.getCounter());

            SnakesProto.GameMessage message = gameMessageBuilder.setRoleChange(roleChgMsgBuilder.build()).build();

            sendMessage(entry.getValue(), message);

        }

    }

    private void findNewMaster() {
        for (var entry : playersIds.entrySet()) {
            if (playersRoles.get(entry.getValue()) == SnakesProto.NodeRole.DEPUTY) {
                master = entry.getValue();
                masterId = entry.getKey();
                if (!nodesTimeout.containsKey(master)) {
                    nodesTimeout.put(master, System.currentTimeMillis());
                }
            }
        }
    }

    private void changeRole(SnakesProto.NodeRole _nodeRole) {
        if (nodeRole != _nodeRole) {
            if (nodeRole == SnakesProto.NodeRole.VIEWER) {
                return;
            }

            if (nodeRole == SnakesProto.NodeRole.MASTER) {
                master = deputy;
                masterId = deputyId;
                deputy = null;
                deputyId = -1;
            }

            nodeRole = _nodeRole;
            gameWindow.setNodeRole(nodeRole);
        }
    }
}