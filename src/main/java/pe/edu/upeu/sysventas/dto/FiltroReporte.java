package pe.edu.upeu.sysventas.dto;

import java.time.LocalDate;

/**
 * Filtros elegidos en la pantalla de reportes. Los que no aplican van en null.
 *
 * @param desde         fecha inicial (incluida)
 * @param hasta         fecha final (incluida)
 * @param idCliente     cliente (null = todos)
 * @param idProveedor   proveedor (null = todos)
 * @param idProducto    prenda (null = todas)
 * @param idCategoria   categoría (null = todas)
 * @param idUsuario     usuario / vendedor (null = todos)
 * @param texto         texto a buscar (nombre, documento...)
 * @param stockMaximo   solo prendas con stock menor o igual (null = sin límite)
 * @param soloStockBajo solo prendas con stock bajo
 * @param detalle       incluir el detalle (prendas de cada venta / compra)
 * @param usuarioSesion usuario que genera el reporte (va en el encabezado)
 */
public record FiltroReporte(LocalDate desde, LocalDate hasta, Long idCliente, Long idProveedor, Long idProducto,
                            Long idCategoria, Long idUsuario, String texto, Integer stockMaximo,
                            boolean soloStockBajo, boolean detalle, String usuarioSesion) {

    /** Stock igual o menor a este valor se considera "stock bajo" (igual que en el Dashboard). */
    public static final int STOCK_BAJO = 20;

    /** Texto en minúsculas sin espacios extremos ("" si no hay). */
    public String textoBuscar() {
        return texto == null ? "" : texto.trim().toLowerCase();
    }
}
