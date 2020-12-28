package Graphic;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class CreateGameWindow {
    private static int sceneWidth = 1024;
    private static int sceneHeight = 768;
    private static int mainPaneSpaces = 10;
    private static int playerInterfaceWidth = 200;

    private static String name = "Unknown";
    private static int width = 20;
    private static int height = 20;
    private static int foodStatic = 1;
    private static float foodPerPlayer = 1.0f;
    private static int stateDelay = 1000;
    private static float deadFoodProb = 0.1f;
    private static int pingDelay = 10;
    private static int nodeTimeout = 5000;

    private static TextField nameInput;
    private static TextField widthInput;
    private static TextField heightInput;
    private static TextField foodStaticInput;
    private static TextField foodPerPlayerInput;
    private static TextField stateDelayInput;
    private static TextField deadFoodProbInput;
    private static TextField pingDelayInput;
    private static TextField nodeTimeoutInput;

    public static boolean created;

    public static boolean display(MainMenu main, Stage primaryStage) {
        Stage window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        window.setTitle("Create new game");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(mainPaneSpaces, mainPaneSpaces, mainPaneSpaces, mainPaneSpaces));
        grid.setVgap(mainPaneSpaces);
        grid.setHgap(mainPaneSpaces);

        Label nameLabel = new Label("Name:");
        GridPane.setConstraints(nameLabel, 0, 0);

        nameInput = new TextField();
        nameInput.setMaxWidth(playerInterfaceWidth);
        nameInput.setText("Unknown");
        GridPane.setConstraints(nameInput, 0, 1);

        Label widthLabel = new Label("Width (10 - 100):");
        GridPane.setConstraints(widthLabel, 0, 2);

        widthInput = new TextField();
        widthInput.setMaxWidth(playerInterfaceWidth);
        widthInput.setText("20");
        GridPane.setConstraints(widthInput, 0, 3);

        Label heightLabel = new Label("Height (10 - 100):");
        GridPane.setConstraints(heightLabel, 0, 4);

        heightInput = new TextField();
        heightInput.setMaxWidth(playerInterfaceWidth);
        heightInput.setText("20");
        GridPane.setConstraints(heightInput, 0, 5);

        Label foodStaticLabel = new Label("Food static (0 - 100):");
        GridPane.setConstraints(foodStaticLabel, 0, 6);

        foodStaticInput = new TextField();
        foodStaticInput.setMaxWidth(playerInterfaceWidth);
        foodStaticInput.setText("1");
        GridPane.setConstraints(foodStaticInput, 0, 7);

        Label foodPerPlayerLabel = new Label("Food per player (0 - 100):");
        GridPane.setConstraints(foodPerPlayerLabel, 0, 8);

        foodPerPlayerInput = new TextField();
        foodPerPlayerInput.setMaxWidth(playerInterfaceWidth);
        foodPerPlayerInput.setText("1");
        GridPane.setConstraints(foodPerPlayerInput, 0, 9);

        Label stateDelayLabel = new Label("State delay ms (0 - 10000):");
        GridPane.setConstraints(stateDelayLabel, 0, 10);

        stateDelayInput = new TextField();
        stateDelayInput.setMaxWidth(playerInterfaceWidth);
        stateDelayInput.setText("500");
        GridPane.setConstraints(stateDelayInput, 0, 11);

        Label deadFoodProbLabel = new Label("Dead food probability % (0 - 100):");
        GridPane.setConstraints(deadFoodProbLabel, 0, 12);

        deadFoodProbInput = new TextField();
        deadFoodProbInput.setMaxWidth(playerInterfaceWidth);
        deadFoodProbInput.setText("50");
        GridPane.setConstraints(deadFoodProbInput, 0, 13);

        Label pingDelayLabel = new Label("Ping delay ms (0 - 10000):");
        GridPane.setConstraints(pingDelayLabel, 0, 14);

        pingDelayInput = new TextField();
        pingDelayInput.setMaxWidth(playerInterfaceWidth);
        pingDelayInput.setText("10");
        GridPane.setConstraints(pingDelayInput, 0, 15);

        Label nodeTimeoutLabel = new Label("Node timeout ms (0 - 10000):");
        GridPane.setConstraints(nodeTimeoutLabel, 0, 16);

        nodeTimeoutInput = new TextField();
        nodeTimeoutInput.setMaxWidth(playerInterfaceWidth);
        nodeTimeoutInput.setText("5000");
        GridPane.setConstraints(nodeTimeoutInput, 0, 17);

        Button createButton = new Button("Create");
        VBox.setVgrow(createButton, Priority.ALWAYS);
        createButton.setMaxWidth(playerInterfaceWidth);
        createButton.setLineSpacing(mainPaneSpaces);
        createButton.setOnAction(actionEvent ->
        {
            try {
                name = nameInput.getText();
                width = Integer.parseInt(widthInput.getText());
                height = Integer.parseInt(heightInput.getText());
                foodStatic = Integer.parseInt(foodStaticInput.getText());
                foodPerPlayer = Float.parseFloat(foodPerPlayerInput.getText());
                deadFoodProb = Float.parseFloat(deadFoodProbInput.getText()) / 100;
                stateDelay = Integer.parseInt(stateDelayInput.getText());
                pingDelay = Integer.parseInt(pingDelayInput.getText());
                nodeTimeout = Integer.parseInt(nodeTimeoutInput.getText());

                if (!checkInputData()) {
                    return;
                }

                created = true;
                window.close();

            } catch (NumberFormatException ignored) {
                ErrorBox.display("Invalid data");
            }

            main.startGame();
        });
        GridPane.setConstraints(createButton, 0, 18);

        Button backButton = new Button("Back To Main Menu");

        VBox.setVgrow(backButton, Priority.ALWAYS);
        backButton.setMaxWidth(playerInterfaceWidth);
        backButton.setLineSpacing(mainPaneSpaces);
        backButton.setOnAction(actionEvent ->
        {
            window.close();
            main.showMainMenu(primaryStage);
        });
        GridPane.setConstraints(backButton, 0, 19);

        grid.setPadding(new Insets(sceneHeight / 10, 0, 0,
                (sceneWidth - playerInterfaceWidth) / 2));

        grid.getChildren().addAll(
                nameLabel, nameInput,
                widthLabel, widthInput,
                heightLabel, heightInput,
                foodStaticLabel, foodStaticInput,
                foodPerPlayerLabel, foodPerPlayerInput,
                stateDelayLabel, stateDelayInput,
                deadFoodProbLabel, deadFoodProbInput,
                pingDelayLabel, pingDelayInput,
                nodeTimeoutLabel, nodeTimeoutInput,
                createButton,
                backButton
        );
        Scene scene = new Scene(grid, sceneWidth, sceneHeight);

        window.setScene(scene);

        window.showAndWait();
        return created;
    }

    private static boolean checkInputData() {
        if (width < 10 || width > 100 ||
                height < 10 || height > 100 ||
                foodStatic < 0 || foodStatic > 100 ||
                foodPerPlayer < 0 || foodPerPlayer > 100 ||
                deadFoodProb < 0 || deadFoodProb > 100 ||
                stateDelay < 0 || stateDelay > 10000 ||
                pingDelay < 0 || pingDelay > 10000 ||
                nodeTimeout < 0 || nodeTimeout > 10000) {
            ErrorBox.display("Invalid data");
            return false;
        }
        return true;
    }

    public static String getName() {
        return name;
    }

    public static int getFoodStatic() {
        return foodStatic;
    }

    public static int getWidth() {
        return width;
    }

    public static float getDeadFoodProb() {
        return deadFoodProb;
    }

    public static float getFoodPerPlayer() {
        return foodPerPlayer;
    }

    public static int getHeight() {
        return height;
    }

    public static int getStateDelay() {
        return stateDelay;
    }

    public static int getPingDelay() {
        return pingDelay;
    }

    public static int getNodeTimeout() {
        return nodeTimeout;
    }
}