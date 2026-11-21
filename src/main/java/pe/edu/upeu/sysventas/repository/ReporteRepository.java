package pe.edu.upeu.sysventas.repository;

import pe.edu.upeu.sysventas.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Consultas de solo lectura para los reportes PDF.
 * Igual que DashboardRepository, no es un CRUD: cada método ejecuta un SELECT
 * (con JOIN / GROUP BY) y devuelve registros listos para armar el reporte.
 */
public class ReporteRepository {

    // ------------------------------------------------------------ registros que devuelve

    /** Opción para los combos de filtros (id + texto). */
    public record Opcion(Long id, String nombre) {
        @Override
        public String toString() {
            return nombre;
        }
    }

    public record VentaFila(long id, LocalDate fecha, long idCliente, String tipoDocCliente, String docCliente,
                            String cliente, String direccion, String celular, String usuario,
                            double neto, double igv, double total) { }

    public record CompraFila(long id, LocalDate fecha, long idProveedor, String tipoDoc, String serie, String numDoc,
                             String ruc, String proveedor, String contacto, String direccion, String celular,
                             String email, String usuario, double neto, double igv, double total) { }

    /** Una prenda dentro de una venta o compra. precio = precio unitario cobrado/pagado. */
    public record ItemFila(long idDocumento, String prenda, int cantidad, double precio, double importe) { }

    public record ProductoFila(long id, String nombre, String categoria, String unidad,
                               double costo, double porceUtil, int stock) { }

    /** Movimiento de kardex: entrada (compra) o salida (venta). */
    public record KardexFila(long idProducto, LocalDate fecha, boolean entrada, long idDocumento, String documento,
                             String tercero, int cantidad, double costoUnit) { }

    public record ClienteFila(long id, String tipoDoc, String doc, String nombre, String celular, String direccion,
                              int compras, double total, LocalDate ultima) { }

    public record ProveedorFila(long id, String ruc, String razonSocial, String contacto, String celular, String email,
                                int compras, double total, LocalDate ultima) { }

    public record UsuarioFila(String usuario, String perfil, int ventas, int unidades, double totalVentas,
                              int compras, double totalCompras) { }

    public record PrendaVendidaFila(String categoria, String prenda, int unidades, double venta, double costo) { }

    public record GrupoFila(String nombre, int prendas, long stock, double valorCosto, double valorVenta) { }

    // ------------------------------------------------------------ utilidades

    @FunctionalInterface
    private interface Mapeo<R> {
        R map(ResultSet rs) throws SQLException;
    }

    private <R> List<R> consultar(String sql, Mapeo<R> mapeo, List<Object> params) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                ps.setObject(i + 1, p instanceof LocalDate f ? Date.valueOf(f) : p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<R> lista = new ArrayList<>();
                while (rs.next()) lista.add(mapeo.map(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en consulta del reporte: " + e.getMessage(), e);
        }
    }

    private <R> List<R> consultar(String sql, Mapeo<R> mapeo, Object... params) {
        return consultar(sql, mapeo, List.of(params));
    }

    private static LocalDate fecha(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    // ------------------------------------------------------------ combos

    public List<Opcion> clientes() {
        return consultar("SELECT idCliente, nombres, dniruc FROM Cliente ORDER BY nombres",
                rs -> new Opcion(rs.getLong(1), rs.getString(2) + "  (" + rs.getString(3) + ")"));
    }

    public List<Opcion> proveedores() {
        return consultar("SELECT idProveedor, razonSocial, ruc FROM proveedor ORDER BY razonSocial",
                rs -> new Opcion(rs.getLong(1), rs.getString(2) + "  (" + rs.getString(3) + ")"));
    }

    public List<Opcion> productos() {
        return consultar("SELECT idProducto, nombre FROM Productos ORDER BY nombre",
                rs -> new Opcion(rs.getLong(1), rs.getString(2)));
    }

    public List<Opcion> categorias() {
        return consultar("SELECT idCateg, nombre FROM Categoria ORDER BY nombre",
                rs -> new Opcion(rs.getLong(1), rs.getString(2)));
    }

    public List<Opcion> usuarios() {
        return consultar("SELECT idUsuario, usuario FROM Usuario ORDER BY usuario",
                rs -> new Opcion(rs.getLong(1), rs.getString(2)));
    }

    // ------------------------------------------------------------ ventas y compras

    /** Ventas entre dos fechas; idCliente / idUsuario en null = todos. */
    public List<VentaFila> ventas(LocalDate desde, LocalDate hasta, Long idCliente, Long idUsuario) {
        StringBuilder sql = new StringBuilder(
                "SELECT v.idVenta, v.fechaVenta, v.netoTotal, v.igv, v.precioTotal, c.idCliente, c.tipoDocumento, "
                        + "c.dniruc, c.nombres, c.direccion, c.celular, u.usuario FROM Venta v "
                        + "JOIN Cliente c ON v.idCliente = c.idCliente JOIN Usuario u ON v.idUsuario = u.idUsuario "
                        + "WHERE v.fechaVenta BETWEEN ? AND ?");
        List<Object> p = new ArrayList<>(List.of(desde, hasta));
        if (idCliente != null) { sql.append(" AND v.idCliente = ?"); p.add(idCliente); }
        if (idUsuario != null) { sql.append(" AND v.idUsuario = ?"); p.add(idUsuario); }
        sql.append(" ORDER BY v.fechaVenta, v.idVenta");
        return consultar(sql.toString(), rs -> new VentaFila(rs.getLong("idVenta"), fecha(rs, "fechaVenta"),
                rs.getLong("idCliente"), rs.getString("tipoDocumento"), rs.getString("dniruc"), rs.getString("nombres"),
                rs.getString("direccion"), rs.getString("celular"), rs.getString("usuario"),
                rs.getDouble("netoTotal"), rs.getDouble("igv"), rs.getDouble("precioTotal")), p);
    }

    /** Compras entre dos fechas; idProveedor / idUsuario en null = todos. */
    public List<CompraFila> compras(LocalDate desde, LocalDate hasta, Long idProveedor, Long idUsuario) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.idCompra, c.fechaCompra, c.tipoDoc, c.serie, c.numDoc, c.netoTotal, c.igv, c.precioTotal, "
                        + "p.idProveedor, p.ruc, p.razonSocial, p.contacto, p.direccion, p.celular, p.email, u.usuario "
                        + "FROM compras c JOIN proveedor p ON c.idProveedor = p.idProveedor "
                        + "JOIN Usuario u ON c.idUsuario = u.idUsuario WHERE c.fechaCompra BETWEEN ? AND ?");
        List<Object> p = new ArrayList<>(List.of(desde, hasta));
        if (idProveedor != null) { sql.append(" AND c.idProveedor = ?"); p.add(idProveedor); }
        if (idUsuario != null) { sql.append(" AND c.idUsuario = ?"); p.add(idUsuario); }
        sql.append(" ORDER BY c.fechaCompra, c.idCompra");
        return consultar(sql.toString(), rs -> new CompraFila(rs.getLong("idCompra"), fecha(rs, "fechaCompra"),
                rs.getLong("idProveedor"), rs.getString("tipoDoc"), rs.getString("serie"), rs.getString("numDoc"),
                rs.getString("ruc"), rs.getString("razonSocial"), rs.getString("contacto"), rs.getString("direccion"),
                rs.getString("celular"), rs.getString("email"), rs.getString("usuario"),
                rs.getDouble("netoTotal"), rs.getDouble("igv"), rs.getDouble("precioTotal")), p);
    }

    /** Prendas de las ventas del período (para mostrar el detalle). */
    public List<ItemFila> itemsVentas(LocalDate desde, LocalDate hasta) {
        return consultar("SELECT d.idVenta, p.nombre, d.cantidad, d.netoTotal FROM VentaDetalle d "
                        + "JOIN Venta v ON d.idVenta = v.idVenta JOIN Productos p ON d.idProducto = p.idProducto "
                        + "WHERE v.fechaVenta BETWEEN ? AND ? ORDER BY d.idVenta, d.idVentaDetalle",
                rs -> {
                    int cant = rs.getInt("cantidad");
                    double importe = rs.getDouble("netoTotal");
                    return new ItemFila(rs.getLong("idVenta"), rs.getString("nombre"), cant,
                            cant == 0 ? 0 : importe / cant, importe);
                }, desde, hasta);
    }

    /** Prendas de las compras del período (para mostrar el detalle). */
    public List<ItemFila> itemsCompras(LocalDate desde, LocalDate hasta) {
        return consultar("SELECT d.idCompra, p.nombre, d.cantidad, d.precioCompra, d.subtotal FROM detalle_compra d "
                        + "JOIN compras c ON d.idCompra = c.idCompra JOIN Productos p ON d.idProducto = p.idProducto "
                        + "WHERE c.fechaCompra BETWEEN ? AND ? ORDER BY d.idCompra, d.idDetalleCompra",
                rs -> new ItemFila(rs.getLong("idCompra"), rs.getString("nombre"), rs.getInt("cantidad"),
                        rs.getDouble("precioCompra"), rs.getDouble("subtotal")), desde, hasta);
    }

    /** {ventas, compras} acumuladas ANTES de una fecha (para el saldo anterior del estado de cuenta). */
    public double[] acumuladoAntesDe(LocalDate fecha) {
        double v = consultar("SELECT COALESCE(SUM(precioTotal),0) FROM Venta WHERE fechaVenta < ?",
                rs -> rs.getDouble(1), fecha).get(0);
        double c = consultar("SELECT COALESCE(SUM(precioTotal),0) FROM compras WHERE fechaCompra < ?",
                rs -> rs.getDouble(1), fecha).get(0);
        return new double[]{v, c};
    }

    // ------------------------------------------------------------ inventario y kardex

    public List<ProductoFila> productos(Long idCategoria, Long idProducto) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.idProducto, p.nombre, p.preUnitario, p.porceUtil, p.stock, c.nombre AS categoria, "
                        + "u.nombre AS unidad FROM Productos p JOIN Categoria c ON p.idCateg = c.idCateg "
                        + "JOIN UnidadMedida u ON p.idUnidadMed = u.idUnidadMed WHERE 1=1");
        List<Object> p = new ArrayList<>();
        if (idCategoria != null) { sql.append(" AND p.idCateg = ?"); p.add(idCategoria); }
        if (idProducto != null) { sql.append(" AND p.idProducto = ?"); p.add(idProducto); }
        sql.append(" ORDER BY c.nombre, p.nombre");
        return consultar(sql.toString(), rs -> new ProductoFila(rs.getLong("idProducto"), rs.getString("nombre"),
                rs.getString("categoria"), rs.getString("unidad"), rs.getDouble("preUnitario"),
                rs.getDouble("porceUtil"), rs.getInt("stock")), p);
    }

    /**
     * Movimientos de stock DESDE una fecha hasta hoy (se necesitan los posteriores al período
     * para calcular el saldo inicial a partir del stock actual).
     */
    public List<KardexFila> movimientosDesde(LocalDate desde, Long idProducto) {
        String filtroProd = idProducto == null ? "" : " AND d.idProducto = ?";
        List<Object> p = new ArrayList<>();
        p.add(desde);
        if (idProducto != null) p.add(idProducto);
        List<KardexFila> lista = new ArrayList<>(consultar(
                "SELECT d.idProducto, c.fechaCompra AS fecha, c.idCompra AS id, c.tipoDoc, c.serie, c.numDoc, "
                        + "pr.razonSocial AS tercero, d.cantidad, d.precioCompra AS costo FROM detalle_compra d "
                        + "JOIN compras c ON d.idCompra = c.idCompra JOIN proveedor pr ON c.idProveedor = pr.idProveedor "
                        + "WHERE c.fechaCompra >= ?" + filtroProd,
                rs -> new KardexFila(rs.getLong("idProducto"), fecha(rs, "fecha"), true, rs.getLong("id"),
                        rs.getString("tipoDoc") + " " + rs.getString("serie") + "-" + rs.getString("numDoc"),
                        rs.getString("tercero"), rs.getInt("cantidad"), rs.getDouble("costo")), p));
        lista.addAll(consultar(
                "SELECT d.idProducto, v.fechaVenta AS fecha, v.idVenta AS id, cl.tipoDocumento, cl.nombres AS tercero, "
                        + "d.cantidad, d.precioUnit AS costo FROM VentaDetalle d JOIN Venta v ON d.idVenta = v.idVenta "
                        + "JOIN Cliente cl ON v.idCliente = cl.idCliente WHERE v.fechaVenta >= ?" + filtroProd,
                rs -> {
                    long id = rs.getLong("id");
                    String serie = "RUC".equals(rs.getString("tipoDocumento")) ? "F001-" : "B001-";
                    return new KardexFila(rs.getLong("idProducto"), fecha(rs, "fecha"), false, id,
                            serie + String.format("%08d", id), rs.getString("tercero"), rs.getInt("cantidad"),
                            rs.getDouble("costo"));
                }, p));
        return lista;
    }

    // ------------------------------------------------------------ clientes, proveedores, usuarios

    /** Clientes con el número y total de compras hechas en el período. */
    public List<ClienteFila> clientes(LocalDate desde, LocalDate hasta) {
        return consultar("SELECT c.idCliente, c.tipoDocumento, c.dniruc, c.nombres, c.celular, c.direccion, "
                        + "COUNT(v.idVenta) AS num, COALESCE(SUM(v.precioTotal),0) AS total, MAX(v.fechaVenta) AS ultima "
                        + "FROM Cliente c LEFT JOIN Venta v ON v.idCliente = c.idCliente AND v.fechaVenta BETWEEN ? AND ? "
                        + "GROUP BY c.idCliente, c.tipoDocumento, c.dniruc, c.nombres, c.celular, c.direccion "
                        + "ORDER BY total DESC, c.nombres",
                rs -> new ClienteFila(rs.getLong("idCliente"), rs.getString("tipoDocumento"), rs.getString("dniruc"),
                        rs.getString("nombres"), rs.getString("celular"), rs.getString("direccion"), rs.getInt("num"),
                        rs.getDouble("total"), fecha(rs, "ultima")), desde, hasta);
    }

    public List<ProveedorFila> proveedores(LocalDate desde, LocalDate hasta) {
        return consultar("SELECT p.idProveedor, p.ruc, p.razonSocial, p.contacto, p.celular, p.email, "
                        + "COUNT(c.idCompra) AS num, COALESCE(SUM(c.precioTotal),0) AS total, MAX(c.fechaCompra) AS ultima "
                        + "FROM proveedor p LEFT JOIN compras c ON c.idProveedor = p.idProveedor "
                        + "AND c.fechaCompra BETWEEN ? AND ? "
                        + "GROUP BY p.idProveedor, p.ruc, p.razonSocial, p.contacto, p.celular, p.email "
                        + "ORDER BY total DESC, p.razonSocial",
                rs -> new ProveedorFila(rs.getLong("idProveedor"), rs.getString("ruc"), rs.getString("razonSocial"),
                        rs.getString("contacto"), rs.getString("celular"), rs.getString("email"), rs.getInt("num"),
                        rs.getDouble("total"), fecha(rs, "ultima")), desde, hasta);
    }

    /** Ventas y compras registradas por cada usuario en el período. */
    public List<UsuarioFila> actividadUsuarios(LocalDate desde, LocalDate hasta) {
        return consultar("SELECT u.usuario, pf.nombre AS perfil, "
                        + "(SELECT COUNT(*) FROM Venta v WHERE v.idUsuario = u.idUsuario AND v.fechaVenta BETWEEN ? AND ?) AS nv, "
                        + "(SELECT COALESCE(SUM(d.cantidad),0) FROM VentaDetalle d JOIN Venta v ON d.idVenta = v.idVenta "
                        + "   WHERE v.idUsuario = u.idUsuario AND v.fechaVenta BETWEEN ? AND ?) AS uni, "
                        + "(SELECT COALESCE(SUM(v.precioTotal),0) FROM Venta v WHERE v.idUsuario = u.idUsuario "
                        + "   AND v.fechaVenta BETWEEN ? AND ?) AS tv, "
                        + "(SELECT COUNT(*) FROM compras c WHERE c.idUsuario = u.idUsuario AND c.fechaCompra BETWEEN ? AND ?) AS nc, "
                        + "(SELECT COALESCE(SUM(c.precioTotal),0) FROM compras c WHERE c.idUsuario = u.idUsuario "
                        + "   AND c.fechaCompra BETWEEN ? AND ?) AS tc "
                        + "FROM Usuario u JOIN Perfil pf ON u.idPerfil = pf.idPerfil ORDER BY tv DESC, u.usuario",
                rs -> new UsuarioFila(rs.getString("usuario"), rs.getString("perfil"), rs.getInt("nv"), rs.getInt("uni"),
                        rs.getDouble("tv"), rs.getInt("nc"), rs.getDouble("tc")),
                desde, hasta, desde, hasta, desde, hasta, desde, hasta, desde, hasta);
    }

    // ------------------------------------------------------------ prendas vendidas y catálogo

    public List<PrendaVendidaFila> prendasVendidas(LocalDate desde, LocalDate hasta, Long idCategoria) {
        String filtro = idCategoria == null ? "" : " AND p.idCateg = ?";
        List<Object> params = new ArrayList<>(List.of(desde, hasta));
        if (idCategoria != null) params.add(idCategoria);
        return consultar("SELECT c.nombre AS categoria, p.nombre AS prenda, SUM(d.cantidad) AS uni, "
                        + "SUM(d.netoTotal) AS venta, SUM(d.precioUnit * d.cantidad) AS costo FROM VentaDetalle d "
                        + "JOIN Venta v ON d.idVenta = v.idVenta JOIN Productos p ON d.idProducto = p.idProducto "
                        + "JOIN Categoria c ON p.idCateg = c.idCateg WHERE v.fechaVenta BETWEEN ? AND ?" + filtro
                        + " GROUP BY c.nombre, p.nombre ORDER BY c.nombre, uni DESC, venta DESC",
                rs -> new PrendaVendidaFila(rs.getString("categoria"), rs.getString("prenda"), rs.getInt("uni"),
                        rs.getDouble("venta"), rs.getDouble("costo")), params);
    }

    /** Resumen por categoría: prendas, stock, valor al costo y a precio de venta. */
    public List<GrupoFila> resumenCategorias() {
        return consultar("SELECT c.nombre, COUNT(p.idProducto) AS n, COALESCE(SUM(p.stock),0) AS st, "
                        + "COALESCE(SUM(p.stock * p.preUnitario),0) AS vc, "
                        + "COALESCE(SUM(p.stock * p.preUnitario * (1 + p.porceUtil / 100)),0) AS vv "
                        + "FROM Categoria c LEFT JOIN Productos p ON p.idCateg = c.idCateg GROUP BY c.nombre ORDER BY c.nombre",
                rs -> new GrupoFila(rs.getString(1), rs.getInt("n"), rs.getLong("st"), rs.getDouble("vc"), rs.getDouble("vv")));
    }

    public List<GrupoFila> resumenUnidades() {
        return consultar("SELECT u.nombre, COUNT(p.idProducto) AS n, COALESCE(SUM(p.stock),0) AS st, "
                        + "COALESCE(SUM(p.stock * p.preUnitario),0) AS vc, "
                        + "COALESCE(SUM(p.stock * p.preUnitario * (1 + p.porceUtil / 100)),0) AS vv "
                        + "FROM UnidadMedida u LEFT JOIN Productos p ON p.idUnidadMed = u.idUnidadMed "
                        + "GROUP BY u.nombre ORDER BY u.nombre",
                rs -> new GrupoFila(rs.getString(1), rs.getInt("n"), rs.getLong("st"), rs.getDouble("vc"), rs.getDouble("vv")));
    }
}
