package pe.edu.upeu.sysventas.reporte;

import java.util.ArrayList;
import java.util.List;

/**
 * Una hoja ya maquetada: lista de "trazos" (rectángulos, líneas y textos) con
 * coordenadas en puntos (1/72 de pulgada), origen arriba a la izquierda.
 * La misma página se dibuja en pantalla y en el PDF, así lo que se ve es lo que se guarda.
 */
public final class Pagina {

    public sealed interface Trazo permits Rect, Linea, Texto { }

    /** relleno / borde: color RGB (0xRRGGBB) o -1 si no tiene. */
    public record Rect(double x, double y, double w, double h, int relleno, int borde) implements Trazo { }

    public record Linea(double x1, double y1, double x2, double y2, int color, double grosor) implements Trazo { }

    /** x es el punto de anclaje según la alineación; y es la línea base del texto. */
    public record Texto(double x, double y, String texto, boolean negrita, double tam, int color,
                        Reporte.Alin alin) implements Trazo { }

    private final double ancho;
    private final double alto;
    private final List<Trazo> trazos = new ArrayList<>();

    public Pagina(double ancho, double alto) {
        this.ancho = ancho;
        this.alto = alto;
    }

    public double ancho() { return ancho; }
    public double alto() { return alto; }
    public List<Trazo> trazos() { return trazos; }

    void rect(double x, double y, double w, double h, int relleno, int borde) {
        trazos.add(new Rect(x, y, w, h, relleno, borde));
    }

    void linea(double x1, double y1, double x2, double y2, int color, double grosor) {
        trazos.add(new Linea(x1, y1, x2, y2, color, grosor));
    }

    void texto(double x, double y, String t, boolean negrita, double tam, int color, Reporte.Alin alin) {
        if (t == null || t.isEmpty()) return;
        trazos.add(new Texto(x, y, t, negrita, tam, color, alin));
    }
}
