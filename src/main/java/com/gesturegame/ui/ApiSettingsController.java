package com.gesturegame.ui;

import com.gesturegame.ai.LlmConfiguration;
import com.gesturegame.ai.LlmConnectionTester;
import com.gesturegame.persistence.LlmSettingsStore;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 程序内 LLM API 设置面板。 */
public final class ApiSettingsController {

    private static final Logger LOGGER = Logger.getLogger(ApiSettingsController.class.getName());

    @FXML private ComboBox<String> providerCombo;
    @FXML private TextField apiUrlField;
    @FXML private TextField modelField;
    @FXML private PasswordField apiKeyField;
    @FXML private Label statusLabel;
    @FXML private Label sourceLabel;
    @FXML private Button testButton;
    @FXML private Button saveButton;

    private final LlmSettingsStore settingsStore = LlmSettingsStore.getInstance();
    private Stage stage;
    private boolean loading;

    public static void show(Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    ApiSettingsController.class.getResource("/fxml/ApiSettings.fxml")));
            Parent root = loader.load();
            ApiSettingsController controller = loader.getController();
            Stage dialog = new Stage(StageStyle.TRANSPARENT);
            dialog.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) dialog.initOwner(owner);
            Scene scene = new Scene(root, 620, 650);
            scene.setFill(Color.TRANSPARENT);
            dialog.setScene(scene);
            dialog.setTitle("LLM API 设置");
            controller.stage = dialog;
            dialog.show();
        } catch (IOException | RuntimeException error) {
            LOGGER.log(Level.SEVERE, "无法打开 API 设置面板", error);
        }
    }

    @FXML
    public void initialize() {
        providerCombo.getItems().setAll("DeepSeek", "其他兼容接口");
        loadConfiguration();
    }

    @FXML
    private void providerChanged() {
        if (loading) return;
        if ("DeepSeek".equals(providerCombo.getValue())) {
            apiUrlField.setText(LlmConfiguration.DEEPSEEK_URL);
            modelField.setText(LlmConfiguration.DEEPSEEK_MODEL);
        }
        showStatus("", false);
    }

    @FXML
    private void testConnection() {
        LlmConfiguration configuration = collectConfiguration();
        String validation = configuration.validationError();
        if (validation != null) {
            showStatus(validation, true);
            return;
        }
        setBusy(true);
        showStatus("正在连接接口并验证模型…", false);
        LlmConnectionTester.test(configuration).whenComplete((result, error) -> Platform.runLater(() -> {
            setBusy(false);
            if (error != null) {
                showStatus("连接测试失败，请检查网络和地址", true);
            } else {
                showStatus(result.message(), !result.success());
            }
        }));
    }

    @FXML
    private void save() {
        LlmConfiguration configuration = collectConfiguration();
        String validation = configuration.validationError();
        if (validation != null) {
            showStatus(validation, true);
            return;
        }
        try {
            settingsStore.save(configuration);
            showStatus("设置已保存，动态关卡和 AI 对手会立即使用", false);
            sourceLabel.setText("当前来源：程序内加密设置  ·  " + configuration.maskedKey());
        } catch (RuntimeException error) {
            LOGGER.log(Level.WARNING, "保存 API 设置失败", error);
            showStatus("保存失败，请查看启动日志", true);
        }
    }

    @FXML
    private void clearSaved() {
        try {
            settingsStore.clear();
            apiKeyField.clear();
            providerCombo.setValue("DeepSeek");
            apiUrlField.setText(LlmConfiguration.DEEPSEEK_URL);
            modelField.setText(LlmConfiguration.DEEPSEEK_MODEL);
            sourceLabel.setText("当前来源：未设置，将使用离线内容");
            showStatus("已删除程序内保存的 API Key", false);
        } catch (RuntimeException error) {
            LOGGER.log(Level.WARNING, "清除 API 设置失败", error);
            showStatus("清除失败，请查看启动日志", true);
        }
    }

    @FXML
    private void close() {
        if (stage != null) stage.close();
    }

    private void loadConfiguration() {
        loading = true;
        LlmConfiguration configuration = settingsStore.getForEditing();
        boolean deepSeek = "DeepSeek".equalsIgnoreCase(configuration.provider())
                || LlmConfiguration.DEEPSEEK_URL.equals(configuration.apiUrl());
        providerCombo.setValue(deepSeek ? "DeepSeek" : "其他兼容接口");
        apiUrlField.setText(configuration.apiUrl());
        modelField.setText(configuration.model());
        apiKeyField.setText(configuration.apiKey());
        loading = false;

        LlmConfiguration effective = settingsStore.getEffective();
        if (configuration.isConfigured()) {
            sourceLabel.setText("当前来源：程序内加密设置  ·  " + configuration.maskedKey());
        } else if (effective.isConfigured()) {
            sourceLabel.setText("当前来源：环境变量  ·  " + effective.maskedKey());
        } else {
            sourceLabel.setText("当前来源：未设置，将使用离线内容");
        }
    }

    private LlmConfiguration collectConfiguration() {
        String provider = "DeepSeek".equals(providerCombo.getValue())
                ? "DeepSeek" : "自定义兼容接口";
        return new LlmConfiguration(provider, apiUrlField.getText(), modelField.getText(), apiKeyField.getText());
    }

    private void setBusy(boolean busy) {
        testButton.setDisable(busy);
        saveButton.setDisable(busy);
        providerCombo.setDisable(busy);
        apiUrlField.setDisable(busy);
        modelField.setDisable(busy);
        apiKeyField.setDisable(busy);
    }

    private void showStatus(String text, boolean error) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("api-status-error", "api-status-success");
        if (text != null && !text.isBlank()) {
            statusLabel.getStyleClass().add(error ? "api-status-error" : "api-status-success");
        }
    }
}
