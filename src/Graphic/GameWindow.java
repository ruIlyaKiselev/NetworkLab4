package Graphic;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import Network.AnnouncementSender;
import Logic.Score;
import Protobuf.SnakesProto;
import Network.NodeInfo;
import Network.NetworkLogic;
import Logic.Point;
import Logic.Snake;
import Logic.GameLogic;

import java.util.*;

public class GameWindow {
    private Stage stage = new Stage();

    private GameLogic snakeGame;
    private SnakesProto.GameConfig gameConfig;

    private int playerID;
    private GraphicsContext fieldGraphicsContext;
    private Scene scene;

    private TableView<Score> scores;

    private int fieldUnitSize;
    public int mainPaneSpaces = 10;
    private final int playerInterfaceWidth = 200;
    private final int sceneWidth;
    private final int sceneHeight;

    private Timer timer = new Timer();
    private final NetworkLogic messageManager;

    private SnakesProto.NodeRole nodeRole;
    private AnnouncementSender discoverer;

    public GameWindow(SnakesProto.GameConfig gameConfig, AnnouncementSender discoverer, String name,
                      SnakesProto.NodeRole nodeRole, NodeInfo hostInfo) {

        this.discoverer = discoverer;
        this.nodeRole = nodeRole;
        this.gameConfig = gameConfig;

        snakeGame = new GameLogic(gameConfig, this, nodeRole);
        messageManager = snakeGame.getMessageManager();
        messageManager.sendJoin(hostInfo, name);

        initFieldUnitSize();
        sceneWidth = fieldUnitSize * snakeGame.getWidth() + playerInterfaceWidth + mainPaneSpaces * 3;
        sceneHeight = fieldUnitSize * snakeGame.getHeight() + mainPaneSpaces * 2;

        start();
    }

    public GameWindow(SnakesProto.GameConfig gameConfig, AnnouncementSender discoverer, String name,
                      SnakesProto.NodeRole nodeRole) {
        this.discoverer = discoverer;
        this.nodeRole = nodeRole;
        this.gameConfig = gameConfig;

        snakeGame = new GameLogic(gameConfig, this, nodeRole);
        messageManager = snakeGame.getMessageManager();

        initFieldUnitSize();
        sceneWidth = fieldUnitSize * snakeGame.getWidth() + playerInterfaceWidth + mainPaneSpaces * 3;
        sceneHeight = fieldUnitSize * snakeGame.getHeight() + mainPaneSpaces * 2;

        if ((playerID = messageManager.addMe(name, SnakesProto.NodeRole.MASTER, SnakesProto.PlayerType.HUMAN)) == -1) {
            ErrorBox.display("Unable to create Snake");
        }

        discoverer.sendAnnouncementMessage(snakeGame, gameConfig);
        start();
    }

    public void start() {
        stage.setTitle("Snake game");

        GridPane mainPane = new GridPane();
        mainPane.setHgap(mainPaneSpaces);
        mainPane.setVgap(mainPaneSpaces);

        Canvas field = new Canvas(snakeGame.getWidth() * fieldUnitSize, snakeGame.getHeight() * fieldUnitSize);

        fieldGraphicsContext = field.getGraphicsContext2D();

        createScores();
        scores.setMaxWidth(playerInterfaceWidth);
        scores.setMinWidth(playerInterfaceWidth);
        scores.setMaxHeight(playerInterfaceWidth);
        scores.setMinHeight(playerInterfaceWidth);

        VBox playerInterface = new VBox(mainPaneSpaces);

        Button becameViewerButton = new Button("To Viewer");
        becameViewerButton.setMaxWidth(playerInterfaceWidth);
        becameViewerButton.setMaxWidth(playerInterfaceWidth);
        becameViewerButton.setFocusTraversable(false);
        becameViewerButton.setOnAction(actionEvent -> messageManager.becameViewer());

        Button exitButton = new Button("Exit");
        exitButton.setMaxWidth(playerInterfaceWidth);
        exitButton.setMaxWidth(playerInterfaceWidth);
        exitButton.setFocusTraversable(false);
        exitButton.setOnAction(actionEvent -> messageManager.safeExit());

        draw();

        playerInterface.getChildren().addAll(scores, becameViewerButton, exitButton);

        timer = new Timer();
        timer.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() ->
                        {
                            synchronized (snakeGame) {
                                if (nodeRole == SnakesProto.NodeRole.MASTER) {
                                    snakeGame.moveSnakes();
                                    messageManager.sendState();

                                    repaint();
                                }
                            }
                        });
                    }
                },
                0,
                gameConfig.getStateDelayMs());

        stage.setOnCloseRequest(windowEvent -> terminate());

        mainPane.add(field, 1, 0);
        mainPane.add(playerInterface, 2, 0);

        scene = new Scene(mainPane, sceneWidth, sceneHeight);

        keyReactionSnake();

        stage.setScene(scene);
        stage.show();
    }

    public void keyReactionSnake() {
        try {
            scene.setOnKeyPressed(keyEvent ->
            {
                if (keyEvent.getCode() == KeyCode.LEFT || keyEvent.getCode() == KeyCode.A) {
                    if (nodeRole == SnakesProto.NodeRole.MASTER) {
                        snakeGame.changeSnakeDir(playerID, SnakesProto.Direction.LEFT);
                    } else if (nodeRole != SnakesProto.NodeRole.VIEWER) {
                        messageManager.sendSteer(playerID, SnakesProto.Direction.LEFT);
                    }
                } else if (keyEvent.getCode() == KeyCode.RIGHT || keyEvent.getCode() == KeyCode.D) {
                    if (nodeRole == SnakesProto.NodeRole.MASTER) {
                        snakeGame.changeSnakeDir(playerID, SnakesProto.Direction.RIGHT );
                    } else if (nodeRole != SnakesProto.NodeRole.VIEWER) {
                        messageManager.sendSteer(playerID, SnakesProto.Direction.RIGHT);
                    }
                } else if (keyEvent.getCode() == KeyCode.UP || keyEvent.getCode() == KeyCode.W) {
                    if (nodeRole == SnakesProto.NodeRole.MASTER) {
                        snakeGame.changeSnakeDir(playerID, SnakesProto.Direction.UP);
                    } else if (nodeRole != SnakesProto.NodeRole.VIEWER) {
                        messageManager.sendSteer(playerID, SnakesProto.Direction.UP);
                    }
                } else if (keyEvent.getCode() == KeyCode.DOWN || keyEvent.getCode() == KeyCode.S) {
                    if (nodeRole == SnakesProto.NodeRole.MASTER) {
                        snakeGame.changeSnakeDir(playerID, SnakesProto.Direction.DOWN);
                    } else if (nodeRole != SnakesProto.NodeRole.VIEWER) {
                        messageManager.sendSteer(playerID, SnakesProto.Direction.DOWN);
                    }
                }
            });
        } catch (NullPointerException ignored) {
        }
    }

    public void repaint() {
        synchronized (snakeGame) {
            draw();
            updateScores();
            if (snakeGame.isGameOver()) {
                terminate();
            }
        }
    }

    public void terminate() {
        messageManager.disableMessageManager();
        timer.cancel();
        discoverer.closeSender();
        Platform.runLater(() -> stage.close());
    }

    private void draw() {
        drawField();
        drawSnakes();
        drawFood();
    }

    private void drawField() {
        String darkFieldColour = "#62D654";
        String lightFieldColour ="#83EA77";
        for (int i = 0; i != snakeGame.getWidth(); i++) {
            for (int j = 0; j != snakeGame.getHeight(); j++) {
                if ((i + j) % 2 == 0) {
                    fieldGraphicsContext.setFill(Color.web(darkFieldColour));
                } else {
                    fieldGraphicsContext.setFill(Color.web(lightFieldColour));
                }
                fieldGraphicsContext.fillRect(i * fieldUnitSize, j * fieldUnitSize,
                        fieldUnitSize - 1, fieldUnitSize - 1);
            }
        }
    }

    public void drawFood() {
        fieldGraphicsContext.setFill(Color.RED);
        for (Point food : snakeGame.getFood()) {
            fieldGraphicsContext.fillOval(food.getX() * fieldUnitSize, food.getY() * fieldUnitSize,
                    fieldUnitSize - 1, fieldUnitSize - 1);
        }
    }

    public void drawSnakes() {
        for (Map.Entry<Integer, Snake> entry : snakeGame.getSnakes().entrySet()) {
            if (snakeGame.getPlayers().containsKey(entry.getKey())) {
                if (entry.getKey() != playerID) {
                    fieldGraphicsContext.setFill(Color.DARKORANGE);
                } else {
                    fieldGraphicsContext.setFill(Color.DEEPPINK);
                }
            } else {
                fieldGraphicsContext.setFill(Color.GRAY);
            }
            fieldGraphicsContext.fillRect(entry.getValue().getSnakeBody().get(0).getX() * fieldUnitSize,
                    entry.getValue().getSnakeBody().get(0).getY() * fieldUnitSize,
                    fieldUnitSize - 1, fieldUnitSize - 1);
            for (int body = 1; body != entry.getValue().getSnakeBody().size(); body++) {
                if (snakeGame.getPlayers().containsKey(entry.getKey())) {
                    if (entry.getKey() != playerID) {
                        fieldGraphicsContext.setFill(Color.SANDYBROWN);
                    } else {
                        fieldGraphicsContext.setFill(Color.HOTPINK);
                    }
                } else {
                    fieldGraphicsContext.setFill(Color.DARKGRAY);
                }
                fieldGraphicsContext.fillRect(entry.getValue().getSnakeBody().get(body).getX() * fieldUnitSize,
                        entry.getValue().getSnakeBody().get(body).getY() * fieldUnitSize,
                        fieldUnitSize - 2, fieldUnitSize - 2);
            }

        }
    }

    private void createScores() {
        TableColumn<Score, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setMinWidth(playerInterfaceWidth / 2 - 1);
        nameColumn.setMaxWidth(playerInterfaceWidth / 2);

        TableColumn<Score, Integer> scoreColumn = new TableColumn<>("Score");
        scoreColumn.setCellValueFactory(new PropertyValueFactory<>("score"));
        scoreColumn.setSortType(TableColumn.SortType.DESCENDING);
        scoreColumn.setMinWidth(playerInterfaceWidth / 2 - 1);
        scoreColumn.setMaxWidth(playerInterfaceWidth / 2);

        scores = new TableView<>();
        scores.getColumns().addAll(nameColumn, scoreColumn);
        scores.setMaxWidth(playerInterfaceWidth);
        scores.setMinWidth(playerInterfaceWidth);
        scores.setEditable(false);
        scores.setFocusTraversable(false);
        scores.sort();
    }

    private void initFieldUnitSize() {
        int sizeX = snakeGame.getWidth();
        int sizeY = snakeGame.getHeight();

        if (sizeX <= 15 && sizeY <= 15) {
            fieldUnitSize = 50;
            return;
        }

        if (sizeX <= 20 && sizeY <= 20) {
            fieldUnitSize = 40;
            return;
        }

        if (sizeX <= 30 && sizeY <= 30) {
            fieldUnitSize = 30;
            return;
        }

        if (sizeX <= 40 && sizeY <= 40) {
            fieldUnitSize = 20;
            return;
        }

        if (sizeX <= 60 && sizeY <= 60) {
            fieldUnitSize = 15;
            return;
        }

        if (sizeX <= 80 && sizeY <= 80) {
            fieldUnitSize = 12;
            return;
        }

        if (sizeX <= 100 && sizeY <= 100) {
            fieldUnitSize = 10;
        }
    }

    private void updateScores() {
        ObservableList<Score> scoresNew = FXCollections.observableArrayList();
        for (Map.Entry<Integer, SnakesProto.GamePlayer> entry : snakeGame.getPlayers().entrySet()) {
            scoresNew.add(new Score(entry.getValue().getName(), entry.getValue().getScore()));
        }

        scores.setItems(scoresNew);
    }

    public void setPlayerID(int playerID) {
        this.playerID = playerID;
    }

    public void setNodeRole(SnakesProto.NodeRole nodeRole) {
        if (this.nodeRole == nodeRole) return;

        this.nodeRole = nodeRole;

        if (this.nodeRole == SnakesProto.NodeRole.MASTER) {
            discoverer.closeSender();
            discoverer.sendAnnouncementMessage(snakeGame, gameConfig);
        } else {
            discoverer.closeSender();
        }
    }
}