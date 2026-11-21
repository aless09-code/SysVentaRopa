package pe.edu.upeu.sysventas.service.impl;

import pe.edu.upeu.sysventas.dto.DashboardResumen;
import pe.edu.upeu.sysventas.dto.DashboardResumen.PuntoMes;
import pe.edu.upeu.sysventas.repository.DashboardRepository;
import pe.edu.upeu.sysventas.service.IDashboardService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reglas del dashboard: arma los periodos, compara contra el periodo anterior y rellena días vacíos. */
public class DashboardServiceImp implements IDashboardService {

    private static final Locale ES = Locale.of("es", "PE");
    private final DashboardRepository repo;

    public DashboardServiceImp(DashboardRepository repo) {
        this.repo = repo;
    }

    @Override
    public DashboardResumen resumen(int diasPeriodo) {
        LocalDate hoy = LocalDate.now();
        LocalDate ayer = hoy.minusDays(1);

        // Mes actual (hasta hoy) vs. el mismo tramo del mes anterior, para una comparación justa
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        LocalDate inicioMesAnt = inicioMes.minusMonths(1);
        LocalDate finMesAnt = inicioMesAnt.plusDays(Math.min(hoy.getDayOfMonth(), inicioMesAnt.lengthOfMonth()) - 1);
        LocalDate finMesAntCompleto = inicioMes.minusDays(1);

        double[] vHoy = repo.totalVentas(hoy, hoy);
        double[] vAyer = repo.totalVentas(ayer, ayer);
        double[] vMes = repo.totalVentas(inicioMes, hoy);
        double[] vMesAnt = repo.totalVentas(inicioMesAnt, finMesAnt);
        double[] vMesAntCompleto = repo.totalVentas(inicioMesAnt, finMesAntCompleto);
        double[] util = repo.utilidad(inicioMes, hoy);
        double[] cMes = repo.totalCompras(inicioMes, hoy);
        double[] inv = repo.inventario();

        double ticket = vMes[1] == 0 ? 0 : vMes[0] / vMes[1];
        double ticketAnt = vMesAnt[1] == 0 ? 0 : vMesAnt[0] / vMesAnt[1];
        double margen = util[1] == 0 ? 0 : util[0] / util[1] * 100.0;

        // Meta del mes: 10 % más que todo el mes anterior (redondeado a centenas); mínimo S/ 5,000
        double meta = Math.max(5000, Math.ceil(vMesAntCompleto[0] * 1.10 / 100.0) * 100.0);

        // Periodo de los gráficos
        LocalDate desde = hoy.minusDays(diasPeriodo - 1L);
        Map<LocalDate, Double> porDia = new LinkedHashMap<>();
        Map<LocalDate, Double> conVentas = repo.ventasPorDia(desde, hoy);
        for (LocalDate d = desde; !d.isAfter(hoy); d = d.plusDays(1)) {
            porDia.put(d, conVentas.getOrDefault(d, 0.0));
        }

        return new DashboardResumen(
                vHoy[0], (int) vHoy[1], vAyer[0],
                vMes[0], (int) vMes[1], vMesAnt[0],
                ticket, ticketAnt,
                util[0], margen,
                cMes[0], (int) cMes[1],
                repo.totalClientes(), (int) inv[0], (int) inv[1], inv[2],
                meta,
                porDia,
                ventasVsCompras(6),
                repo.ventasPorCategoria(desde, hoy),
                repo.topPrendas(desde, hoy, 5),
                repo.topVendedores(desde, hoy, 4),
                repo.ultimasVentas(6),
                repo.stockBajo(STOCK_MINIMO, 6)
        );
    }

    /** Totales de ventas y compras de los últimos N meses (incluido el actual). */
    private List<PuntoMes> ventasVsCompras(int meses) {
        YearMonth actual = YearMonth.now();
        YearMonth primero = actual.minusMonths(meses - 1L);
        LocalDate desde = primero.atDay(1);
        LocalDate hasta = actual.atEndOfMonth();

        Map<YearMonth, double[]> acumulado = new LinkedHashMap<>();
        for (int i = 0; i < meses; i++) acumulado.put(primero.plusMonths(i), new double[2]);
        repo.ventasPorDia(desde, hasta).forEach((f, t) -> acumulado.get(YearMonth.from(f))[0] += t);
        repo.comprasPorDia(desde, hasta).forEach((f, t) -> acumulado.get(YearMonth.from(f))[1] += t);

        List<PuntoMes> lista = new ArrayList<>();
        acumulado.forEach((ym, v) -> {
            String nombre = ym.getMonth().getDisplayName(TextStyle.SHORT, ES).replace(".", "");
            nombre = nombre.substring(0, 1).toUpperCase() + nombre.substring(1);
            lista.add(new PuntoMes(nombre + " " + String.valueOf(ym.getYear()).substring(2), v[0], v[1]));
        });
        return lista;
    }
}
