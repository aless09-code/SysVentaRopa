package pe.edu.upeu.sysventas.components;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** Aplica el estilo (CSS) elegido por el usuario a cualquier ventana o diálogo. */
public final class Tema {

    public static final List<String> TEMAS = List.of("Claro", "Oscuro", "Rosa", "Esmeralda");
    private static final Preferences PREFS = Preferences.userRoot().node("sysventaropa");

    private Tema() {
    }

    public static String actual() {
        return PREFS.get("tema", "Claro");
    }

    public static void guardar(String tema) {
        PREFS.put("tema", tema);
    }

    /** Hojas de estilo del tema actual: la base + la variante de color. */
    private static List<String> hojas() {
        List<String> lista = new ArrayList<>();
        lista.add(Tema.class.getResource("/css/style.css").toExternalForm());
        String variante = switch (actual()) {
            case "Oscuro" -> "/css/tema-oscuro.css";
            case "Rosa" -> "/css/tema-rosa.css";
            case "Esmeralda" -> "/css/tema-esmeralda.css";
            default -> null;
        };
        if (variante != null) lista.add(Tema.class.getResource(variante).toExternalForm());
        return lista;
    }

    public static void aplicar(Scene scene) {
        if (scene == null) return;
        scene.getStylesheets().setAll(hojas());
    }

    /** Para que las alertas y confirmaciones también usen el tema. */
    public static void aplicar(DialogPane pane) {
        if (pane == null) return;
        pane.getStylesheets().setAll(hojas());
    }
}
