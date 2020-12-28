package Graphic;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import Network.AnnouncementSender;
import Logic.JoinInfo;
import Protobuf.SnakesProto;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import Network.NodeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainMenu extends Application {
    public int mainPaneSpaces = 10;
    private final int playerInterfaceWidth = 200;
    private static int sceneWidth = 1024;
    private static int sceneHeight = 768;

    private boolean startFlag = false;

    Scene mainMenuScene;
    Scene joinGameScene;
    GameWindow gameWindow;

    private static TextField nameInput;

    private ConcurrentHashMap<NodeInfo, SnakesProto.GameMessage.AnnouncementMsg> sessionInfoMap
            = new ConcurrentHashMap<>();
    AnnouncementSender announcementSender;
    TableView<JoinInfo> tableView = createTableViewColumns();

    private String name;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {

        try {
            announcementSender = new AnnouncementSender(sessionInfoMap);
            announcementSender.start();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        primaryStage.setTitle("Snake game");

        primaryStage.setOnCloseRequest(actionEvent ->
        {
            announcementSender.interrupt();
            Platform.exit();
            System.exit(0);
        });

        nameInput = new TextField();
        nameInput.setMaxWidth(playerInterfaceWidth);
        nameInput.setText("Unknown");

        Button joinButton = new Button("Join");
        joinButton.setMinWidth(playerInterfaceWidth);
        joinButton.setMaxWidth(playerInterfaceWidth);
        joinButton.setOnAction(actionEvent ->
        {
            name = nameInput.getText();
            ObservableList<JoinInfo> sessionSelected;
            sessionSelected = tableView.getSelectionModel().getSelectedItems();

            if (sessionSelected.size() == 0) {
                return;
            }

            JoinInfo joinInfo = sessionSelected.get(0);

            NodeInfo hostInfo = new NodeInfo(joinInfo.getAddress(), joinInfo.getPort());

            gameWindow = new GameWindow(joinInfo.getGameConfig(), announcementSender, name,
                    SnakesProto.NodeRole.NORMAL, hostInfo);

        });

        Button checkUpdateButton = new Button("Check Update");
        checkUpdateButton.setMinWidth(playerInterfaceWidth);
        checkUpdateButton.setMaxWidth(playerInterfaceWidth);
        checkUpdateButton.setOnAction(actionEvent ->
        {
            tableView.getItems().removeAll();
            tableView.setItems(getSessionsInfo());
        });

        Button backButton = new Button("Back To Main Menu");
        backButton.setMinWidth(playerInterfaceWidth);
        backButton.setMaxWidth(playerInterfaceWidth);
        backButton.setOnAction(actionEvent ->
        {
            showMainMenu(primaryStage);
        });

        VBox bottomMenu = new VBox(mainPaneSpaces);
        bottomMenu.setMinHeight(playerInterfaceWidth);
        bottomMenu.setMaxHeight(playerInterfaceWidth);
        bottomMenu.getChildren().addAll(nameInput, joinButton, checkUpdateButton, backButton);
        bottomMenu.setAlignment(Pos.CENTER);

        VBox layout = new VBox();
        layout.getChildren().addAll(tableView, bottomMenu);

        joinGameScene = new Scene(layout, sceneWidth, sceneHeight);
        createMainMenu(primaryStage);
        showMainMenu(primaryStage);
    }

    private TableView<JoinInfo> createTableViewColumns() {
        TableView<JoinInfo> tableView;

        TableColumn<JoinInfo, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setMinWidth(sceneWidth / 10);
        nameColumn.setMaxWidth(sceneWidth / 10);
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<JoinInfo, InetAddress> addressColumn = new TableColumn<>("Address");
        addressColumn.setMinWidth(sceneWidth / 10);
        addressColumn.setMaxWidth(sceneWidth / 10);
        addressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));

        TableColumn<JoinInfo, Integer> portColumn = new TableColumn<>("Port");
        portColumn.setMinWidth(sceneWidth / 10);
        portColumn.setMaxWidth(sceneWidth / 10);
        portColumn.setCellValueFactory(new PropertyValueFactory<>("port"));

        TableColumn<JoinInfo, Integer> widthColumn = new TableColumn<>("Width");
        widthColumn.setMinWidth(sceneWidth / 10);
        widthColumn.setMaxWidth(sceneWidth / 10);
        widthColumn.setCellValueFactory(new PropertyValueFactory<>("width"));

        TableColumn<JoinInfo, Integer> heightColumn = new TableColumn<>("Height");
        heightColumn.setMinWidth(sceneWidth / 10);
        heightColumn.setMaxWidth(sceneWidth / 10);
        heightColumn.setCellValueFactory(new PropertyValueFactory<>("height"));

        TableColumn<JoinInfo, Integer> foodStaticColumn = new TableColumn<>("Static Food");
        foodStaticColumn.setMinWidth(sceneWidth / 10);
        foodStaticColumn.setMaxWidth(sceneWidth / 10);
        foodStaticColumn.setCellValueFactory(new PropertyValueFactory<>("staticFood"));

        TableColumn<JoinInfo, Double> foodPerPlayerColumn = new TableColumn<>("Food Per Player");
        foodPerPlayerColumn.setMinWidth(sceneWidth / 10);
        foodPerPlayerColumn.setMaxWidth(sceneWidth / 10);
        foodPerPlayerColumn.setCellValueFactory(new PropertyValueFactory<>("foodPerPlayer"));

        TableColumn<JoinInfo, Double> deadDropProbColumn = new TableColumn<>("Dead Food Prob.");
        deadDropProbColumn.setMinWidth(sceneWidth / 10);
        deadDropProbColumn.setMaxWidth(sceneWidth / 10);
        deadDropProbColumn.setCellValueFactory(new PropertyValueFactory<>("deadDropProb"));

        TableColumn<JoinInfo, Integer> numOfPlayersColumn = new TableColumn<>("Num of players");
        numOfPlayersColumn.setMinWidth(sceneWidth / 10);
        numOfPlayersColumn.setMaxWidth(sceneWidth / 10);
        numOfPlayersColumn.setCellValueFactory(new PropertyValueFactory<>("numOfPlayers"));

        TableColumn<JoinInfo, Boolean> canJoinColumn = new TableColumn<>("Can join");
        canJoinColumn.setMinWidth(sceneWidth / 10);
        canJoinColumn.setMaxWidth(sceneWidth / 10);
        canJoinColumn.setCellValueFactory(new PropertyValueFactory<>("canJoin"));
        canJoinColumn.setSortType(TableColumn.SortType.DESCENDING);

        tableView = new TableView<>();
        tableView.setItems(getSessionsInfo());
        tableView.getColumns().addAll(
                nameColumn,
                addressColumn,
                portColumn,
                widthColumn,
                heightColumn,
                foodStaticColumn,
                foodPerPlayerColumn,
                deadDropProbColumn,
                numOfPlayersColumn,
                canJoinColumn
        );

        return tableView;
    }

    private ObservableList<JoinInfo> getSessionsInfo() {
        ObservableList<JoinInfo> sessionsInfo = FXCollections.observableArrayList();

        for (Map.Entry<NodeInfo, SnakesProto.GameMessage.AnnouncementMsg> entry : sessionInfoMap.entrySet()) {
            for (SnakesProto.GamePlayer gamePlayer : entry.getValue().getPlayers().getPlayersList()) {
                if (gamePlayer.getRole() == SnakesProto.NodeRole.MASTER) {
                    SnakesProto.GameConfig gameConfig = entry.getValue().getConfig();
                    sessionsInfo.add(new JoinInfo(
                            entry.getKey().getIp(), entry.getKey().getPort(), gamePlayer.getName(),
                            gameConfig.getWidth(), gameConfig.getHeight(), gameConfig.getFoodStatic(),
                            gameConfig.getFoodPerPlayer(), gameConfig.getDeadFoodProb(),
                            entry.getValue().getPlayers().getPlayersCount(), entry.getValue().getCanJoin(),
                            entry.getValue().getConfig())
                    );
                    break;
                }
            }
        }

        return sessionsInfo;
    }

    private SnakesProto.GameConfig createGameConfig() {
        return SnakesProto.GameConfig.newBuilder()
                .setWidth(CreateGameWindow.getWidth())
                .setHeight(CreateGameWindow.getHeight())
                .setFoodStatic(CreateGameWindow.getFoodStatic())
                .setFoodPerPlayer(CreateGameWindow.getFoodPerPlayer())
                .setStateDelayMs(CreateGameWindow.getStateDelay())
                .setDeadFoodProb(CreateGameWindow.getDeadFoodProb())
                .setPingDelayMs(CreateGameWindow.getPingDelay())
                .setNodeTimeoutMs(CreateGameWindow.getNodeTimeout())
                .build();
    }

    public void createMainMenu(Stage primaryStage) {
        Button newGameButton = new Button("New Game");
        VBox.setVgrow(newGameButton, Priority.ALWAYS);
        newGameButton.setMaxWidth(playerInterfaceWidth);
        newGameButton.setLineSpacing(mainPaneSpaces);
        newGameButton.setOnAction(actionEvent ->
        {
            name = CreateGameWindow.getName();
            CreateGameWindow.display(this, primaryStage);
            SnakesProto.GameConfig gameConfig = createGameConfig();
            if (startFlag) {
                gameWindow = new GameWindow(gameConfig, announcementSender, name, SnakesProto.NodeRole.MASTER);
            }
        });

        Button joinButton = new Button("Join");
        VBox.setVgrow(joinButton, Priority.ALWAYS);
        joinButton.setMaxWidth(playerInterfaceWidth);
        joinButton.setLineSpacing(mainPaneSpaces);
        joinButton.setOnAction(actionEvent ->
        {
            showJoinGame(primaryStage);
        });

        Button exitButton = new Button("Exit");
        VBox.setVgrow(exitButton, Priority.ALWAYS);
        exitButton.setMaxWidth(playerInterfaceWidth);
        exitButton.setLineSpacing(mainPaneSpaces);
        exitButton.setOnAction(actionEvent ->
        {
            Platform.exit();
            System.exit(0);
        });

        VBox buttons = new VBox(mainPaneSpaces, newGameButton, joinButton, exitButton);
        buttons.setPadding(new Insets(sceneHeight / 2 - mainPaneSpaces * 6, 0, 0,
                (sceneWidth - playerInterfaceWidth) / 2));
        buttons.setSpacing(10);
        buttons.setAlignment(Pos.BASELINE_LEFT);

        mainMenuScene = new Scene(buttons, sceneWidth, sceneHeight);
    }

    public void showMainMenu(Stage primaryStage) {
        primaryStage.setScene(mainMenuScene);
        primaryStage.show();
    }

    public void showJoinGame(Stage primaryStage) {
        primaryStage.setScene(joinGameScene);
        primaryStage.show();
    }

    public void startGame() {
        startFlag = true;
    }
}