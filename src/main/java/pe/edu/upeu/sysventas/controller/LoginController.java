package pe.edu.upeu.sysventas.controller;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import pe.edu.upeu.sysventas.components.Iconos;
import pe.edu.upeu.sysventas.components.StageManager;
import pe.edu.upeu.sysventas.components.Tema;
import pe.edu.upeu.sysventas.config.AppContext;
import pe.edu.upeu.sysventas.dto.SessionManager;
import pe.edu.upeu.sysventas.model.Usuario;
import pe.edu.upeu.sysventas.service.IUsuarioService;

@RequiredArgsConstructor
public class LoginController {

    // Servicio de usuarios (inyectado por AppContext)
    private final IUsuarioService usuarioService;

    @FXML HBox tarjeta;
    @FXML StackPane logo, campoUsuario, campoClave;
    @FXML VBox beneficios;
    @FXML TextField usuario, claveVisible;
    @FXML PasswordField clave;
    @FXML Button btnVerClave;
    @FXML Label lblError;

    @FXML
    public void initialize() {
        lblError.setText("");

        // Íconos vectoriales
        logo.getChildren().setAll(Iconos.crear("percha", 28));
        campoUsuario.getChildren().add(icono("cliente"));
        campoClave.getChildren().add(2, icono("candado"));
        btnVerClave.setGraphic(Iconos.crear("ojo", 18));

        // El campo "ver clave" comparte el texto con el PasswordField
        claveVisible.textProperty().bindBidirectional(clave.textProperty());

        beneficios.getChildren().setAll(
                beneficio("tendencia", "Dashboard con tus ventas al instante"),
                beneficio("venta", "Boletas y facturas con IGV automático"),
                beneficio("caja", "Control de stock y compras a proveedores"));

        // Animación de entrada de la tarjeta
        tarjeta.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(450), tarjeta);
        fade.setToValue(1);
        ScaleTransition escala = new ScaleTransition(Duration.millis(450), tarjeta);
        escala.setFromX(0.96);
        escala.setFromY(0.96);
        escala.setToX(1);
        escala.setToY(1);
        escala.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, escala).play();

        Platform.runLater(() -> usuario.requestFocus());
    }

    private Node icono(String nombre) {
        StackPane caja = new StackPane(Iconos.crear(nombre, 18));
        caja.getStyleClass().add("campo-icono");
        caja.setMouseTransparent(true);
        caja.setMaxWidth(30);
        StackPane.setAlignment(caja, Pos.CENTER_LEFT);
        return caja;
    }

    private HBox beneficio(String icono, String texto) {
        HBox fila = new HBox(Iconos.crear(icono, 16), new Label(texto));
        fila.getStyleClass().add("login-beneficio");
        return fila;
    }

    // Enter en el usuario pasa a la clave
    @FXML
    public void pasarAClave() {
        (claveVisible.isVisible() ? claveVisible : clave).requestFocus();
    }

    // Botón del ojo: muestra u oculta la clave
    @FXML
    public void alternarClave() {
        boolean mostrar = !claveVisible.isVisible();
        claveVisible.setVisible(mostrar);
        claveVisible.setManaged(mostrar);
        clave.setVisible(!mostrar);
        clave.setManaged(!mostrar);
        TextField activo = mostrar ? claveVisible : clave;
        activo.requestFocus();
        activo.positionCaret(activo.getText().length());
    }

    // Botón Ingresar
    @FXML
    public void login() {
        // Validación: los dos campos son obligatorios
        if (usuario.getText().isBlank() || clave.getText().isBlank()) {
            error("Ingrese usuario y clave");
            return;
        }
        try {
            // Busca el usuario y compara la clave encriptada (ver PasswordUtil)
            usuarioService.loginUsuario(usuario.getText().trim(), clave.getText())
                    .ifPresentOrElse(this::abrirMain,
                            () -> {
                                error("Usuario o clave incorrectos. Intente nuevamente.");
                                clave.clear();
                            });
        } catch (Exception ex) {
            error("No se pudo conectar a la base de datos");
            ex.printStackTrace();
        }
    }

    /** Muestra el error y "sacude" la tarjeta. */
    private void error(String texto) {
        lblError.setText(texto);
        TranslateTransition sacudir = new TranslateTransition(Duration.millis(60), tarjeta);
        sacudir.setFromX(0);
        sacudir.setByX(8);
        sacudir.setCycleCount(6);
        sacudir.setAutoReverse(true);
        sacudir.setOnFinished(e -> tarjeta.setTranslateX(0));
        sacudir.play();
    }

    // Login correcto: guarda los datos de la sesión y abre la ventana principal
    private void abrirMain(Usuario usu) {
        try {
            // SessionManager recuerda quién inició sesión (se usa al registrar ventas)
            SessionManager.getInstance().setUserId(usu.getIdUsuario());
            SessionManager.getInstance().setUserName(usu.getUsuario());
            SessionManager.getInstance().setUserPerfil(usu.getIdPerfil().getNombre());

            // Carga la ventana principal y le pide los controladores a AppContext
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/view/maingui.fxml"));
            fxmlLoader.setControllerFactory(AppContext.getInstance()::getBean);
            Rectangle2D r2d = Screen.getPrimary().getVisualBounds();
            Scene scene = new Scene(fxmlLoader.load(), r2d.getWidth(), r2d.getHeight());
            Tema.aplicar(scene);

            Stage stage = (Stage) usuario.getScene().getWindow();
            stage.setResizable(true);
            stage.setScene(scene);
            stage.setX(r2d.getMinX());
            stage.setY(r2d.getMinY());
            stage.setWidth(r2d.getWidth());
            stage.setHeight(r2d.getHeight());
            stage.setMinWidth(1100);
            stage.setMinHeight(680);
            StageManager.setPrimaryStage(stage);
            stage.show();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    public void salir() {
        Platform.exit();
    }
}
