package pe.edu.upeu.sysventas.repository;

import pe.edu.upeu.sysventas.config.DatabaseConfig;
import pe.edu.upeu.sysventas.dto.DashboardResumen.ItemMonto;
import pe.edu.upeu.sysventas.dto.DashboardResumen.ItemRanking;
import pe.edu.upeu.sysventas.dto.DashboardResumen.ItemStock;
import pe.edu.upeu.sysventas.dto.DashboardResumen.VentaReciente;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Consultas de solo lectura para el Dashboard (reportes / agregados).
 * No es un CRUD, por eso no hereda de AbstractJpaRepository: cada método
 * ejecuta un SELECT con SUM / COUNT / GROUP BY y devuelve datos listos para graficar.
 */
public class DashboardRepository {

    /** Lee una fila de un ResultSet y la convierte en un objeto. */
    @FunctionalInterface
    private interface Mapeo<R> {
        R map(ResultSet rs) throws SQLException;
    }

    private <R> List<R> consultar(String sql, Mapeo<R> mapeo, Object... params) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                Object p = params[i];
                ps.setObject(i + 1, p instanceof LocalDate f ? Date.valueOf(f) : p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<R> lista = new ArrayList<>();
                while (rs.next()) lista.add(mapeo.map(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en consulta del dashboard: " + e.getMessage(), e);
        }
    }

    /** {suma de precioTotal, cantidad de ventas} entre dos fechas (incluidas). */
    public double[] totalVentas(LocalDate desde, LocalDate hasta) {
        return consultar("SELECT COALESCE(SUM(precioTotal),0) AS total, COUNT(*) AS num FROM Venta "
                        + "WHERE fechaVenta BETWEEN ? AND ?",
                rs -> new double[]{rs.getDouble("total"), rs.getInt("num")}, desde, hasta).get(0);
    }

    /** {suma de precioTotal, cantidad de compras} entre dos fechas (incluidas). */
    public double[] totalCompras(LocalDate desde, LocalDate hasta) {
        return consultar("SELECT COALESCE(SUM(precioTotal),0) AS total, COUNT(*) AS num FROM compras "
                        + "WHERE fechaCompra BETWEEN ? AND ?",
                rs -> new double[]{rs.getDouble("total"), rs.getInt("num")}, desde, hasta).get(0);
    }

    /**
     * {utilidad, venta neta} del periodo. Utilidad = precio de venta sin IGV - costo.
     * En VentaDetalle: netoTotal = precio de venta x cantidad; precioUnit = costo unitario.
     */
    public double[] utilidad(LocalDate desde, LocalDate hasta) {
        return consultar("SELECT COALESCE(SUM(d.netoTotal - d.precioUnit * d.cantidad),0) AS util, "
                        + "COALESCE(SUM(d.netoTotal),0) AS neto FROM VentaDetalle d "
                        + "JOIN Venta v ON d.idVenta = v.idVenta WHERE v.fechaVenta BETWEEN ? AND ?",
                rs -> new double[]{rs.getDouble("util"), rs.getDouble("neto")}, desde, hasta).get(0);
    }

    /** Total vendido por día (solo los días con ventas). */
    public Map<LocalDate, Double> ventasPorDia(LocalDate desde, LocalDate hasta) {
        Map<LocalDate, Double> mapa = new TreeMap<>();
        consultar("SELECT fechaVenta, SUM(precioTotal) AS total FROM Venta WHERE fechaVenta BETWEEN ? AND ? "
                        + "GROUP BY fechaVenta",
                rs -> mapa.put(rs.getDate("fechaVenta").toLocalDate(), rs.getDouble("total")), desde, hasta);
        return mapa;
    }

    /** Total comprado por día (solo los días con compras). */
    public Map<LocalDate, Double> comprasPorDia(LocalDate desde, LocalDate hasta) {
        Map<LocalDate, Double> mapa = new TreeMap<>();
        consultar("SELECT fechaCompra, SUM(precioTotal) AS total FROM compras WHERE fechaCompra BETWEEN ? AND ? "
                        + "GROUP BY fechaCompra",
                rs -> mapa.put(rs.getDate("fechaCompra").toLocalDate(), rs.getDouble("total")), desde, hasta);
        return mapa;
    }

    /** Venta neta (sin IGV) por categoría de prenda. */
    public List<ItemMonto> ventasPorCategoria(LocalDate desde, LocalDate hasta) {
        return consultar("SELECT c.nombre AS categoria, SUM(d.netoTotal) AS monto FROM VentaDetalle d "
                        + "JOIN Venta v ON d.idVenta = v.idVenta "
                        + "JOIN Productos p ON d.idProducto = p.idProducto "
                        + "JOIN Categoria c ON p.idCateg = c.idCateg "
                        + "WHERE v.fechaVenta BETWEEN ? AND ? GROUP BY c.nombre ORDER BY monto DESC",
                rs -> new ItemMonto(rs.getString("categoria"), rs.getDouble("monto")), desde, hasta);
    }

    /** Prendas más vendidas (por unidades). */
    public List<ItemRanking> topPrendas(LocalDate desde, LocalDate hasta, int limite) {
        return consultar("SELECT p.nombre AS nombre, SUM(d.cantidad) AS unidades, SUM(d.netoTotal) AS monto "
                        + "FROM VentaDetalle d JOIN Venta v ON d.idVenta = v.idVenta "
                        + "JOIN Productos p ON d.idProducto = p.idProducto "
                        + "WHERE v.fechaVenta BETWEEN ? AND ? GROUP BY p.nombre "
                        + "ORDER BY unidades DESC, monto DESC FETCH FIRST " + limite + " ROWS ONLY",
                rs -> new ItemRanking(rs.getString("nombre"), rs.getInt("unidades"), rs.getDouble("monto")),
                desde, hasta);
    }

    /** Vendedores con más ventas (por monto). */
    public List<ItemRanking> topVendedores(LocalDate desde, LocalDate hasta, int limite) {
        return consultar("SELECT u.usuario AS nombre, COUNT(*) AS num, SUM(v.precioTotal) AS monto "
                        + "FROM Venta v JOIN Usuario u ON v.idUsuario = u.idUsuario "
                        + "WHERE v.fechaVenta BETWEEN ? AND ? GROUP BY u.usuario "
                        + "ORDER BY monto DESC FETCH FIRST " + limite + " ROWS ONLY",
                rs -> new ItemRanking(rs.getString("nombre"), rs.getInt("num"), rs.getDouble("monto")),
                desde, hasta);
    }

    /** Las ventas más recientes. */
    public List<VentaReciente> ultimasVentas(int limite) {
        return consultar("SELECT v.idVenta, v.fechaVenta, v.precioTotal, c.nombres, u.usuario FROM Venta v "
                        + "JOIN Cliente c ON v.idCliente = c.idCliente "
                        + "JOIN Usuario u ON v.idUsuario = u.idUsuario "
                        + "ORDER BY v.fechaVenta DESC, v.idVenta DESC FETCH FIRST " + limite + " ROWS ONLY",
                rs -> new VentaReciente(rs.getLong("idVenta"), rs.getDate("fechaVenta").toLocalDate(),
                        rs.getString("nombres"), rs.getString("usuario"), rs.getDouble("precioTotal")));
    }

    /** Prendas cuyo stock es menor o igual al mínimo indicado. */
    public List<ItemStock> stockBajo(int minimo, int limite) {
        return consultar("SELECT p.nombre, c.nombre AS categoria, p.stock FROM Productos p "
                        + "JOIN Categoria c ON p.idCateg = c.idCateg WHERE p.stock <= ? "
                        + "ORDER BY p.stock ASC FETCH FIRST " + limite + " ROWS ONLY",
                rs -> new ItemStock(rs.getString("nombre"), rs.getString("categoria"), rs.getInt("stock")),
                minimo);
    }

    public int totalClientes() {
        return consultar("SELECT COUNT(*) AS n FROM Cliente", rs -> rs.getInt("n")).get(0);
    }

    /** {número de prendas, unidades en stock, valor del inventario al costo}. */
    public double[] inventario() {
        return consultar("SELECT COUNT(*) AS n, COALESCE(SUM(stock),0) AS uni, "
                        + "COALESCE(SUM(stock * preUnitario),0) AS valor FROM Productos",
                rs -> new double[]{rs.getInt("n"), rs.getLong("uni"), rs.getDouble("valor")}).get(0);
    }
}
