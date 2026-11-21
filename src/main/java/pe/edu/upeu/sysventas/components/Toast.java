package pe.edu.upeu.sysventas.components;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Notificación flotante ("toast") moderna: aparece abajo a la derecha,
 * con ícono de éxito o error, y se desvanece sola.
 */
public class Toast {

    /** Muestra el toast con el estilo del tema actual. */
    public static void mostrar(Stage owner, String mensaje, boolean exito) {
        if (owner == null || owner.getScene() == null) return;

        Label texto = new Label(mensaje);
        texto.setWrapText(true);
        texto.setMaxWidth(340);
        HBox caja = new HBox(Iconos.crear(exito ? "check" : "error", 20), texto);
        caja.getStyleClass().addAll("toast", exito ? "exito" : "error");
        caja.getStylesheets().setAll(owner.getScene().getStylesheets());
        caja.setOpacity(0);

        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.getContent().add(caja);
        popup.show(owner);
        // Esquina inferior derecha de la ventana
        caja.applyCss();
        caja.layout();
        double ancho = Math.max(caja.prefWidth(-1), 220);
        popup.setX(owner.getX() + owner.getWidth() - ancho - 40);
        popup.setY(owner.getY() + owner.getHeight() - 120);

        FadeTransition entra = new FadeTransition(Duration.millis(220), caja);
        entra.setToValue(1);
        TranslateTransition sube = new TranslateTransition(Duration.millis(220), caja);
        sube.setFromY(18);
        sube.setToY(0);
        FadeTransition sale = new FadeTransition(Duration.millis(300), caja);
        sale.setToValue(0);

        SequentialTransition anim = new SequentialTransition(
                new ParallelTransition(entra, sube), new PauseTransition(Duration.millis(2600)), sale);
        anim.setOnFinished(e -> popup.hide());
        anim.play();
    }

    /* ---- Métodos anteriores (se mantienen por compatibilidad) ---- */

    public static void showToast(Stage ownerStage, String message, String colorFondo, int durationInMillis, double x, double y) {
        mostrar(ownerStage, message, !"#fecaca".equalsIgnoreCase(colorFondo));
    }

    public static void showToastMain(Stage ownerStage, String message, String colorFondo, int durationInMillis, double x, double y) {
        mostrar(ownerStage, message, !"#fecaca".equalsIgnoreCase(colorFondo));
    }

    public static void showToast(Stage ownerStage, String message, int durationInMillis, double x, double y) {
        mostrar(ownerStage, message, true);
    }
}
