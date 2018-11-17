import org.controlsfx.control.CheckTreeView;

import java.util.Arrays;

import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class TestCTV extends Application {
	VBox rootp = new VBox(5);
	
	public static void main(String[] args) {
		launch(args);
	}
	
	@Override
	public void start(Stage primaryStage) {
		Scene scene = new Scene(rootp);
		primaryStage.setScene(scene);
		primaryStage.show();
		
		// create the data to show in the CheckTreeView
		CheckBoxTreeItem<String> root = new CheckBoxTreeItem<String>("Root");
		
		root.setExpanded(true);
		
		for (int i = 0; i < 5; i++) {
			CheckBoxTreeItem<String> node = new CheckBoxTreeItem<String>("Node " + i);
			root.getChildren().add(node);
			for (int j = 0; j < 5; j++)
				node.getChildren().add(new CheckBoxTreeItem<String>("Leaf " + i + "." + j));
		}
		
		// Create the CheckTreeView with the data
		final CheckTreeView<String> ctv = new CheckTreeView<>(root);
		ctv.getCheckModel().getCheckedItems().addListener((ListChangeListener) c ->
				System.out.println(Arrays.toString(ctv.getCheckModel().getCheckedItems().toArray())));
		
		rootp.getChildren().add(ctv);
		
	}
}