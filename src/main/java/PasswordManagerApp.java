import java.util.List;
import java.util.Map;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

public class PasswordManagerApp extends Application {

    private String masterPassword;
    private final LocalApiServer apiServer = new LocalApiServer();
    private Stage primaryStage;
    private Scene scene;

    private static final Map<String, String> LOGIN_URLS = Map.ofEntries(
            Map.entry("google", "https://accounts.google.com"),
            Map.entry("gmail", "https://accounts.google.com"),
            Map.entry("youtube", "https://accounts.google.com"),
            Map.entry("netflix", "https://www.netflix.com/login"),
            Map.entry("amazon", "https://www.amazon.com/gp/sign-in.html"),
            Map.entry("playstation", "https://my.account.sony.com"),
            Map.entry("sony", "https://my.account.sony.com"),
            Map.entry("steam", "https://store.steampowered.com/login"),
            Map.entry("github", "https://github.com/login"),
            Map.entry("discord", "https://discord.com/login"),
            Map.entry("reddit", "https://www.reddit.com/login"),
            Map.entry("twitter", "https://x.com/login"),
            Map.entry("x", "https://x.com/login"),
            Map.entry("instagram", "https://www.instagram.com/accounts/login/"),
            Map.entry("facebook", "https://www.facebook.com/login"),
            Map.entry("spotify", "https://accounts.spotify.com/login"),
            Map.entry("twitch", "https://www.twitch.tv/login"),
            Map.entry("epicgames", "https://www.epicgames.com/id/login"),
            Map.entry("epic", "https://www.epicgames.com/id/login"),
            Map.entry("riot", "https://authenticate.riotgames.com"),
            Map.entry("microsoft", "https://login.live.com"),
            Map.entry("outlook", "https://login.live.com"),
            Map.entry("apple", "https://account.apple.com"),
            Map.entry("paypal", "https://www.paypal.com/signin"),
            Map.entry("linkedin", "https://www.linkedin.com/login"),
            Map.entry("tiktok", "https://www.tiktok.com/login"),
            Map.entry("crunchyroll", "https://sso.crunchyroll.com/login"),
            Map.entry("roblox", "https://www.roblox.com/login")
    );

    private String loginUrlFor(String site) {
        String key = site.toLowerCase().trim();
        if (LOGIN_URLS.containsKey(key)) return LOGIN_URLS.get(key);
        if (key.contains(".")) return key.startsWith("http") ? key : "https://" + key;
        return "https://www." + key + ".com";
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        DatabaseManager.init();
        apiServer.start(() -> masterPassword);
        stage.setTitle("Password Vault");

        scene = new Scene(new StackPane(), 420, 480);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setScene(scene);

        showLoginScreen();
        stage.show();
    }

    @Override
    public void stop() {
        apiServer.stop();
    }

    private void showLoginScreen() {
        boolean firstTime = !DatabaseManager.masterPasswordIsSet();

        Label title = new Label("Password Vault");
        title.getStyleClass().add("title-label");

        Label subtitle = new Label(firstTime
                ? "Create a master password to get started"
                : "Enter your master password");
        subtitle.getStyleClass().add("subtitle-label");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(260);
        subtitle.setTextAlignment(TextAlignment.CENTER);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Master password");
        passwordField.getStyleClass().add("password-field");

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirm master password");
        confirmField.getStyleClass().add("password-field");
        confirmField.setManaged(firstTime);
        confirmField.setVisible(firstTime);

        Label errorLabel = new Label();
        errorLabel.setWrapText(true);

        Button actionButton = new Button(firstTime ? "Create Vault" : "Unlock");
        actionButton.getStyleClass().add("primary-button");
        actionButton.setMaxWidth(Double.MAX_VALUE);
        addHoverScale(actionButton);

        VBox card = new VBox(14, title, subtitle, passwordField);
        if (firstTime) {
            card.getChildren().add(confirmField);
        }
        card.getChildren().addAll(errorLabel, actionButton);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(300);

        actionButton.setOnAction(e -> {
            String pw = passwordField.getText();
            if (pw == null || pw.isBlank()) {
                showError(errorLabel, "Password cannot be empty.");
                shake(card);
                return;
            }
            if (firstTime) {
                String confirm = confirmField.getText();
                if (!pw.equals(confirm)) {
                    showError(errorLabel, "Passwords don't match.");
                    shake(card);
                    return;
                }
                DatabaseManager.setupMasterPassword(pw);
                masterPassword = pw;
                showVaultScreen();
            } else {
                if (DatabaseManager.checkMasterPassword(pw)) {
                    masterPassword = pw;
                    showVaultScreen();
                } else {
                    showError(errorLabel, "Incorrect master password.");
                    shake(card);
                    passwordField.clear();
                }
            }
        });

        passwordField.setOnAction(e -> actionButton.fire());
        confirmField.setOnAction(e -> actionButton.fire());

        StackPane root = new StackPane(card);
        root.getStyleClass().add("root");
        root.setPadding(new Insets(40));

        primaryStage.setWidth(420);
        primaryStage.setHeight(480);
        scene.setRoot(root);
        fadeIn(root);
        passwordField.requestFocus();
    }

    private void showVaultScreen() {
        Label title = new Label("Password Vault");
        title.getStyleClass().add("title-label");
        title.setStyle("-fx-font-size: 20px;");

        Button lockButton = new Button("Lock Vault");
        lockButton.getStyleClass().add("secondary-button");
        addHoverScale(lockButton);
        lockButton.setOnAction(e -> {
            masterPassword = null;
            showLoginScreen();
        });

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(title, headerSpacer, lockButton);
        header.setAlignment(Pos.CENTER_LEFT);

        TextField siteField = new TextField();
        siteField.setPromptText("Site (e.g. netflix)");
        siteField.getStyleClass().add("text-field");

        TextField userField = new TextField();
        userField.setPromptText("Username");
        userField.getStyleClass().add("text-field");

        TextField passwordInput = new TextField();
        passwordInput.setPromptText("Password (empty = generate)");
        passwordInput.getStyleClass().add("text-field");
        Tooltip.install(passwordInput, new Tooltip("Type your existing password, or leave empty to auto-generate"));

        Button diceButton = new Button("\uD83C\uDFB2");
        diceButton.getStyleClass().add("icon-button");
        Tooltip.install(diceButton, new Tooltip("Generate a strong password"));
        addHoverScale(diceButton);
        diceButton.setOnAction(e -> passwordInput.setText(PasswordGenerator.generatePassword(12)));

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add("primary-button");
        addHoverScale(saveButton);

        Label statusLabel = new Label();

        TextField searchField = new TextField();
        searchField.setPromptText("Search by site or username...");
        searchField.getStyleClass().add("text-field");

        ObservableList<PasswordRow> rows = FXCollections.observableArrayList();
        FilteredList<PasswordRow> filteredRows = new FilteredList<>(rows, p -> true);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal == null ? "" : newVal.toLowerCase();
            filteredRows.setPredicate(row ->
                    row.getSite().toLowerCase().contains(filter)
                    || row.getUsername().toLowerCase().contains(filter));
        });

        TableView<PasswordRow> table = new TableView<>(filteredRows);
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Label emptyLabel = new Label("Your vault is empty. Add your first password above.");
        emptyLabel.getStyleClass().add("empty-label");
        table.setPlaceholder(emptyLabel);

        TableColumn<PasswordRow, String> siteCol = new TableColumn<>("Site");
        siteCol.setCellValueFactory(d -> d.getValue().siteProperty());

        TableColumn<PasswordRow, String> userCol = new TableColumn<>("Username");
        userCol.setCellValueFactory(d -> d.getValue().usernameProperty());

        TableColumn<PasswordRow, String> passCol = new TableColumn<>("Password");
        passCol.setCellValueFactory(d -> d.getValue().displayPasswordProperty());

        TableColumn<PasswordRow, Void> openCol = new TableColumn<>("");
        openCol.setCellFactory(col -> new TableCell<>() {
            private final Button openBtn = new Button("Open");
            {
                openBtn.getStyleClass().add("icon-button");
                addHoverScale(openBtn);
                openBtn.setOnAction(e -> {
                    PasswordRow row = getTableView().getItems().get(getIndex());
                    getHostServices().showDocument(loginUrlFor(row.getSite()));
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : openBtn);
            }
        });
        openCol.setMaxWidth(80);
        openCol.setMinWidth(80);

        TableColumn<PasswordRow, Void> actionCol = new TableColumn<>("");
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button copyBtn = new Button("Copy");
            {
                copyBtn.getStyleClass().add("icon-button");
                addHoverScale(copyBtn);
                copyBtn.setOnAction(e -> {
                    PasswordRow row = getTableView().getItems().get(getIndex());
                    ClipboardContent content = new ClipboardContent();
                    content.putString(row.getActualPassword());
                    Clipboard.getSystemClipboard().setContent(content);
                    copyBtn.setText("Copied!");
                    PauseTransition reset = new PauseTransition(Duration.seconds(1.2));
                    reset.setOnFinished(ev -> copyBtn.setText("Copy"));
                    reset.play();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : copyBtn);
            }
        });
        actionCol.setMaxWidth(90);
        actionCol.setMinWidth(90);

        table.getColumns().addAll(siteCol, userCol, passCol, openCol, actionCol);

        ToggleButton revealToggle = new ToggleButton("Show Passwords");
        revealToggle.getStyleClass().add("secondary-button");
        addHoverScale(revealToggle);
        revealToggle.setOnAction(e -> {
            boolean show = revealToggle.isSelected();
            revealToggle.setText(show ? "Hide Passwords" : "Show Passwords");
            for (PasswordRow row : rows) {
                row.setRevealed(show);
            }
        });

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("secondary-button");
        addHoverScale(refreshButton);
        refreshButton.setOnAction(e -> {
            rows.clear();
            loadEntries(rows, revealToggle, statusLabel);
            showSuccessToast(statusLabel, "Refreshed.");
        });

        Button deleteButton = new Button("Remove Selected");
        deleteButton.getStyleClass().add("secondary-button");
        addHoverScale(deleteButton);
        deleteButton.setOnAction(e -> {
            PasswordRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError(statusLabel, "Select a row to delete.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete the saved password for " + selected.getSite() + "?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.setTitle("Confirm Delete");
            confirm.getDialogPane().getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    try {
                        DatabaseManager.deletePassword(selected.getSite(), selected.getUsername());
                        rows.remove(selected);
                        showSuccessToast(statusLabel, "Deleted.");
                    } catch (Exception ex) {
                        showError(statusLabel, "Failed to delete: " + ex.getMessage());
                    }
                }
            });
        });

        saveButton.setOnAction(e -> {
            String site = siteField.getText().trim();
            String user = userField.getText().trim();
            if (site.isEmpty() || user.isEmpty()) {
                showError(statusLabel, "Site and username cannot be empty.");
                return;
            }
            String typed = passwordInput.getText();
            String password = (typed == null || typed.isBlank())
                    ? PasswordGenerator.generatePassword(12)
                    : typed;
            try {
                DatabaseManager.savePassword(site, user, password, masterPassword);
                rows.add(new PasswordRow(site, user, password, revealToggle.isSelected()));
                siteField.clear();
                userField.clear();
                passwordInput.clear();
                showSuccessToast(statusLabel, "Saved!");
            } catch (Exception ex) {
                showError(statusLabel, "Failed to save: " + ex.getMessage());
            }
        });

        loadEntries(rows, revealToggle, statusLabel);

        HBox passwordBox = new HBox(6, passwordInput, diceButton);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(passwordInput, Priority.ALWAYS);

        HBox inputRow = new HBox(10, siteField, userField, passwordBox, saveButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(siteField, Priority.ALWAYS);
        HBox.setHgrow(userField, Priority.ALWAYS);
        HBox.setHgrow(passwordBox, Priority.ALWAYS);

        HBox actionRow = new HBox(10, revealToggle, refreshButton, deleteButton);

        VBox root = new VBox(14, header, inputRow, statusLabel, searchField, table, actionRow);
        root.getStyleClass().add("root");
        root.setPadding(new Insets(24));
        VBox.setVgrow(table, Priority.ALWAYS);

        primaryStage.setWidth(820);
        primaryStage.setHeight(600);
        scene.setRoot(root);
        fadeIn(root);
    }

    private void loadEntries(ObservableList<PasswordRow> rows, ToggleButton revealToggle, Label statusLabel) {
        try {
            List<DatabaseManager.PasswordEntry> entries = DatabaseManager.getAllPasswords();
            for (DatabaseManager.PasswordEntry entry : entries) {
                String decrypted = DatabaseManager.decryptEntry(entry, masterPassword);
                rows.add(new PasswordRow(entry.site, entry.username, decrypted, revealToggle.isSelected()));
            }
        } catch (Exception ex) {
            showError(statusLabel, "Failed to load passwords: " + ex.getMessage());
        }
    }

    private void fadeIn(Node node) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(280), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void shake(Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(60), node);
        tt.setFromX(0);
        tt.setByX(10);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
        tt.setOnFinished(e -> node.setTranslateX(0));
        tt.play();
    }

    private void addHoverScale(ButtonBase button) {
        ScaleTransition grow = new ScaleTransition(Duration.millis(120), button);
        grow.setToX(1.05);
        grow.setToY(1.05);
        ScaleTransition shrink = new ScaleTransition(Duration.millis(120), button);
        shrink.setToX(1.0);
        shrink.setToY(1.0);
        button.setOnMouseEntered(e -> { shrink.stop(); grow.playFromStart(); });
        button.setOnMouseExited(e -> { grow.stop(); shrink.playFromStart(); });
    }

    private void showError(Label label, String message) {
        label.getStyleClass().removeAll("toast-label");
        if (!label.getStyleClass().contains("error-label")) {
            label.getStyleClass().add("error-label");
        }
        label.setOpacity(1);
        label.setText(message);
    }

    private void showSuccessToast(Label label, String message) {
        label.getStyleClass().removeAll("error-label");
        if (!label.getStyleClass().contains("toast-label")) {
            label.getStyleClass().add("toast-label");
        }
        label.setText(message);
        label.setOpacity(1);
        PauseTransition pause = new PauseTransition(Duration.seconds(2));
        FadeTransition fade = new FadeTransition(Duration.millis(500), label);
        fade.setFromValue(1);
        fade.setToValue(0);
        new SequentialTransition(pause, fade).play();
    }

    public static class PasswordRow {
        private final SimpleStringProperty site;
        private final SimpleStringProperty username;
        private final String actualPassword;
        private final SimpleStringProperty displayPassword;

        public PasswordRow(String site, String username, String password, boolean revealed) {
            this.site = new SimpleStringProperty(site);
            this.username = new SimpleStringProperty(username);
            this.actualPassword = password;
            this.displayPassword = new SimpleStringProperty(revealed ? password : mask(password));
        }

        private String mask(String pw) {
            return "\u2022".repeat(Math.min(pw.length(), 12));
        }

        public void setRevealed(boolean revealed) {
            displayPassword.set(revealed ? actualPassword : mask(actualPassword));
        }

        public String getSite() { return site.get(); }
        public String getUsername() { return username.get(); }
        public String getActualPassword() { return actualPassword; }
        public SimpleStringProperty siteProperty() { return site; }
        public SimpleStringProperty usernameProperty() { return username; }
        public SimpleStringProperty displayPasswordProperty() { return displayPassword; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}