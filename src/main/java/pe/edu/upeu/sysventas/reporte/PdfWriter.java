package pe.edu.upeu.sysventas.reporte;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Escribe las páginas de un reporte en un archivo PDF real (versión 1.4),
 * sin librerías externas: usa las fuentes estándar Helvetica / Helvetica-Bold
 * que traen todos los lectores de PDF (Adobe, Edge, Chrome, etc.).
 */
public final class PdfWriter {

    private PdfWriter() {
    }

    /** Maqueta el reporte y lo guarda como PDF en la ruta indicada. */
    public static void guardar(Reporte reporte, Path destino) throws IOException {
        Files.write(destino, generar(reporte.titulo(), Maquetador.maquetar(reporte)));
    }

    /** Devuelve los bytes del PDF con las páginas indicadas. */
    public static byte[] generar(String titulo, List<Pagina> paginas) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();
            escribir(out, "%PDF-1.4\n%âãÏÓ\n", true);

            int n = paginas.size();
            // Objetos: 1 catálogo, 2 árbol de páginas, 3 Helvetica, 4 Helvetica-Bold, 5 info,
            // luego por cada página: (página, contenido)
            int primeraPagina = 6;

            objeto(out, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");

            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < n; i++) kids.append(primeraPagina + i * 2).append(" 0 R ");
            objeto(out, offsets, 2, "<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + n + " >>");
            objeto(out, offsets, 3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
            objeto(out, offsets, 4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
            String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            objeto(out, offsets, 5, "<< /Title " + cadena(titulo) + " /Producer (SysVentaRopa) /Creator (SysVentaRopa)"
                    + " /CreationDate (D:" + fecha + ") >>");

            for (int i = 0; i < n; i++) {
                Pagina p = paginas.get(i);
                int idPag = primeraPagina + i * 2;
                int idCont = idPag + 1;
                objeto(out, offsets, idPag, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + num(p.ancho()) + " " + num(p.alto())
                        + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + idCont + " 0 R >>");

                byte[] contenido = comprimir(contenido(p));
                offsets.add(out.size());
                escribir(out, idCont + " 0 obj\n<< /Length " + contenido.length + " /Filter /FlateDecode >>\nstream\n", false);
                out.write(contenido);
                escribir(out, "\nendstream\nendobj\n", false);
            }

            int xref = out.size();
            int total = offsets.size() + 1;
            StringBuilder sb = new StringBuilder();
            sb.append("xref\n0 ").append(total).append("\n0000000000 65535 f \n");
            // los objetos se escribieron en orden 1..N
            for (int off : offsets) sb.append(String.format("%010d 00000 n \n", off));
            sb.append("trailer\n<< /Size ").append(total).append(" /Root 1 0 R /Info 5 0 R >>\nstartxref\n")
                    .append(xref).append("\n%%EOF\n");
            escribir(out, sb.toString(), false);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
    }

    private static void objeto(ByteArrayOutputStream out, List<Integer> offsets, int id, String cuerpo) throws IOException {
        offsets.add(out.size());
        out.write((id + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
        out.write(cuerpo.getBytes(Fuente.WIN_ANSI));
        out.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
    }

    private static void escribir(OutputStream out, String s, boolean latin1) throws IOException {
        out.write(s.getBytes(latin1 ? StandardCharsets.ISO_8859_1 : StandardCharsets.US_ASCII));
    }

    /** Traduce los trazos de la página a operadores PDF (el origen del PDF está abajo a la izquierda). */
    private static byte[] contenido(Pagina p) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        double H = p.alto();
        for (Pagina.Trazo t : p.trazos()) {
            switch (t) {
                case Pagina.Rect r -> {
                    StringBuilder sb = new StringBuilder();
                    String caja = num(r.x()) + " " + num(H - r.y() - r.h()) + " " + num(r.w()) + " " + num(r.h()) + " re ";
                    if (r.relleno() >= 0) sb.append(color(r.relleno())).append(" rg ").append(caja).append("f\n");
                    if (r.borde() >= 0) sb.append("0.6 w ").append(color(r.borde())).append(" RG ").append(caja).append("S\n");
                    escribir(out, sb.toString(), false);
                }
                case Pagina.Linea l -> escribir(out, num(l.grosor()) + " w " + color(l.color()) + " RG "
                        + num(l.x1()) + " " + num(H - l.y1()) + " m " + num(l.x2()) + " " + num(H - l.y2()) + " l S\n", false);
                case Pagina.Texto tx -> {
                    double ancho = Fuente.ancho(tx.texto(), tx.negrita(), tx.tam());
                    double x = switch (tx.alin()) {
                        case IZQUIERDA -> tx.x();
                        case CENTRO -> tx.x() - ancho / 2;
                        case DERECHA -> tx.x() - ancho;
                    };
                    escribir(out, "BT /" + (tx.negrita() ? "F2" : "F1") + " " + num(tx.tam()) + " Tf "
                            + color(tx.color()) + " rg " + num(x) + " " + num(H - tx.y()) + " Td ", false);
                    out.write(cadenaBytes(tx.texto()));
                    escribir(out, " Tj ET\n", false);
                }
            }
        }
        return out.toByteArray();
    }

    private static byte[] comprimir(byte[] datos) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(out, new Deflater(Deflater.BEST_COMPRESSION))) {
            z.write(datos);
        }
        return out.toByteArray();
    }

    /** Cadena PDF entre paréntesis, con escapes; los bytes > 126 van en octal. */
    private static byte[] cadenaBytes(String s) {
        StringBuilder sb = new StringBuilder("(");
        for (byte b : Fuente.bytes(s)) {
            int c = b & 0xFF;
            if (c == '(' || c == ')' || c == '\\') sb.append('\\').append((char) c);
            else if (c < 32 || c > 126) sb.append(String.format("\\%03o", c));
            else sb.append((char) c);
        }
        sb.append(')');
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String cadena(String s) {
        return new String(cadenaBytes(s), StandardCharsets.US_ASCII);
    }

    private static String color(int rgb) {
        return num(((rgb >> 16) & 0xFF) / 255.0) + " " + num(((rgb >> 8) & 0xFF) / 255.0) + " " + num((rgb & 0xFF) / 255.0);
    }

    private static String num(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s.equals("-0") ? "0" : s;
    }
}
