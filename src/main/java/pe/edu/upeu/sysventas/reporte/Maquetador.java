package pe.edu.upeu.sysventas.reporte;

import pe.edu.upeu.sysventas.reporte.Reporte.Alin;
import pe.edu.upeu.sysventas.reporte.Reporte.Columna;
import pe.edu.upeu.sysventas.reporte.Reporte.Fila;
import pe.edu.upeu.sysventas.reporte.Reporte.TipoFila;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Convierte un {@link Reporte} en hojas A4 ({@link Pagina}): pone el encabezado y el pie
 * en cada hoja, reparte las tablas entre páginas (repitiendo la cabecera de columnas)
 * y numera "Página X de Y".
 */
public final class Maquetador {

    // Tamaño A4 en puntos
    public static final double A4_ANCHO = 595.28;
    public static final double A4_ALTO = 841.89;

    // Colores (0xRRGGBB)
    static final int AZUL = 0x1F2D4D;
    static final int ACENTO = 0xC62828;
    static final int TEXTO = 0x222222;
    static final int GRIS = 0x5F6368;
    static final int GRIS_LINEA = 0xD5D9E0;
    static final int CEBRA = 0xF4F6F9;
    static final int FONDO_GRUPO = 0xE2E8F0;
    static final int FONDO_SUBTOTAL = 0xEDF0F5;
    static final int VERDE = 0x2E7D32;
    static final int BLANCO = 0xFFFFFF;

    private static final double MARGEN = 36;
    private static final double PIE = 42;           // espacio reservado abajo para el pie
    private static final double ALTO_CABECERA = 18;
    private static final double ALTO_FILA = 15;
    private static final double ALTO_DETALLE = 12.5;
    private static final double ALTO_TOTAL = 17;
    private static final double PAD = 4;
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Reporte r;
    private final double W;
    private final double H;
    private final double izq;
    private final double der;
    private final double util;
    private final List<Pagina> paginas = new ArrayList<>();
    private Pagina pag;
    private double y;

    private Maquetador(Reporte r) {
        this.r = r;
        this.W = r.horizontal() ? A4_ALTO : A4_ANCHO;
        this.H = r.horizontal() ? A4_ANCHO : A4_ALTO;
        this.izq = MARGEN;
        this.der = W - MARGEN;
        this.util = der - izq;
    }

    /** Maqueta el reporte completo y devuelve sus páginas. */
    public static List<Pagina> maquetar(Reporte r) {
        Maquetador m = new Maquetador(r);
        m.construir();
        return m.paginas;
    }

    private double limite() {
        return H - PIE;
    }

    private void construir() {
        nuevaPagina();
        for (Reporte.Bloque b : r.bloques()) {
            switch (b) {
                case Reporte.Seccion s -> seccion(s);
                case Reporte.Resumen s -> resumen(s);
                case Reporte.Tabla t -> tabla(t);
                case Reporte.Nota n -> nota(n);
                case Reporte.Ficha f -> ficha(f);
            }
        }
        // Pie de página con numeración, ahora que se sabe el total de hojas
        int total = paginas.size();
        for (int i = 0; i < total; i++) {
            Pagina p = paginas.get(i);
            p.linea(izq, H - 30, der, H - 30, GRIS_LINEA, 0.6);
            p.texto(izq, H - 19, "SysVentaRopa  ·  Sistema de Ventas e Inventario  ·  " + r.titulo(),
                    false, 7, GRIS, Alin.IZQUIERDA);
            p.texto(der, H - 19, "Página " + (i + 1) + " de " + total, true, 7, GRIS, Alin.DERECHA);
        }
    }

    // ------------------------------------------------------------ páginas

    private void nuevaPagina() {
        pag = new Pagina(W, H);
        paginas.add(pag);
        double top = MARGEN;

        // Encabezado de la empresa (todas las hojas)
        pag.rect(izq, top - 4, 4, 30, AZUL, -1);
        pag.texto(izq + 10, top + 9, r.empresa(), true, 13, AZUL, Alin.IZQUIERDA);
        pag.texto(izq + 10, top + 21, r.empresaSub(), false, 7.5, GRIS, Alin.IZQUIERDA);
        pag.texto(der, top + 7, "Emitido: " + r.emitido().format(FECHA_HORA), false, 7.5, GRIS, Alin.DERECHA);
        if (!r.usuario().isBlank()) {
            pag.texto(der, top + 18, "Usuario: " + r.usuario(), false, 7.5, GRIS, Alin.DERECHA);
        }
        pag.linea(izq, top + 32, der, top + 32, GRIS_LINEA, 0.6);

        if (paginas.size() == 1) {
            // Título grande solo en la primera hoja
            pag.texto(W / 2, top + 58, r.titulo(), true, 17, ACENTO, Alin.CENTRO);
            pag.texto(W / 2, top + 72, r.subtitulo(), false, 9, TEXTO, Alin.CENTRO);
            pag.linea(izq, top + 80, der, top + 80, ACENTO, 1.6);
            y = top + 88;
            filtros();
        } else {
            pag.texto(izq, top + 46, r.titulo() + "  (continuación)", true, 9, ACENTO, Alin.IZQUIERDA);
            y = top + 56;
        }
    }

    /** Pasa a otra hoja si no entra la altura indicada. */
    private void asegurar(double alto) {
        if (y + alto > limite()) nuevaPagina();
    }

    /** Caja gris con los filtros usados ("Filtros: Desde: 01/11/2026  Hasta: ..."). */
    private void filtros() {
        if (r.filtros().isEmpty()) return;
        double tam = 8;
        List<List<Object[]>> lineas = new ArrayList<>();
        List<Object[]> actual = new ArrayList<>();
        double x = 0;
        double max = util - 16;
        actual.add(new Object[]{"Filtros:  ", true});
        x += Fuente.ancho("Filtros:  ", true, tam);
        for (String[] f : r.filtros()) {
            String et = f[0] + ": ";
            String va = Fuente.limpiar(f[1]) + "     ";
            double w = Fuente.ancho(et, true, tam) + Fuente.ancho(va, false, tam);
            if (x + w > max && actual.size() > 1) {
                lineas.add(actual);
                actual = new ArrayList<>();
                x = 0;
            }
            actual.add(new Object[]{et, true});
            actual.add(new Object[]{va, false});
            x += w;
        }
        lineas.add(actual);

        double alto = 8 + lineas.size() * 11;
        pag.rect(izq, y, util, alto, CEBRA, GRIS_LINEA);
        double by = y + 13;
        for (List<Object[]> linea : lineas) {
            double bx = izq + 8;
            for (Object[] seg : linea) {
                String t = (String) seg[0];
                boolean neg = (Boolean) seg[1];
                pag.texto(bx, by, Fuente.ajustar(t, neg, tam, der - 8 - bx), neg, tam, neg ? AZUL : TEXTO, Alin.IZQUIERDA);
                bx += Fuente.ancho(t, neg, tam);
            }
            by += 11;
        }
        y += alto + 12;
    }

    // ------------------------------------------------------------ bloques

    private void seccion(Reporte.Seccion s) {
        asegurar(24 + ALTO_CABECERA + ALTO_FILA * 2);   // que el título no quede solo al final de la hoja
        y += 4;
        pag.texto(izq, y + 11, s.titulo(), true, 10.5, AZUL, Alin.IZQUIERDA);
        pag.rect(izq, y + 15, 34, 2, ACENTO, -1);
        y += 24;
    }

    private void resumen(Reporte.Resumen s) {
        int porFila = r.horizontal() ? 5 : 4;
        double gap = 8;
        double alto = 38;
        double ancho = (util - gap * (porFila - 1)) / porFila;
        List<Reporte.Kpi> items = s.items();
        for (int i = 0; i < items.size(); i += porFila) {
            asegurar(alto);
            for (int j = 0; j < porFila && i + j < items.size(); j++) {
                Reporte.Kpi k = items.get(i + j);
                double x = izq + j * (ancho + gap);
                pag.rect(x, y, ancho, alto, CEBRA, GRIS_LINEA);
                pag.rect(x, y, 3, alto, AZUL, -1);
                pag.texto(x + 10, y + 13, Fuente.ajustar(k.etiqueta().toUpperCase(), true, 6.8, ancho - 14),
                        true, 6.8, GRIS, Alin.IZQUIERDA);
                int color = switch (k.tono()) {
                    case POSITIVO -> VERDE;
                    case NEGATIVO -> ACENTO;
                    default -> AZUL;
                };
                pag.texto(x + 10, y + 30, Fuente.ajustar(k.valor(), true, 12, ancho - 14), true, 12, color, Alin.IZQUIERDA);
            }
            y += alto + gap;
        }
        y += 6;
    }

    private void ficha(Reporte.Ficha f) {
        double colAncho = util / 2;
        double etAncho = 95;
        int filas = (f.datos().size() + 1) / 2;
        double alto = filas * 13 + 10;
        asegurar(alto);
        pag.rect(izq, y, util, alto, -1, GRIS_LINEA);
        for (int i = 0; i < f.datos().size(); i++) {
            String[] d = f.datos().get(i);
            double x = izq + 8 + (i % 2) * colAncho;
            double by = y + 14 + (i / 2) * 13;
            pag.texto(x, by, Fuente.ajustar(d[0] + ":", true, 8, etAncho - 4), true, 8, AZUL, Alin.IZQUIERDA);
            pag.texto(x + etAncho, by, Fuente.ajustar(d[1], false, 8, colAncho - etAncho - 14), false, 8, TEXTO, Alin.IZQUIERDA);
        }
        y += alto + 12;
    }

    private void nota(Reporte.Nota n) {
        double tam = 8;
        List<String> lineas = partir(n.texto(), tam, util);
        asegurar(lineas.size() * 11 + 4);
        for (String l : lineas) {
            y += 11;
            pag.texto(izq, y, l, false, tam, GRIS, Alin.IZQUIERDA);
        }
        y += 10;
    }

    /** Parte un párrafo en líneas que entren en el ancho. */
    private static List<String> partir(String texto, double tam, double max) {
        List<String> out = new ArrayList<>();
        StringBuilder linea = new StringBuilder();
        for (String palabra : Fuente.limpiar(texto).split(" ")) {
            String prueba = linea.isEmpty() ? palabra : linea + " " + palabra;
            if (Fuente.ancho(prueba, false, tam) > max && !linea.isEmpty()) {
                out.add(linea.toString());
                linea = new StringBuilder(palabra);
            } else {
                linea = new StringBuilder(prueba);
            }
        }
        if (!linea.isEmpty()) out.add(linea.toString());
        return out;
    }

    // ------------------------------------------------------------ tablas

    private double[] anchos(List<Columna> cols) {
        double suma = cols.stream().mapToDouble(Columna::peso).sum();
        double[] a = new double[cols.size()];
        for (int i = 0; i < a.length; i++) a[i] = util * cols.get(i).peso() / suma;
        return a;
    }

    private void cabecera(List<Columna> cols, double[] anchos) {
        pag.rect(izq, y, util, ALTO_CABECERA, AZUL, -1);
        double x = izq;
        for (int i = 0; i < cols.size(); i++) {
            celda(x, anchos[i], ALTO_CABECERA, cols.get(i).titulo(), cols.get(i).alin(), true, 7.8, BLANCO);
            x += anchos[i];
        }
        y += ALTO_CABECERA;
    }

    private void tabla(Reporte.Tabla t) {
        List<Columna> cols = t.columnas();
        double[] anchos = anchos(cols);
        asegurar(ALTO_CABECERA + ALTO_FILA * 2);
        cabecera(cols, anchos);

        if (t.sinDatos() && t.filas().stream().noneMatch(f -> f.tipo() == TipoFila.GRUPO)) {
            pag.rect(izq, y, util, 24, CEBRA, -1);
            pag.texto(izq + util / 2, y + 15, t.vacio(), false, 8, GRIS, Alin.CENTRO);
            y += 24;
            pag.linea(izq, y, der, y, GRIS_LINEA, 0.6);
            y += 12;
            return;
        }

        int cebra = 0;
        List<Fila> filas = t.filas();
        for (int idx = 0; idx < filas.size(); idx++) {
            Fila f = filas.get(idx);
            double alto = switch (f.tipo()) {
                case DETALLE -> ALTO_DETALLE;
                case TOTAL -> ALTO_TOTAL;
                default -> ALTO_FILA;
            };
            // Un grupo no debe quedar solo al final de la hoja
            double necesario = alto;
            if (f.tipo() == TipoFila.GRUPO) necesario += ALTO_FILA;
            // El subtotal no se separa del total que le sigue
            if (f.tipo() == TipoFila.SUBTOTAL && idx + 1 < filas.size()
                    && filas.get(idx + 1).tipo() == TipoFila.TOTAL) necesario += ALTO_TOTAL;
            if (y + necesario > limite()) {
                pag.linea(izq, y, der, y, GRIS_LINEA, 0.6);
                nuevaPagina();
                cabecera(cols, anchos);
            }
            switch (f.tipo()) {
                case GRUPO -> {
                    pag.rect(izq, y, util, alto, FONDO_GRUPO, -1);
                    celda(izq, util, alto, f.celdas()[0], Alin.IZQUIERDA, true, 8, AZUL);
                    cebra = 0;
                }
                case DATO -> {
                    if (cebra++ % 2 == 1) pag.rect(izq, y, util, alto, CEBRA, -1);
                    fila(f.celdas(), cols, anchos, alto, false, 7.8, TEXTO, false);
                }
                case DETALLE -> fila(f.celdas(), cols, anchos, alto, false, 6.8, GRIS, false);
                case SUBTOTAL -> {
                    pag.rect(izq, y, util, alto, FONDO_SUBTOTAL, -1);
                    pag.linea(izq, y, der, y, GRIS_LINEA, 0.6);
                    fila(f.celdas(), cols, anchos, alto, true, 7.8, AZUL, true);
                }
                case TOTAL -> {
                    pag.rect(izq, y, util, alto, ACENTO, -1);
                    fila(f.celdas(), cols, anchos, alto, true, 8.2, BLANCO, true);
                }
            }
            y += alto;
        }
        pag.linea(izq, y, der, y, GRIS_LINEA, 0.6);
        y += 14;
    }

    /**
     * Dibuja las celdas de una fila. La primera celda con texto puede ocupar las celdas
     * vacías que le siguen: en subtotales/totales la etiqueta se alinea a la derecha
     * (ej. "TOTAL (25 registros)"), en filas de detalle sigue a la izquierda.
     */
    private void fila(String[] celdas, List<Columna> cols, double[] anchos, double alto,
                      boolean negrita, double tam, int color, boolean esTotal) {
        double x = izq;
        boolean primera = true;
        for (int i = 0; i < cols.size(); i++) {
            String txt = i < celdas.length ? celdas[i] : null;
            double ancho = anchos[i];
            Alin alin = cols.get(i).alin();
            int salto = 0;
            boolean conTexto = txt != null && !txt.isEmpty();
            if (conTexto && primera) {
                int hasta = siguienteConTexto(celdas, cols, i);
                while (i + salto + 1 < hasta) {
                    salto++;
                    ancho += anchos[i + salto];
                }
                if (salto > 0) alin = esTotal ? Alin.DERECHA : Alin.IZQUIERDA;
            }
            if (conTexto) {
                celda(x, ancho, alto, txt, alin, negrita, tam, color);
                primera = false;
            }
            x += ancho;
            i += salto;
        }
    }

    /** Índice de la siguiente columna con texto (las etiquetas se expanden solo hasta ahí). */
    private static int siguienteConTexto(String[] celdas, List<Columna> cols, int desde) {
        for (int j = desde + 1; j < cols.size(); j++) {
            if (j < celdas.length && celdas[j] != null && !celdas[j].isEmpty()) return j;
        }
        return cols.size();
    }

    private void celda(double x, double ancho, double alto, String txt, Alin alin,
                       boolean negrita, double tam, int color) {
        String t = Fuente.ajustar(txt, negrita, tam, ancho - PAD * 2);
        double base = y + alto / 2 + tam * 0.35;
        double ax = switch (alin) {
            case IZQUIERDA -> x + PAD;
            case CENTRO -> x + ancho / 2;
            case DERECHA -> x + ancho - PAD;
        };
        pag.texto(ax, base, t, negrita, tam, color, alin);
    }
}
