package Logic;

import Protobuf.SnakesProto;

import java.net.InetAddress;

public class JoinInfo {
    private InetAddress address;
    private int port;

    private String name;

    private int width;
    private int height;
    private int staticFood;
    private double foodPerPlayer;
    private double deadDropProb;
    private int numOfPlayers;
    private boolean canJoin;

    private SnakesProto.GameConfig gameConfig;

    public JoinInfo(InetAddress address, int port, String name,
                    int width, int height,
                    int staticFood, double foodPerPlayer, double deadDropProb,
                    int numOfPlayers, boolean canJoin, SnakesProto.GameConfig gameConfig) {

        this.address = address;
        this.port = port;
        this.name = name;
        this.width = width;
        this.height = height;
        this.staticFood = staticFood;
        this.foodPerPlayer = foodPerPlayer;
        this.deadDropProb = deadDropProb;
        this.numOfPlayers = numOfPlayers;
        this.canJoin = canJoin;
        this.gameConfig = gameConfig;
    }

    public InetAddress getAddress() {
        return address;
    }
    public void setAddress(InetAddress ip) {
        this.address = ip;
    }

    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }

    public int getWidth() {
        return width;
    }
    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }
    public void setHeight(int height) {
        this.height = height;
    }

    public int getStaticFood() {
        return staticFood;
    }
    public void setStaticFood(int staticFood) {
        this.staticFood = staticFood;
    }

    public double getFoodPerPlayer() {
        return foodPerPlayer;
    }
    public void setFoodPerPlayer(double foodPerPlayer) {
        this.foodPerPlayer = foodPerPlayer;
    }

    public double getDeadDropProb() {
        return deadDropProb;
    }
    public void setDeadDropProb(double foodDropChance) {
        this.deadDropProb = foodDropChance;
    }

    public int getNumOfPlayers() {
        return numOfPlayers;
    }
    public void setNumOfPlayers(int numOfPlayers) {
        this.numOfPlayers = numOfPlayers;
    }

    public boolean isCanJoin() {
        return canJoin;
    }
    public void setCanJoin(boolean canJoin) {
        this.canJoin = canJoin;
    }

    public SnakesProto.GameConfig getGameConfig() {
        return gameConfig;
    }
    public void setGameConfig(SnakesProto.GameConfig gameConfig) {
        this.gameConfig = gameConfig;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}