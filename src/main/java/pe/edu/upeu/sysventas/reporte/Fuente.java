package pe.edu.upeu.sysventas.reporte;

import java.nio.charset.Charset;

/**
 * Medidas de la letra Helvetica (una de las 14 fuentes estándar de todo lector PDF),
 * para calcular el ancho de los textos sin incrustar archivos de fuente.
 * Los anchos están en milésimas del tamaño de letra, para los caracteres 32..255
 * de la codificación WinAnsi (Windows-1252), que incluye tildes, ñ, °, etc.
 */
public final class Fuente {

    private Fuente() {
    }

    public static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private static final int[] NORMAL = {
            278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556,
            556, 556, 278, 278, 584, 584, 584, 556, 1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556, 333, 556, 556, 500, 556, 556, 278, 556,
            556, 222, 222, 500, 222, 833, 556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584, 556,
            556, 278, 222, 556, 333, 1000, 556, 556, 333, 1000, 667, 333, 1000, 278, 611, 278, 278, 222, 221, 333, 333, 350, 556, 1000,
            333, 1000, 500, 333, 944, 278, 500, 667, 278, 333, 556, 556, 556, 556, 260, 556, 333, 737, 370, 556, 584, 333, 737, 333,
            606, 584, 556, 556, 333, 556, 537, 278, 333, 556, 365, 556, 869, 869, 869, 611, 667, 667, 667, 667, 667, 667, 1000, 722,
            667, 667, 667, 667, 278, 278, 278, 278, 722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 666, 666, 611,
            556, 556, 556, 556, 556, 556, 889, 500, 556, 556, 556, 556, 278, 278, 278, 278, 556, 556, 556, 556, 556, 556, 556, 584,
            611, 556, 556, 556, 556, 500, 555, 500};

    private static final int[] NEGRITA = {
            278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556,
            556, 556, 333, 333, 584, 584, 584, 611, 975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556, 333, 556, 611, 556, 611, 556, 333, 611,
            611, 278, 278, 556, 278, 889, 611, 611, 611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584, 556,
            556, 278, 278, 556, 500, 1000, 556, 556, 333, 1000, 667, 333, 1000, 278, 611, 278, 278, 278, 278, 500, 500, 350, 556, 1000,
            333, 1000, 556, 333, 944, 278, 500, 667, 278, 333, 556, 556, 556, 556, 280, 556, 333, 737, 370, 556, 584, 333, 737, 333,
            606, 584, 556, 556, 333, 611, 556, 278, 333, 556, 365, 556, 869, 869, 869, 611, 722, 722, 722, 722, 722, 722, 1000, 722,
            667, 667, 667, 667, 278, 278, 278, 278, 722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611,
            556, 556, 556, 556, 556, 556, 889, 556, 556, 556, 556, 556, 278, 278, 278, 278, 611, 611, 611, 611, 611, 611, 611, 584,
            611, 611, 611, 611, 611, 556, 611, 556};

    /** Convierte el texto a bytes WinAnsi; lo que no existe en esa tabla se cambia por '?'. */
    public static byte[] bytes(String s) {
        return limpiar(s).getBytes(WIN_ANSI);
    }

    /** Reemplaza caracteres que el PDF estándar no puede mostrar. */
    public static String limpiar(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        var enc = WIN_ANSI.newEncoder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '≤') sb.append("<=");
            else if (c == '≥') sb.append(">=");
            else if (c == '−') sb.append('-');
            else if (c == '\t' || c == '\n' || c == '\r') sb.append(' ');
            else if (c < 32) continue;
            else if (enc.canEncode(c)) sb.append(c);
            else sb.append('?');
        }
        return sb.toString();
    }

    /** Ancho del texto en puntos. */
    public static double ancho(String s, boolean negrita, double tam) {
        int[] tabla = negrita ? NEGRITA : NORMAL;
        double total = 0;
        for (byte b : bytes(s)) {
            int c = b & 0xFF;
            total += c >= 32 ? tabla[c - 32] : 0;
        }
        return total * tam / 1000.0;
    }

    /** Recorta el texto con "…" para que entre en el ancho indicado. */
    public static String ajustar(String s, boolean negrita, double tam, double max) {
        s = limpiar(s);
        if (ancho(s, negrita, tam) <= max) return s;
        String puntos = "…";
        int fin = s.length();
        while (fin > 0 && ancho(s.substring(0, fin) + puntos, negrita, tam) > max) fin--;
        return fin == 0 ? "" : s.substring(0, fin).stripTrailing() + puntos;
    }
}
