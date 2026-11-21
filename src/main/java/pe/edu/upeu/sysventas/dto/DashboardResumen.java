package pe.edu.upeu.sysventas.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Todos los datos que muestra el Dashboard, calculados de una sola vez.
 * Se usan records (Java 16+): clases inmutables y compactas solo para transportar datos.
 */
public record DashboardResumen(
        // --- Indicadores (KPI) ---
        double ventasHoy, int numVentasHoy, double ventasAyer,
        double ventasMes, int numVentasMes, double ventasMesAnterior,
        double ticketPromedio, double ticketPromedioAnterior,
        double utilidadMes, double margenMes,
        double comprasMes, int numComprasMes,
        int totalClientes, int totalPrendas, int unidadesStock, double valorInventario,
        double metaMensual,
        // --- Gráficos ---
        Map<LocalDate, Double> ventasPorDia,          // periodo elegido (con días en 0)
        List<PuntoMes> ventasVsCompras,               // últimos 6 meses
        List<ItemMonto> ventasPorCategoria,           // periodo elegido
        // --- Listas ---
        List<ItemRanking> topPrendas,
        List<ItemRanking> topVendedores,
        List<VentaReciente> ultimasVentas,
        List<ItemStock> stockBajo
) {

    /** Ventas y compras de un mes (para el gráfico de barras). */
    public record PuntoMes(String mes, double ventas, double compras) { }

    /** Nombre + monto (por ejemplo, categoría y lo que vendió). */
    public record ItemMonto(String nombre, double monto) { }

    /** Elemento de un ranking: nombre, cantidad y monto. */
    public record ItemRanking(String nombre, int cantidad, double monto) { }

    /** Una venta para la lista de "Últimas ventas". */
    public record VentaReciente(long idVenta, LocalDate fecha, String cliente, String vendedor, double total) { }

    /** Prenda con poco stock. */
    public record ItemStock(String nombre, String categoria, int stock) { }
}
