package pe.edu.upeu.sysventas.components;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Mensajes cortos (Toast) y confirmaciones para todas las pantallas. */
public final class Mensaje {

    private Mensaje() {
    }

    public static void exito(Node nodo, String texto) {
        mostrar(nodo, texto, true);
    }

    public static void error(Node nodo, String texto) {
        mostrar(nodo, texto, false);
    }

    private static void mostrar(Node nodo, String texto, boolean exito) {
        if (nodo == null || nodo.getScene() == null) return;
        Window w = nodo.getScene().getWindow();
        if (!(w instanceof Stage stage)) return;
        Toast.mostrar(stage, texto, exito);
    }

    public static boolean confirmar(String titulo, String texto) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, texto, ButtonType.YES, ButtonType.NO);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        prepararDialogo(alert);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    public static void info(String titulo, String texto) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, texto);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        prepararDialogo(alert);
        alert.showAndWait();
    }

    public static void errorDialogo(String titulo, String texto) {
        Alert alert = new Alert(Alert.AlertType.ERROR, texto);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        prepararDialogo(alert);
        alert.showAndWait();
    }

    /** Aplica el tema actual al diálogo y lo centra sobre la ventana principal. */
    private static void prepararDialogo(Alert alert) {
        Tema.aplicar(alert.getDialogPane());
        Stage principal = StageManager.getPrimaryStage();
        if (principal != null && principal.isShowing()) alert.initOwner(principal);
    }
}
