package pe.edu.upeu.sysventas.components;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

import java.util.Map;

/**
 * Íconos vectoriales (SVG) dibujados en una cuadrícula de 24 x 24.
 * No dependen de imágenes ni de librerías externas y toman el color del tema
 * mediante la clase CSS "icono" (propiedad -fx-fill).
 *
 * Uso: Iconos.crear("venta", 18)
 */
public final class Iconos {

    private Iconos() {
    }

    private static final Map<String, String> PATHS = Map.ofEntries(
            Map.entry("inicio", "M 3 3 h 8 v 10 H 3 z M 13 3 h 8 v 6 h -8 z M 13 11 h 8 v 10 h -8 z M 3 15 h 8 v 6 H 3 z"),
            Map.entry("venta", "M 2 3 h 3.2 l .6 2 H 22 l -2.6 9.4 c -.25 .95 -1.05 1.6 -2 1.6 H 9.1 c -.9 0 -1.7 -.6 -2 -1.5 L 4 5 H 2 z "
                    + "M 9 18.5 a 1.75 1.75 0 1 0 0 3.5 a 1.75 1.75 0 1 0 0 -3.5 z M 17 18.5 a 1.75 1.75 0 1 0 0 3.5 a 1.75 1.75 0 1 0 0 -3.5 z"),
            Map.entry("cliente", "M 9 4 a 4 4 0 1 0 0 8 a 4 4 0 1 0 0 -8 z M 1 20 c 0 -3.9 3.6 -6 8 -6 s 8 2.1 8 6 v 1 H 1 z "
                    + "M 16.5 4.6 a 3.4 3.4 0 1 1 0 6.8 c .9 -1 1.4 -2.2 1.4 -3.4 s -.5 -2.4 -1.4 -3.4 z M 18.6 14.2 c 2.6 .6 4.4 2.5 4.4 5.8 v 1 h -4 v -1 c 0 -2.4 -.1 -4.3 -.4 -5.8 z"),
            Map.entry("historialventa", "M 12 2 a 10 10 0 1 0 0 20 a 10 10 0 1 0 0 -20 z M 12 4 a 8 8 0 1 1 0 16 a 8 8 0 1 1 0 -16 z "
                    + "M 11 6.5 h 2 v 5.3 l 3.6 2.1 -1 1.7 -4.6 -2.7 z"),
            Map.entry("producto", "M 8 3 L 2.6 6 l 1.9 4.6 2.5 -1 V 21 h 10 V 9.6 l 2.5 1 L 21.4 6 16 3 c -.5 1.7 -2.1 2.9 -4 2.9 S 8.5 4.7 8 3 z"),
            Map.entry("catalogo", "M 3 3 h 8.2 l 9.8 9.8 -8.2 8.2 L 3 11.2 z M 7.5 6 a 1.5 1.5 0 1 0 0 3 a 1.5 1.5 0 1 0 0 -3 z"),
            Map.entry("compra", "M 4 7 h 16 l -1.2 13.2 c -.1 1 -.9 1.8 -2 1.8 H 7.2 c -1 0 -1.9 -.8 -2 -1.8 z "
                    + "M 7.5 7.2 V 6 a 4.5 4.5 0 0 1 9 0 v 1.2 h -2 V 6 a 2.5 2.5 0 0 0 -5 0 v 1.2 z"),
            Map.entry("proveedor", "M 1 5 h 13 v 11 H 1 z M 14 8 h 4.6 l 3.4 4.2 V 16 h -8 z "
                    + "M 5.5 15 a 2.5 2.5 0 1 0 0 5 a 2.5 2.5 0 1 0 0 -5 z M 17.5 15 a 2.5 2.5 0 1 0 0 5 a 2.5 2.5 0 1 0 0 -5 z"),
            Map.entry("historialcompra", "M 5 2 h 14 v 20 l -2.3 -1.6 L 14.3 22 12 20.4 9.7 22 l -2.4 -1.6 L 5 22 z "
                    + "M 8 7 h 8 v 2 H 8 z M 8 11 h 8 v 2 H 8 z M 8 15 h 5 v 2 H 8 z"),
            Map.entry("usuario", "M 12 2 l 8 3 v 6 c 0 5 -3.4 9.4 -8 11 -4.6 -1.6 -8 -6 -8 -11 V 5 z M 12 6.8 a 2.6 2.6 0 1 0 0 5.2 a 2.6 2.6 0 1 0 0 -5.2 z "
                    + "M 7.6 16.6 c .8 -1.8 2.5 -2.9 4.4 -2.9 s 3.6 1.1 4.4 2.9 c -1.1 1.3 -2.7 2.4 -4.4 3 -1.7 -.6 -3.3 -1.7 -4.4 -3 z"),
            Map.entry("salir", "M 11 3 H 4 v 18 h 7 v -2 H 6 V 5 h 5 z M 15.5 7 l -1.4 1.4 2.6 2.6 H 9 v 2 h 7.7 l -2.6 2.6 1.4 1.4 5 -5 z"),
            Map.entry("tendencia", "M 2 16.6 L 9 9.6 l 4 4 5.6 -5.6 H 15 V 6 h 7 v 7 h -2 V 9.4 l -7 7 -4 -4 -5.6 5.6 z"),
            Map.entry("billetera", "M 2 6 h 18 a 2 2 0 0 1 2 2 v 10 a 2 2 0 0 1 -2 2 H 4 a 2 2 0 0 1 -2 -2 z M 4 3 h 13 v 2 H 4 z M 15.5 11 a 2 2 0 1 0 0 4 H 22 v -4 z"),
            Map.entry("ticket", "M 3 5 h 18 v 4.5 a 2.5 2.5 0 0 0 0 5 V 19 H 3 v -4.5 a 2.5 2.5 0 0 0 0 -5 z M 9 7 h 2 v 2 H 9 z M 9 11 h 2 v 2 H 9 z M 9 15 h 2 v 2 H 9 z"),
            Map.entry("caja", "M 12 2 l 9 4.5 v 11 L 12 22 l -9 -4.5 v -11 z M 12 4.3 L 5.6 7.5 12 10.7 l 6.4 -3.2 z"),
            Map.entry("alerta", "M 12 2 L 23 21 H 1 z M 11 9 h 2 v 6 h -2 z M 11 16.5 h 2 v 2 h -2 z"),
            Map.entry("calendario", "M 3 5 h 18 v 16 H 3 z M 5 9 h 14 v 10 H 5 z M 7 2 h 2 v 3 H 7 z M 15 2 h 2 v 3 h -2 z"),
            Map.entry("buscar", "M 10 3 a 7 7 0 1 0 0 14 a 7 7 0 1 0 0 -14 z M 10 5 a 5 5 0 1 1 0 10 a 5 5 0 1 1 0 -10 z M 14.8 16.2 l 1.4 -1.4 5.3 5.3 -1.4 1.4 z"),
            Map.entry("refrescar", "M 12 4 a 8 8 0 0 1 7.4 5 H 22 l -3.5 4.5 L 15 9 h 2.2 A 6 6 0 1 0 18 14 h 2.1 A 8 8 0 1 1 12 4 z"),
            Map.entry("menu", "M 3 5 h 18 v 2 H 3 z M 3 11 h 18 v 2 H 3 z M 3 17 h 18 v 2 H 3 z"),
            Map.entry("editar", "M 3 17.3 V 21 h 3.7 L 17.8 9.9 l -3.7 -3.7 z M 20.7 7 c .4 -.4 .4 -1 0 -1.4 l -2.3 -2.3 c -.4 -.4 -1 -.4 -1.4 0 l -1.8 1.8 3.7 3.7 z"),
            Map.entry("eliminar", "M 6 7 h 12 l -1 14 H 7 z M 4 4 h 5 l 1 -1 h 4 l 1 1 h 5 v 2 H 4 z"),
            Map.entry("check", "M 12 2 a 10 10 0 1 0 0 20 a 10 10 0 1 0 0 -20 z M 10.5 16.2 l -4.2 -4.2 1.4 -1.4 2.8 2.8 6 -6 1.4 1.4 z"),
            Map.entry("error", "M 12 2 a 10 10 0 1 0 0 20 a 10 10 0 1 0 0 -20 z M 11 6.5 h 2 v 7 h -2 z M 11 15.5 h 2 v 2 h -2 z"),
            Map.entry("estrella", "M 12 2 l 3 6.5 7 .8 -5.2 4.8 1.5 6.9 L 12 17.5 5.7 21 l 1.5 -6.9 L 2 9.3 l 7 -.8 z"),
            Map.entry("percha", "M 12 3 a 3 3 0 0 0 -3 3 h 2 a 1 1 0 1 1 1.6 .8 L 11 8 v 2.3 L 2.6 16.2 C 1.6 16.9 2.1 18.5 3.3 18.5 h 17.4 c 1.2 0 1.7 -1.6 .7 -2.3 L 13 10.3 V 8.9 l .8 -.6 A 3 3 0 0 0 12 3 z M 12 12.4 l 6.3 4.1 H 5.7 z"),
            Map.entry("ojo", "M 12 5 C 6.5 5 2.6 9.2 1 12 c 1.6 2.8 5.5 7 11 7 s 9.4 -4.2 11 -7 c -1.6 -2.8 -5.5 -7 -11 -7 z M 12 8 a 4 4 0 1 1 0 8 a 4 4 0 1 1 0 -8 z M 12 10 a 2 2 0 1 0 0 4 a 2 2 0 1 0 0 -4 z"),
            Map.entry("reporte", "M 5 2 h 10 l 4 4 v 16 H 5 z M 7 4 v 16 h 10 V 7 h -3 V 4 z M 8.5 18 h 2 v -5 h -2 z M 11.5 18 h 2 v -8 h -2 z M 14.5 18 h 2 v -3 h -2 z"),
            Map.entry("descargar", "M 11 3 h 2 v 9.2 l 3.3 -3.3 1.4 1.4 L 12 16 6.3 10.3 l 1.4 -1.4 3.3 3.3 z M 4 18 h 16 v 3 H 4 z"),
            Map.entry("imprimir", "M 7 3 h 10 v 5 H 7 z M 3 9 h 18 v 8 h -3 v -3 H 6 v 3 H 3 z M 7 15 h 10 v 6 H 7 z"),
            Map.entry("play", "M 7 4 l 13 8 -13 8 z"),
            Map.entry("candado", "M 6 10 V 7 a 6 6 0 0 1 12 0 v 3 h 1 v 12 H 5 V 10 z M 8 10 h 8 V 7 a 4 4 0 0 0 -8 0 z M 11 14 h 2 v 4 h -2 z")
    );

    /** Crea el ícono dentro de una caja cuadrada del tamaño indicado (en píxeles). */
    public static Node crear(String nombre, double tamanio) {
        SVGPath svg = new SVGPath();
        svg.setContent(PATHS.getOrDefault(nombre, PATHS.get("inicio")));
        svg.setFillRule(FillRule.EVEN_ODD);
        svg.getStyleClass().add("icono");
        double escala = tamanio / 24.0;
        svg.setScaleX(escala);
        svg.setScaleY(escala);

        StackPane caja = new StackPane(svg);
        caja.getStyleClass().add("icono-caja");
        caja.setMinSize(tamanio, tamanio);
        caja.setPrefSize(tamanio, tamanio);
        caja.setMaxSize(tamanio, tamanio);
        return caja;
    }

    public static boolean existe(String nombre) {
        return PATHS.containsKey(nombre);
    }
}
