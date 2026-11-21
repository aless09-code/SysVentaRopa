package pe.edu.upeu.sysventas.reporte;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo de un reporte (lo que se quiere mostrar), independiente de cómo se dibuja.
 * El {@link Maquetador} lo convierte en páginas, que luego se ven en pantalla
 * ({@link VistaReporte}) o se guardan como PDF ({@link PdfWriter}).
 *
 * <pre>
 * Reporte r = new Reporte("INVENTARIO DE PRENDAS", "Existencias valorizadas por categoría");
 * r.filtro("Categoría", "Todas");
 * r.resumen(new Reporte.Kpi("Prendas", "25"), new Reporte.Kpi("Valor", "S/ 1,200.00"));
 * Reporte.Tabla t = r.tabla(new Reporte.Columna("Prenda", 3), new Reporte.Columna("Stock", 1, Alin.DERECHA));
 * t.dato("Polo básico", "20");
 * t.total("TOTAL", "20");
 * </pre>
 */
public class Reporte {

    public enum Alin { IZQUIERDA, CENTRO, DERECHA }

    public enum Tono { NORMAL, POSITIVO, NEGATIVO }

    public enum TipoFila { DATO, DETALLE, GRUPO, SUBTOTAL, TOTAL }

    /** Bloques que se apilan uno debajo del otro en la hoja. */
    public sealed interface Bloque permits Seccion, Resumen, Tabla, Nota, Ficha { }

    /** Subtítulo de sección (ej. "Movimientos del período"). */
    public record Seccion(String titulo) implements Bloque { }

    /** Cajitas de indicadores (KPI). */
    public record Resumen(List<Kpi> items) implements Bloque { }

    public record Kpi(String etiqueta, String valor, Tono tono) {
        public Kpi(String etiqueta, String valor) {
            this(etiqueta, valor, Tono.NORMAL);
        }
    }

    /** Párrafo de texto corto (observaciones). */
    public record Nota(String texto) implements Bloque { }

    /** Ficha de datos en dos columnas: etiqueta / valor (ej. datos del cliente). */
    public record Ficha(List<String[]> datos) implements Bloque { }

    public record Columna(String titulo, double peso, Alin alin) {
        public Columna(String titulo, double peso) {
            this(titulo, peso, Alin.IZQUIERDA);
        }
    }

    public record Fila(TipoFila tipo, String[] celdas) { }

    /** Tabla con encabezado azul, filas cebra, grupos, subtotales y total. */
    public static final class Tabla implements Bloque {
        private final List<Columna> columnas;
        private final List<Fila> filas = new ArrayList<>();
        private String vacio = "No hay registros para los filtros elegidos.";

        public Tabla(List<Columna> columnas) {
            this.columnas = columnas;
        }

        public List<Columna> columnas() {
            return columnas;
        }

        public List<Fila> filas() {
            return filas;
        }

        public String vacio() {
            return vacio;
        }

        public Tabla siVacia(String texto) {
            this.vacio = texto;
            return this;
        }

        public Tabla dato(String... celdas) {
            filas.add(new Fila(TipoFila.DATO, celdas));
            return this;
        }

        /** Fila secundaria (más pequeña y gris), ej. las prendas de una venta. */
        public Tabla detalle(String... celdas) {
            filas.add(new Fila(TipoFila.DETALLE, celdas));
            return this;
        }

        /** Fila de agrupación: el texto ocupa todo el ancho (ej. "Categoría: Polos"). */
        public Tabla grupo(String texto) {
            filas.add(new Fila(TipoFila.GRUPO, new String[]{texto}));
            return this;
        }

        public Tabla subtotal(String... celdas) {
            filas.add(new Fila(TipoFila.SUBTOTAL, celdas));
            return this;
        }

        public Tabla total(String... celdas) {
            filas.add(new Fila(TipoFila.TOTAL, celdas));
            return this;
        }

        public boolean sinDatos() {
            return filas.stream().noneMatch(f -> f.tipo() == TipoFila.DATO || f.tipo() == TipoFila.DETALLE);
        }
    }

    // ------------------------------------------------------------------

    private final String titulo;
    private final String subtitulo;
    private String empresa = "SysVentaRopa - Tienda de Ropa";
    private String empresaSub = "RUC 20123456789  ·  Av. Principal 123 - Lima";
    private String usuario = "";
    private LocalDateTime emitido = LocalDateTime.now();
    private boolean horizontal;
    private final List<String[]> filtros = new ArrayList<>();
    private final List<Bloque> bloques = new ArrayList<>();

    public Reporte(String titulo, String subtitulo) {
        this.titulo = titulo;
        this.subtitulo = subtitulo;
    }

    public Reporte filtro(String etiqueta, String valor) {
        filtros.add(new String[]{etiqueta, valor});
        return this;
    }

    public Reporte seccion(String titulo) {
        bloques.add(new Seccion(titulo));
        return this;
    }

    public Reporte resumen(Kpi... items) {
        bloques.add(new Resumen(List.of(items)));
        return this;
    }

    public Reporte resumen(List<Kpi> items) {
        bloques.add(new Resumen(List.copyOf(items)));
        return this;
    }

    public Reporte nota(String texto) {
        bloques.add(new Nota(texto));
        return this;
    }

    public Reporte ficha(List<String[]> datos) {
        bloques.add(new Ficha(datos));
        return this;
    }

    public Tabla tabla(Columna... columnas) {
        Tabla t = new Tabla(List.of(columnas));
        bloques.add(t);
        return t;
    }

    public Reporte horizontal(boolean h) {
        this.horizontal = h;
        return this;
    }

    public Reporte usuario(String u) {
        this.usuario = u == null ? "" : u;
        return this;
    }

    public Reporte emitido(LocalDateTime f) {
        this.emitido = f;
        return this;
    }

    public Reporte empresa(String nombre, String sub) {
        this.empresa = nombre;
        this.empresaSub = sub;
        return this;
    }

    public String titulo() { return titulo; }
    public String subtitulo() { return subtitulo; }
    public String empresa() { return empresa; }
    public String empresaSub() { return empresaSub; }
    public String usuario() { return usuario; }
    public LocalDateTime emitido() { return emitido; }
    public boolean horizontal() { return horizontal; }
    public List<String[]> filtros() { return filtros; }
    public List<Bloque> bloques() { return bloques; }
}
