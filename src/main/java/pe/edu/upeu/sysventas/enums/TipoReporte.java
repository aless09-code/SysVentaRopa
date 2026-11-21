package pe.edu.upeu.sysventas.enums;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reportes disponibles en la pantalla "Reportes": qué filtros usa cada uno
 * y qué perfiles pueden generarlo.
 */
public enum TipoReporte {

    ESTADO_CUENTA("Estado de cuenta general",
            "Todos los ingresos (ventas) y egresos (compras) del período, con saldo acumulado.",
            EnumSet.of(Filtro.FECHAS), Perfiles.ADMIN),
    ESTADO_CUENTA_CLIENTE("Estado de cuenta por cliente",
            "Compras de cada cliente con su comprobante y monto acumulado.",
            EnumSet.of(Filtro.FECHAS, Filtro.CLIENTE, Filtro.DETALLE), Perfiles.ADMIN_VENTAS),
    ESTADO_CUENTA_PROVEEDOR("Estado de cuenta por proveedor",
            "Compras hechas a cada proveedor con su documento y monto acumulado.",
            EnumSet.of(Filtro.FECHAS, Filtro.PROVEEDOR, Filtro.DETALLE), Perfiles.ADMIN_ALMACEN),
    VENTAS("Reporte de ventas",
            "Ventas del período agrupadas por día, con boleta/factura, cliente y vendedor.",
            EnumSet.of(Filtro.FECHAS, Filtro.CLIENTE, Filtro.USUARIO, Filtro.DETALLE), Perfiles.ADMIN_VENTAS),
    COMPRAS("Reporte de compras",
            "Compras a proveedores del período agrupadas por día.",
            EnumSet.of(Filtro.FECHAS, Filtro.PROVEEDOR, Filtro.USUARIO, Filtro.DETALLE), Perfiles.ADMIN_ALMACEN),
    PRENDAS_VENDIDAS("Ventas por prenda y categoría",
            "Unidades vendidas, costo, utilidad y margen de cada prenda.",
            EnumSet.of(Filtro.FECHAS, Filtro.CATEGORIA), Perfiles.ADMIN_VENTAS),
    INVENTARIO("Inventario de prendas",
            "Existencias valorizadas por categoría (al costo y a precio de venta).",
            EnumSet.of(Filtro.TEXTO, Filtro.CATEGORIA, Filtro.STOCK), Perfiles.ADMIN_ALMACEN),
    KARDEX("Kardex de prendas",
            "Entradas (compras), salidas (ventas) y saldo de stock de cada prenda.",
            EnumSet.of(Filtro.FECHAS, Filtro.PRODUCTO), Perfiles.ADMIN_ALMACEN),
    CLIENTES("Reporte de clientes",
            "Clientes registrados con su número de compras y total comprado.",
            EnumSet.of(Filtro.FECHAS, Filtro.TEXTO), Perfiles.ADMIN_VENTAS),
    PROVEEDORES("Reporte de proveedores",
            "Proveedores registrados con su número de compras y total comprado.",
            EnumSet.of(Filtro.FECHAS, Filtro.TEXTO), Perfiles.ADMIN_ALMACEN),
    USUARIOS("Actividad de usuarios",
            "Ventas y compras registradas por cada usuario del sistema.",
            EnumSet.of(Filtro.FECHAS), Perfiles.ADMIN),
    CATALOGO("Categorías y unidades de medida",
            "Prendas, stock y valor por categoría y por unidad de medida.",
            EnumSet.noneOf(Filtro.class), Perfiles.ADMIN_ALMACEN);

    /** Filtros que puede mostrar la pantalla. */
    public enum Filtro { FECHAS, CLIENTE, PROVEEDOR, PRODUCTO, CATEGORIA, USUARIO, TEXTO, STOCK, DETALLE }

    private static final class Perfiles {
        static final String A = "Administrador de Tienda";
        static final String V = "Vendedor de Tienda / Cajero";
        static final String E = "Encargado de Almacén";
        static final List<String> ADMIN = List.of(A);
        static final List<String> ADMIN_VENTAS = List.of(A, V);
        static final List<String> ADMIN_ALMACEN = List.of(A, E);
    }

    private final String nombre;
    private final String descripcion;
    private final Set<Filtro> filtros;
    private final List<String> perfiles;

    TipoReporte(String nombre, String descripcion, Set<Filtro> filtros, List<String> perfiles) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.filtros = filtros;
        this.perfiles = perfiles;
    }

    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public boolean usa(Filtro f) { return filtros.contains(f); }

    /** Reportes que puede ver un perfil. */
    public static List<TipoReporte> paraPerfil(String perfil) {
        return java.util.Arrays.stream(values()).filter(t -> t.perfiles.contains(perfil)).toList();
    }

    /** Nombre corto para el archivo PDF (ej. "estado_cuenta"). */
    public String archivo() {
        return name().toLowerCase();
    }

    @Override
    public String toString() {
        return nombre;
    }
}
