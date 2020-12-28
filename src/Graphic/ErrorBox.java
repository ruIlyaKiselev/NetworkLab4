package Graphic;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ErrorBox {
    public static void display(String error) {
        Stage window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        window.setTitle("Error");

        Label errorMess = new Label(error);

        Button ok = new Button("OK");
        ok.setOnAction(actionEvent -> window.close());

        VBox layout = new VBox(10);
        layout.setAlignment(Pos.CENTER);

        layout.getChildren().addAll(errorMess, ok);

        Scene scene = new Scene(layout, 10 * error.length(), 100);

        window.setScene(scene);
        window.show();
    }
}