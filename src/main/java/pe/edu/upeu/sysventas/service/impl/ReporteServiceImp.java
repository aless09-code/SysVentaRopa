package pe.edu.upeu.sysventas.service.impl;

import pe.edu.upeu.sysventas.dto.FiltroReporte;
import pe.edu.upeu.sysventas.enums.TipoReporte;
import pe.edu.upeu.sysventas.model.Producto;
import pe.edu.upeu.sysventas.reporte.Reporte;
import pe.edu.upeu.sysventas.reporte.Reporte.Alin;
import pe.edu.upeu.sysventas.reporte.Reporte.Columna;
import pe.edu.upeu.sysventas.reporte.Reporte.Kpi;
import pe.edu.upeu.sysventas.reporte.Reporte.Tabla;
import pe.edu.upeu.sysventas.reporte.Reporte.Tono;
import pe.edu.upeu.sysventas.repository.ReporteRepository;
import pe.edu.upeu.sysventas.repository.ReporteRepository.ClienteFila;
import pe.edu.upeu.sysventas.repository.ReporteRepository.CompraFila;
import pe.edu.upeu.sysventas.repository.ReporteRepository.GrupoFila;
import pe.edu.upeu.sysventas.repository.ReporteRepository.ItemFila;
import pe.edu.upeu.sysventas.repository.ReporteRepository.KardexFila;
import pe.edu.upeu.sysventas.repository.ReporteRepository.Opcion;
import pe.edu.upeu.sysventas.repository.ReporteRepository.PrendaVendidaFila;
import pe.edu.upeu.sysventas.repository.ReporteRepository.ProductoFila;
import pe.edu.upeu.sysventas.repository.ReporteRepository.ProveedorFila;
import pe.edu.upeu.sysventas.repository.ReporteRepository.UsuarioFila;
import pe.edu.upeu.sysventas.repository.ReporteRepository.VentaFila;
import pe.edu.upeu.sysventas.service.IReporteService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arma cada reporte: consulta los datos (ReporteRepository), calcula totales
 * y los acomoda en tablas, indicadores y secciones de un {@link Reporte}.
 */
public class ReporteServiceImp implements IReporteService {

    private static final Locale ES = Locale.of("es", "PE");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", ES);

    private final ReporteRepository repo;

    public ReporteServiceImp(ReporteRepository repo) {
        this.repo = repo;
    }

    @Override public List<Opcion> clientes() { return repo.clientes(); }
    @Override public List<Opcion> proveedores() { return repo.proveedores(); }
    @Override public List<Opcion> productos() { return repo.productos(); }
    @Override public List<Opcion> categorias() { return repo.categorias(); }
    @Override public List<Opcion> usuarios() { return repo.usuarios(); }

    @Override
    public Reporte generar(TipoReporte tipo, FiltroReporte f) {
        return switch (tipo) {
            case ESTADO_CUENTA -> estadoCuenta(f);
            case ESTADO_CUENTA_CLIENTE -> estadoCuentaCliente(f);
            case ESTADO_CUENTA_PROVEEDOR -> estadoCuentaProveedor(f);
            case VENTAS -> ventas(f);
            case COMPRAS -> compras(f);
            case PRENDAS_VENDIDAS -> prendasVendidas(f);
            case INVENTARIO -> inventario(f);
            case KARDEX -> kardex(f);
            case CLIENTES -> clientes(f);
            case PROVEEDORES -> proveedores(f);
            case USUARIOS -> usuarios(f);
            case CATALOGO -> catalogo(f);
        };
    }

    // =====================================================================
    // 1. ESTADO DE CUENTA GENERAL
    // =====================================================================

    private record Movimiento(LocalDate fecha, boolean ingreso, long id, String documento, String tercero,
                              String usuario, double monto) { }

    private Reporte estadoCuenta(FiltroReporte f) {
        Reporte r = base(TipoReporte.ESTADO_CUENTA, f,
                "Ingresos por ventas y egresos por compras del período, con saldo acumulado").horizontal(true);

        List<VentaFila> ventas = repo.ventas(f.desde(), f.hasta(), null, null);
        List<CompraFila> compras = repo.compras(f.desde(), f.hasta(), null, null);
        double[] antes = repo.acumuladoAntesDe(f.desde());
        double saldoAnterior = antes[0] - antes[1];

        List<Movimiento> movs = new ArrayList<>();
        for (VentaFila v : ventas) {
            movs.add(new Movimiento(v.fecha(), true, v.id(), comprobante(v), v.cliente(), v.usuario(), v.total()));
        }
        for (CompraFila c : compras) {
            movs.add(new Movimiento(c.fecha(), false, c.id(), documento(c), c.proveedor(), c.usuario(), c.total()));
        }
        movs.sort(Comparator.comparing(Movimiento::fecha)
                .thenComparing(m -> m.ingreso() ? 0 : 1).thenComparing(Movimiento::id));

        double ingresos = suma(ventas, VentaFila::total);
        double egresos = suma(compras, CompraFila::total);
        double igvVentas = suma(ventas, VentaFila::igv);
        double igvCompras = suma(compras, CompraFila::igv);
        double resultado = ingresos - egresos;
        double saldoFinal = saldoAnterior + resultado;

        r.resumen(
                new Kpi("Saldo anterior", m(saldoAnterior)),
                new Kpi("Ingresos (ventas)", m(ingresos), Tono.POSITIVO),
                new Kpi("Egresos (compras)", m(egresos), Tono.NEGATIVO),
                new Kpi("Resultado del período", m(resultado), tono(resultado)),
                new Kpi("Saldo final", m(saldoFinal), tono(saldoFinal)),
                new Kpi("N° de ventas", entero(ventas.size())),
                new Kpi("N° de compras", entero(compras.size())),
                new Kpi("IGV de ventas", m(igvVentas)),
                new Kpi("IGV de compras (crédito)", m(igvCompras)),
                new Kpi("IGV neto por pagar", m(igvVentas - igvCompras), tono(-(igvVentas - igvCompras))));

        r.seccion("Movimientos del período");
        Tabla t = r.tabla(
                new Columna("N°", 0.5, Alin.CENTRO), new Columna("Fecha", 0.9), new Columna("Tipo", 0.8),
                new Columna("Documento", 1.4), new Columna("Cliente / Proveedor", 3), new Columna("Usuario", 1.8),
                new Columna("Ingreso", 1.1, Alin.DERECHA), new Columna("Egreso", 1.1, Alin.DERECHA),
                new Columna("Saldo", 1.2, Alin.DERECHA));
        t.grupo("Saldo anterior al " + fecha(f.desde()) + ":  " + m(saldoAnterior));
        double saldo = saldoAnterior;
        int n = 1;
        for (Movimiento mv : movs) {
            saldo += mv.ingreso() ? mv.monto() : -mv.monto();
            t.dato(String.valueOf(n++), fecha(mv.fecha()), mv.ingreso() ? "Venta" : "Compra", mv.documento(),
                    mv.tercero(), mv.usuario(), mv.ingreso() ? m(mv.monto()) : "", mv.ingreso() ? "" : m(mv.monto()), m(saldo));
        }
        t.total("TOTAL (" + movs.size() + " movimientos)", "", "", "", "", "", m(ingresos), m(egresos), m(saldoFinal));

        // Resumen mensual
        r.seccion("Resumen por mes");
        Tabla mes = r.tabla(new Columna("Mes", 2), new Columna("N° ventas", 1, Alin.DERECHA),
                new Columna("Ingresos", 1.4, Alin.DERECHA), new Columna("N° compras", 1, Alin.DERECHA),
                new Columna("Egresos", 1.4, Alin.DERECHA), new Columna("Resultado", 1.4, Alin.DERECHA));
        Map<YearMonth, double[]> porMes = new TreeMap<>();   // {nVentas, ingresos, nCompras, egresos}
        for (VentaFila v : ventas) {
            double[] a = porMes.computeIfAbsent(YearMonth.from(v.fecha()), k -> new double[4]);
            a[0]++;
            a[1] += v.total();
        }
        for (CompraFila c : compras) {
            double[] a = porMes.computeIfAbsent(YearMonth.from(c.fecha()), k -> new double[4]);
            a[2]++;
            a[3] += c.total();
        }
        porMes.forEach((ym, a) -> mes.dato(nombreMes(ym), entero(a[0]), m(a[1]), entero(a[2]), m(a[3]), m(a[1] - a[3])));
        mes.total("TOTAL", entero(ventas.size()), m(ingresos), entero(compras.size()), m(egresos), m(resultado));

        // Ventas por vendedor
        r.seccion("Ingresos por vendedor");
        Tabla vend = r.tabla(new Columna("Vendedor", 3), new Columna("N° ventas", 1, Alin.DERECHA),
                new Columna("Total vendido", 1.4, Alin.DERECHA), new Columna("% del total", 1, Alin.DERECHA));
        agrupar(ventas, VentaFila::usuario, VentaFila::total).forEach((k, a) ->
                vend.dato(k, entero(a[0]), m(a[1]), pct(a[1], ingresos)));
        vend.total("TOTAL", entero(ventas.size()), m(ingresos), pct(ingresos, ingresos));

        // Compras por proveedor
        r.seccion("Egresos por proveedor");
        Tabla prov = r.tabla(new Columna("Proveedor", 3), new Columna("N° compras", 1, Alin.DERECHA),
                new Columna("Total comprado", 1.4, Alin.DERECHA), new Columna("% del total", 1, Alin.DERECHA));
        agrupar(compras, CompraFila::proveedor, CompraFila::total).forEach((k, a) ->
                prov.dato(k, entero(a[0]), m(a[1]), pct(a[1], egresos)));
        prov.total("TOTAL", entero(compras.size()), m(egresos), pct(egresos, egresos));

        r.nota("Los montos incluyen IGV (18 %). Saldo = saldo anterior + ingresos - egresos. El saldo anterior es "
                + "la diferencia entre todas las ventas y compras registradas antes del " + fecha(f.desde()) + ".");
        return r;
    }

    // =====================================================================
    // 2. ESTADO DE CUENTA POR CLIENTE
    // =====================================================================

    private Reporte estadoCuentaCliente(FiltroReporte f) {
        Reporte r = base(TipoReporte.ESTADO_CUENTA_CLIENTE, f, "Compras realizadas por el cliente y saldo acumulado");
        r.filtro("Cliente", nombreOpcion(repo.clientes(), f.idCliente(), "Todos"));
        List<VentaFila> ventas = repo.ventas(f.desde(), f.hasta(), f.idCliente(), null);
        Map<Long, List<ItemFila>> items = f.detalle() ? porDocumento(repo.itemsVentas(f.desde(), f.hasta())) : Map.of();

        Map<Long, List<VentaFila>> porCliente = ventas.stream()
                .collect(Collectors.groupingBy(VentaFila::idCliente, LinkedHashMap::new, Collectors.toList()));
        double total = suma(ventas, VentaFila::total);

        if (f.idCliente() != null && !ventas.isEmpty()) {
            VentaFila c = ventas.get(0);
            List<String[]> ficha = new ArrayList<>();
            ficha.add(new String[]{"RUC".equals(c.tipoDocCliente()) ? "Razón social" : "Cliente", c.cliente()});
            ficha.add(new String[]{c.tipoDocCliente(), c.docCliente()});
            ficha.add(new String[]{"Dirección", texto(c.direccion())});
            ficha.add(new String[]{"Celular", texto(c.celular())});
            r.ficha(ficha);
        }
        r.resumen(
                new Kpi(f.idCliente() == null ? "Clientes con compras" : "Comprobantes", entero(
                        f.idCliente() == null ? porCliente.size() : ventas.size())),
                new Kpi("Total comprado", m(total), Tono.POSITIVO),
                new Kpi("Ticket promedio", m(ventas.isEmpty() ? 0 : total / ventas.size())),
                new Kpi("Última compra", ventas.isEmpty() ? "-" : fecha(ventas.get(ventas.size() - 1).fecha())));

        r.seccion("Detalle de movimientos");
        Tabla t = r.tabla(new Columna("Fecha", 1), new Columna("Comprobante", 1.4), new Columna("Atendido por", 2.4),
                new Columna("Op. gravada", 1.1, Alin.DERECHA), new Columna("IGV", 0.9, Alin.DERECHA),
                new Columna("Total", 1.1, Alin.DERECHA), new Columna("Acumulado", 1.2, Alin.DERECHA))
                .siVacia("El cliente no tiene compras en el período elegido.");
        porCliente.forEach((id, lista) -> {
            VentaFila c = lista.get(0);
            t.grupo(c.cliente() + "   ·   " + c.tipoDocCliente() + " " + c.docCliente());
            double acum = 0;
            for (VentaFila v : lista) {
                acum += v.total();
                t.dato(fecha(v.fecha()), comprobante(v), v.usuario(), m(v.neto()), m(v.igv()), m(v.total()), m(acum));
                for (ItemFila it : items.getOrDefault(v.id(), List.of())) {
                    t.detalle("", "   " + it.cantidad() + " x " + it.prenda() + "  (" + m(it.precio()) + " c/u)", "",
                            m(it.importe()));
                }
            }
            t.subtotal("Subtotal (" + lista.size() + ")", "", "", m(suma(lista, VentaFila::neto)),
                    m(suma(lista, VentaFila::igv)), m(acum), "");
        });
        t.total("TOTAL (" + ventas.size() + " comprobantes)", "", "", m(suma(ventas, VentaFila::neto)),
                m(suma(ventas, VentaFila::igv)), m(total), "");
        return r;
    }

    // =====================================================================
    // 3. ESTADO DE CUENTA POR PROVEEDOR
    // =====================================================================

    private Reporte estadoCuentaProveedor(FiltroReporte f) {
        Reporte r = base(TipoReporte.ESTADO_CUENTA_PROVEEDOR, f, "Compras realizadas al proveedor y monto acumulado");
        r.filtro("Proveedor", nombreOpcion(repo.proveedores(), f.idProveedor(), "Todos"));
        List<CompraFila> compras = repo.compras(f.desde(), f.hasta(), f.idProveedor(), null);
        Map<Long, List<ItemFila>> items = f.detalle() ? porDocumento(repo.itemsCompras(f.desde(), f.hasta())) : Map.of();
        Map<Long, List<CompraFila>> porProv = compras.stream()
                .collect(Collectors.groupingBy(CompraFila::idProveedor, LinkedHashMap::new, Collectors.toList()));
        double total = suma(compras, CompraFila::total);

        if (f.idProveedor() != null && !compras.isEmpty()) {
            CompraFila c = compras.get(0);
            List<String[]> ficha = new ArrayList<>();
            ficha.add(new String[]{"Razón social", c.proveedor()});
            ficha.add(new String[]{"RUC", c.ruc()});
            ficha.add(new String[]{"Contacto", texto(c.contacto())});
            ficha.add(new String[]{"Celular", texto(c.celular())});
            ficha.add(new String[]{"Dirección", texto(c.direccion())});
            ficha.add(new String[]{"Email", texto(c.email())});
            r.ficha(ficha);
        }
        r.resumen(
                new Kpi(f.idProveedor() == null ? "Proveedores" : "Documentos",
                        entero(f.idProveedor() == null ? porProv.size() : compras.size())),
                new Kpi("Total comprado", m(total), Tono.NEGATIVO),
                new Kpi("IGV (crédito fiscal)", m(suma(compras, CompraFila::igv))),
                new Kpi("Última compra", compras.isEmpty() ? "-" : fecha(compras.get(compras.size() - 1).fecha())));

        r.seccion("Detalle de movimientos");
        Tabla t = r.tabla(new Columna("Fecha", 1), new Columna("Documento", 1.6), new Columna("Registrado por", 2.2),
                new Columna("Neto", 1.1, Alin.DERECHA), new Columna("IGV", 0.9, Alin.DERECHA),
                new Columna("Total", 1.1, Alin.DERECHA), new Columna("Acumulado", 1.2, Alin.DERECHA))
                .siVacia("No hay compras al proveedor en el período elegido.");
        porProv.forEach((id, lista) -> {
            CompraFila c = lista.get(0);
            t.grupo(c.proveedor() + "   ·   RUC " + c.ruc());
            double acum = 0;
            for (CompraFila cp : lista) {
                acum += cp.total();
                t.dato(fecha(cp.fecha()), documento(cp), cp.usuario(), m(cp.neto()), m(cp.igv()), m(cp.total()), m(acum));
                for (ItemFila it : items.getOrDefault(cp.id(), List.of())) {
                    t.detalle("", "   " + it.cantidad() + " x " + it.prenda() + "  (" + m(it.precio()) + " c/u)", "",
                            m(it.importe()));
                }
            }
            t.subtotal("Subtotal (" + lista.size() + ")", "", "", m(suma(lista, CompraFila::neto)),
                    m(suma(lista, CompraFila::igv)), m(acum), "");
        });
        t.total("TOTAL (" + compras.size() + " documentos)", "", "", m(suma(compras, CompraFila::neto)),
                m(suma(compras, CompraFila::igv)), m(total), "");
        return r;
    }

    // =====================================================================
    // 4. VENTAS
    // =====================================================================

    private Reporte ventas(FiltroReporte f) {
        Reporte r = base(TipoReporte.VENTAS, f, "Boletas y facturas emitidas, agrupadas por día").horizontal(true);
        r.filtro("Cliente", nombreOpcion(repo.clientes(), f.idCliente(), "Todos"));
        r.filtro("Vendedor", nombreOpcion(repo.usuarios(), f.idUsuario(), "Todos"));
        List<VentaFila> ventas = repo.ventas(f.desde(), f.hasta(), f.idCliente(), f.idUsuario());
        Map<Long, List<ItemFila>> items = f.detalle() ? porDocumento(repo.itemsVentas(f.desde(), f.hasta())) : Map.of();
        double neto = suma(ventas, VentaFila::neto);
        double igv = suma(ventas, VentaFila::igv);
        double total = suma(ventas, VentaFila::total);
        long boletas = ventas.stream().filter(v -> !"RUC".equals(v.tipoDocCliente())).count();

        r.resumen(
                new Kpi("N° de ventas", entero(ventas.size())),
                new Kpi("Boletas / Facturas", boletas + " / " + (ventas.size() - boletas)),
                new Kpi("Op. gravada", m(neto)),
                new Kpi("IGV (18 %)", m(igv)),
                new Kpi("Total vendido", m(total), Tono.POSITIVO));

        r.seccion("Ventas registradas");
        Tabla t = r.tabla(new Columna("N°", 0.5, Alin.CENTRO), new Columna("Comprobante", 1.3),
                new Columna("Cliente", 3), new Columna("DNI / RUC", 1.1), new Columna("Vendedor", 2),
                new Columna("Op. gravada", 1.1, Alin.DERECHA), new Columna("IGV", 0.9, Alin.DERECHA),
                new Columna("Total", 1.1, Alin.DERECHA));
        int[] n = {1};
        ventas.stream().collect(Collectors.groupingBy(VentaFila::fecha, TreeMap::new, Collectors.toList()))
                .forEach((dia, lista) -> {
                    t.grupo(capital(dia.format(DIA)));
                    for (VentaFila v : lista) {
                        t.dato(String.valueOf(n[0]++), comprobante(v), v.cliente(), v.docCliente(), v.usuario(),
                                m(v.neto()), m(v.igv()), m(v.total()));
                        for (ItemFila it : items.getOrDefault(v.id(), List.of())) {
                            t.detalle("", "", "   " + it.cantidad() + " x " + it.prenda(), "", m(it.precio()) + " c/u",
                                    m(it.importe()));
                        }
                    }
                    t.subtotal("Subtotal del día (" + lista.size() + ")", "", "", "", "",
                            m(suma(lista, VentaFila::neto)), m(suma(lista, VentaFila::igv)), m(suma(lista, VentaFila::total)));
                });
        t.total("TOTAL (" + ventas.size() + " ventas)", "", "", "", "", m(neto), m(igv), m(total));
        return r;
    }

    // =====================================================================
    // 5. COMPRAS
    // =====================================================================

    private Reporte compras(FiltroReporte f) {
        Reporte r = base(TipoReporte.COMPRAS, f, "Compras a proveedores, agrupadas por día").horizontal(true);
        r.filtro("Proveedor", nombreOpcion(repo.proveedores(), f.idProveedor(), "Todos"));
        r.filtro("Usuario", nombreOpcion(repo.usuarios(), f.idUsuario(), "Todos"));
        List<CompraFila> compras = repo.compras(f.desde(), f.hasta(), f.idProveedor(), f.idUsuario());
        Map<Long, List<ItemFila>> items = f.detalle() ? porDocumento(repo.itemsCompras(f.desde(), f.hasta())) : Map.of();
        double neto = suma(compras, CompraFila::neto);
        double igv = suma(compras, CompraFila::igv);
        double total = suma(compras, CompraFila::total);

        r.resumen(
                new Kpi("N° de compras", entero(compras.size())),
                new Kpi("Proveedores", entero(compras.stream().map(CompraFila::idProveedor).distinct().count())),
                new Kpi("Neto", m(neto)),
                new Kpi("IGV (crédito fiscal)", m(igv)),
                new Kpi("Total comprado", m(total), Tono.NEGATIVO));

        r.seccion("Compras registradas");
        Tabla t = r.tabla(new Columna("N°", 0.5, Alin.CENTRO), new Columna("Documento", 1.6),
                new Columna("Proveedor", 3), new Columna("RUC", 1.1), new Columna("Registrado por", 1.8),
                new Columna("Neto", 1.1, Alin.DERECHA), new Columna("IGV", 0.9, Alin.DERECHA),
                new Columna("Total", 1.1, Alin.DERECHA));
        int[] n = {1};
        compras.stream().collect(Collectors.groupingBy(CompraFila::fecha, TreeMap::new, Collectors.toList()))
                .forEach((dia, lista) -> {
                    t.grupo(capital(dia.format(DIA)));
                    for (CompraFila c : lista) {
                        t.dato(String.valueOf(n[0]++), documento(c), c.proveedor(), c.ruc(), c.usuario(),
                                m(c.neto()), m(c.igv()), m(c.total()));
                        for (ItemFila it : items.getOrDefault(c.id(), List.of())) {
                            t.detalle("", "", "   " + it.cantidad() + " x " + it.prenda(), "", m(it.precio()) + " c/u",
                                    m(it.importe()));
                        }
                    }
                    t.subtotal("Subtotal del día (" + lista.size() + ")", "", "", "", "",
                            m(suma(lista, CompraFila::neto)), m(suma(lista, CompraFila::igv)), m(suma(lista, CompraFila::total)));
                });
        t.total("TOTAL (" + compras.size() + " compras)", "", "", "", "", m(neto), m(igv), m(total));
        return r;
    }

    // =====================================================================
    // 6. VENTAS POR PRENDA Y CATEGORÍA
    // =====================================================================

    private Reporte prendasVendidas(FiltroReporte f) {
        Reporte r = base(TipoReporte.PRENDAS_VENDIDAS, f, "Unidades vendidas, costo, utilidad y margen por prenda");
        r.filtro("Categoría", nombreOpcion(repo.categorias(), f.idCategoria(), "Todas"));
        List<PrendaVendidaFila> filas = repo.prendasVendidas(f.desde(), f.hasta(), f.idCategoria());
        int unidades = filas.stream().mapToInt(PrendaVendidaFila::unidades).sum();
        double venta = suma(filas, PrendaVendidaFila::venta);
        double costo = suma(filas, PrendaVendidaFila::costo);

        r.resumen(
                new Kpi("Unidades vendidas", entero(unidades)),
                new Kpi("Venta neta (sin IGV)", m(venta), Tono.POSITIVO),
                new Kpi("Costo de lo vendido", m(costo)),
                new Kpi("Utilidad bruta", m(venta - costo), tono(venta - costo)),
                new Kpi("Margen", pct(venta - costo, venta)),
                new Kpi("Prendas distintas", entero(filas.size())));

        r.seccion("Detalle por categoría");
        Tabla t = r.tabla(new Columna("N°", 0.5, Alin.CENTRO), new Columna("Prenda", 3.2),
                new Columna("Unid.", 0.8, Alin.DERECHA), new Columna("Venta neta", 1.2, Alin.DERECHA),
                new Columna("Costo", 1.1, Alin.DERECHA), new Columna("Utilidad", 1.1, Alin.DERECHA),
                new Columna("Margen", 0.9, Alin.DERECHA), new Columna("% venta", 0.9, Alin.DERECHA));
        int[] n = {1};
        filas.stream().collect(Collectors.groupingBy(PrendaVendidaFila::categoria, LinkedHashMap::new, Collectors.toList()))
                .forEach((cat, lista) -> {
                    t.grupo("Categoría: " + cat);
                    for (PrendaVendidaFila p : lista) {
                        t.dato(String.valueOf(n[0]++), p.prenda(), entero(p.unidades()), m(p.venta()), m(p.costo()),
                                m(p.venta() - p.costo()), pct(p.venta() - p.costo(), p.venta()), pct(p.venta(), venta));
                    }
                    double v = suma(lista, PrendaVendidaFila::venta);
                    double c = suma(lista, PrendaVendidaFila::costo);
                    t.subtotal("Subtotal (" + lista.size() + ")", "",
                            entero(lista.stream().mapToInt(PrendaVendidaFila::unidades).sum()),
                            m(v), m(c), m(v - c), pct(v - c, v), pct(v, venta));
                });
        t.total("TOTAL (" + filas.size() + " prendas)", "", entero(unidades), m(venta), m(costo), m(venta - costo),
                pct(venta - costo, venta), pct(venta, venta));
        r.nota("Venta neta = precio de venta sin IGV. Costo = precio unitario de costo x unidades. "
                + "Utilidad = venta neta - costo. Margen = utilidad / venta neta.");
        return r;
    }

    // =====================================================================
    // 7. INVENTARIO
    // =====================================================================

    private Reporte inventario(FiltroReporte f) {
        Reporte r = base(TipoReporte.INVENTARIO, f, "Existencias valorizadas por categoría").horizontal(true);
        String txt = f.textoBuscar();
        if (!txt.isEmpty()) r.filtro("Prenda contiene", f.texto().trim());
        r.filtro("Categoría", nombreOpcion(repo.categorias(), f.idCategoria(), "Todas"));
        if (f.stockMaximo() != null) r.filtro("Stock máximo", entero(f.stockMaximo()));
        r.filtro("Solo stock bajo (<= " + FiltroReporte.STOCK_BAJO + ")", f.soloStockBajo() ? "Sí" : "No");

        List<ProductoFila> prods = repo.productos(f.idCategoria(), null).stream()
                .filter(p -> txt.isEmpty() || p.nombre().toLowerCase().contains(txt))
                .filter(p -> f.stockMaximo() == null || p.stock() <= f.stockMaximo())
                .filter(p -> !f.soloStockBajo() || p.stock() <= FiltroReporte.STOCK_BAJO)
                .toList();

        long unidades = prods.stream().mapToLong(ProductoFila::stock).sum();
        double valorCosto = suma(prods, p -> p.stock() * p.costo());
        double valorVenta = suma(prods, p -> p.stock() * precioVenta(p));
        long bajos = prods.stream().filter(p -> p.stock() <= FiltroReporte.STOCK_BAJO).count();
        r.resumen(
                new Kpi("Prendas", entero(prods.size())),
                new Kpi("Unidades en stock", entero(unidades)),
                new Kpi("Valor al costo", m(valorCosto)),
                new Kpi("Valor a precio de venta", m(valorVenta), Tono.POSITIVO),
                new Kpi("Con stock bajo", entero(bajos), bajos > 0 ? Tono.NEGATIVO : Tono.POSITIVO));

        r.seccion("Existencias");
        Tabla t = r.tabla(new Columna("N°", 0.5, Alin.CENTRO), new Columna("Prenda", 3.2), new Columna("Unidad", 1.5),
                new Columna("Costo", 1, Alin.DERECHA), new Columna("% Util.", 0.8, Alin.DERECHA),
                new Columna("P. Venta", 1, Alin.DERECHA), new Columna("Stock", 0.8, Alin.DERECHA),
                new Columna("Valor costo", 1.2, Alin.DERECHA), new Columna("Valor venta", 1.2, Alin.DERECHA),
                new Columna("Estado", 0.9, Alin.CENTRO));
        int[] n = {1};
        prods.stream().collect(Collectors.groupingBy(ProductoFila::categoria, LinkedHashMap::new, Collectors.toList()))
                .forEach((cat, lista) -> {
                    t.grupo("Categoría: " + cat);
                    for (ProductoFila p : lista) {
                        t.dato(String.valueOf(n[0]++), p.nombre(), p.unidad(), m(p.costo()), dec(p.porceUtil()) + " %",
                                m(precioVenta(p)), entero(p.stock()), m(p.stock() * p.costo()),
                                m(p.stock() * precioVenta(p)), estadoStock(p.stock()));
                    }
                    t.subtotal("Subtotal (" + lista.size() + ")", "", "", "", "", "",
                            entero(lista.stream().mapToLong(ProductoFila::stock).sum()),
                            m(suma(lista, p -> p.stock() * p.costo())), m(suma(lista, p -> p.stock() * precioVenta(p))), "");
                });
        t.total("TOTAL (" + prods.size() + " registros)", "", "", "", "", "", entero(unidades), m(valorCosto), m(valorVenta), "");
        r.nota("Estado: AGOTADO = sin stock; BAJO = " + FiltroReporte.STOCK_BAJO + " unidades o menos; OK = stock suficiente. "
                + "P. Venta = costo + % de utilidad (sin IGV).");
        return r;
    }

    // =====================================================================
    // 8. KARDEX
    // =====================================================================

    private Reporte kardex(FiltroReporte f) {
        Reporte r = base(TipoReporte.KARDEX, f, "Entradas (compras), salidas (ventas) y saldo de unidades").horizontal(true);
        r.filtro("Prenda", nombreOpcion(repo.productos(), f.idProducto(), "Todas con movimientos"));

        List<ProductoFila> prods = repo.productos(null, f.idProducto());
        Map<Long, List<KardexFila>> movs = repo.movimientosDesde(f.desde(), f.idProducto()).stream()
                .sorted(Comparator.comparing(KardexFila::fecha).thenComparing(k -> k.entrada() ? 0 : 1)
                        .thenComparing(KardexFila::idDocumento))
                .collect(Collectors.groupingBy(KardexFila::idProducto));

        int totEntradas = 0, totSalidas = 0, numMovs = 0, prendas = 0;
        boolean saldoNegativo = false;
        Tabla t = r.tabla(new Columna("Fecha", 0.9), new Columna("Movimiento", 1.1), new Columna("Documento", 1.6),
                new Columna("Cliente / Proveedor", 2.8), new Columna("Entrada", 0.8, Alin.DERECHA),
                new Columna("Salida", 0.8, Alin.DERECHA), new Columna("Saldo", 0.8, Alin.DERECHA),
                new Columna("Costo unit.", 1, Alin.DERECHA), new Columna("Valor", 1.1, Alin.DERECHA))
                .siVacia("No hay movimientos de stock en el período elegido.");

        for (ProductoFila p : prods) {
            List<KardexFila> lista = movs.getOrDefault(p.id(), List.of());
            // saldo al inicio del período = stock actual - entradas desde entonces + salidas desde entonces
            int entradasDesde = lista.stream().filter(KardexFila::entrada).mapToInt(KardexFila::cantidad).sum();
            int salidasDesde = lista.stream().filter(k -> !k.entrada()).mapToInt(KardexFila::cantidad).sum();
            int saldo = p.stock() - entradasDesde + salidasDesde;
            List<KardexFila> periodo = lista.stream().filter(k -> !k.fecha().isAfter(f.hasta())).toList();
            if (periodo.isEmpty() && f.idProducto() == null) continue;   // solo prendas con movimientos

            prendas++;
            t.grupo(p.nombre() + "   ·   " + p.categoria() + "   ·   Stock actual: " + p.stock());
            if (saldo < 0) saldoNegativo = true;
            t.dato(fecha(f.desde()), "Saldo inicial", "", "", "", "", entero(saldo), m(p.costo()), m(saldo * p.costo()));
            int ent = 0, sal = 0;
            for (KardexFila k : periodo) {
                saldo += k.entrada() ? k.cantidad() : -k.cantidad();
                if (k.entrada()) ent += k.cantidad(); else sal += k.cantidad();
                t.dato(fecha(k.fecha()), k.entrada() ? "Entrada (compra)" : "Salida (venta)", k.documento(), k.tercero(),
                        k.entrada() ? entero(k.cantidad()) : "", k.entrada() ? "" : entero(k.cantidad()), entero(saldo),
                        m(k.costoUnit()), m(k.cantidad() * k.costoUnit()));
            }
            t.subtotal("Totales (" + periodo.size() + " mov.)", "", "", "", entero(ent), entero(sal), entero(saldo), "", "");
            totEntradas += ent;
            totSalidas += sal;
            numMovs += periodo.size();
        }
        t.total("TOTAL GENERAL (" + numMovs + " movimientos)", "", "", "", entero(totEntradas), entero(totSalidas), "", "", "");

        // Los indicadores van antes de la tabla
        r.bloques().add(0, new Reporte.Resumen(List.of(
                new Kpi("Prendas con movimientos", entero(prendas)),
                new Kpi("N° de movimientos", entero(numMovs)),
                new Kpi("Unidades que entraron", entero(totEntradas), Tono.POSITIVO),
                new Kpi("Unidades que salieron", entero(totSalidas), Tono.NEGATIVO),
                new Kpi("Variación neta", entero(totEntradas - totSalidas), tono(totEntradas - totSalidas)))));
        r.bloques().add(1, new Reporte.Seccion("Movimientos por prenda"));

        r.nota("El saldo inicial se calcula a partir del stock actual: stock actual - entradas + salidas posteriores al "
                + fecha(f.desde()) + ". Entradas valorizadas al precio de compra; salidas al costo registrado en la venta.");
        if (saldoNegativo) {
            r.nota("Atención: alguna prenda muestra saldo negativo. Esto ocurre cuando hay ventas que no descontaron stock "
                    + "(por ejemplo, los datos de demostración generados automáticamente).");
        }
        return r;
    }

    // =====================================================================
    // 9. CLIENTES
    // =====================================================================

    private Reporte clientes(FiltroReporte f) {
        Reporte r = base(TipoReporte.CLIENTES, f, "Clientes registrados y sus compras en el período").horizontal(true);
        String txt = f.textoBuscar();
        if (!txt.isEmpty()) r.filtro("Buscar", f.texto().trim());
        List<ClienteFila> lista = repo.clientes(f.desde(), f.hasta()).stream()
                .filter(c -> txt.isEmpty() || c.nombre().toLowerCase().contains(txt) || c.doc().contains(txt))
                .toList();
        double total = suma(lista, ClienteFila::total);
        long conRuc = lista.stream().filter(c -> "RUC".equals(c.tipoDoc())).count();
        long activos = lista.stream().filter(c -> c.compras() > 0).count();
        r.resumen(
                new Kpi("Clientes", entero(lista.size())),
                new Kpi("Personas (DNI)", entero(lista.size() - conRuc)),
                new Kpi("Empresas (RUC)", entero(conRuc)),
                new Kpi("Compraron en el período", entero(activos)),
                new Kpi("Total comprado", m(total), Tono.POSITIVO));

        r.seccion("Listado de clientes (ordenado por monto comprado)");
        Tabla t = r.tabla(new Columna("N°", 0.5, Alin.CENTRO), new Columna("Doc.", 0.6), new Columna("Número", 1.1),
                new Columna("Nombre / Razón social", 3), new Columna("Celular", 1), new Columna("Dirección", 2.4),
                new Columna("Compras", 0.8, Alin.DERECHA), new Columna("Total comprado", 1.2, Alin.DERECHA),
                new Columna("Última", 0.9, Alin.CENTRO));
        int n = 1;
        for (ClienteFila c : lista) {
            t.dato(String.valueOf(n++), c.tipoDoc(), c.doc(), c.nombre(), texto(c.celular()), texto(c.direccion()),
                    entero(c.compras()), m(c.total()), c.ultima() == null ? "-" : fecha(c.ultima()));
        }
        t.total("TOTAL (" + lista.size() + " clientes)", "", "", "", "", "",
                entero(lista.stream().mapToInt(ClienteFila::compras).sum()), m(total), "");
        return r;
    }

    // =====================================================================
    // 10. PROVEEDORES
    // =====================================================================

    private Reporte proveedores(FiltroReporte f) {
        Reporte r = base(TipoReporte.PROVEEDORES, f, "Proveedores registrados y compras del período").horizontal(true);
        String txt = f.textoBuscar();
        if (!txt.isEmpty()) r.filtro("Buscar", f.texto().trim());
        List<ProveedorFila> lista = repo.proveedores(f.desde(), f.hasta()).stream()
                .filter(p -> txt.isEmpty() || p.razonSocial().toLowerCase().contains(txt) || p.ruc().contains(txt))
                .toList();
        double total = suma(lista, ProveedorFila::total);
        r.resumen(
                new Kpi("Proveedores", entero(lista.size())),
                new Kpi("Con compras en el período", entero(lista.stream().filter(p -> p.compras() > 0).count())),
                new Kpi("N° de compras", entero(lista.stream().mapToInt(ProveedorFila::compras).sum())),
                new Kpi("Total comprado", m(total), Tono.NEGATIVO));

        r.seccion("Listado de proveedores (ordenado por monto comprado)");
        Tabla t = r.tabla(new Columna("N°", 0.5, Alin.CENTRO), new Columna("RUC", 1.1),
                new Columna("Razón social", 3), new Columna("Contacto", 1.8), new Columna("Celular", 1),
                new Columna("Email", 2), new Columna("Compras", 0.8, Alin.DERECHA),
                new Columna("Total comprado", 1.2, Alin.DERECHA), new Columna("Última", 0.9, Alin.CENTRO));
        int n = 1;
        for (ProveedorFila p : lista) {
            t.dato(String.valueOf(n++), p.ruc(), p.razonSocial(), texto(p.contacto()), texto(p.celular()), texto(p.email()),
                    entero(p.compras()), m(p.total()), p.ultima() == null ? "-" : fecha(p.ultima()));
        }
        t.total("TOTAL (" + lista.size() + " proveedores)", "", "", "", "", "",
                entero(lista.stream().mapToInt(ProveedorFila::compras).sum()), m(total), "");
        return r;
    }

    // =====================================================================
    // 11. ACTIVIDAD DE USUARIOS
    // =====================================================================

    private Reporte usuarios(FiltroReporte f) {
        Reporte r = base(TipoReporte.USUARIOS, f, "Ventas y compras registradas por cada usuario").horizontal(true);
        List<UsuarioFila> lista = repo.actividadUsuarios(f.desde(), f.hasta());
        double tv = suma(lista, UsuarioFila::totalVentas);
        double tc = suma(lista, UsuarioFila::totalCompras);
        int nv = lista.stream().mapToInt(UsuarioFila::ventas).sum();
        int nc = lista.stream().mapToInt(UsuarioFila::compras).sum();
        r.resumen(
                new Kpi("Usuarios", entero(lista.size())),
                new Kpi("Ventas registradas", entero(nv)),
                new Kpi("Total vendido", m(tv), Tono.POSITIVO),
                new Kpi("Compras registradas", entero(nc)),
                new Kpi("Total comprado", m(tc), Tono.NEGATIVO));

        r.seccion("Actividad por usuario");
        Tabla t = r.tabla(new Columna("N°", 0.5, Alin.CENTRO), new Columna("Usuario", 2.6), new Columna("Perfil", 2.2),
                new Columna("Ventas", 0.8, Alin.DERECHA), new Columna("Unid. vendidas", 1, Alin.DERECHA),
                new Columna("Total vendido", 1.2, Alin.DERECHA), new Columna("Ticket prom.", 1.1, Alin.DERECHA),
                new Columna("% ventas", 0.8, Alin.DERECHA), new Columna("Compras", 0.8, Alin.DERECHA),
                new Columna("Total comprado", 1.2, Alin.DERECHA));
        int n = 1;
        for (UsuarioFila u : lista) {
            t.dato(String.valueOf(n++), u.usuario(), u.perfil(), entero(u.ventas()), entero(u.unidades()),
                    m(u.totalVentas()), m(u.ventas() == 0 ? 0 : u.totalVentas() / u.ventas()), pct(u.totalVentas(), tv),
                    entero(u.compras()), m(u.totalCompras()));
        }
        t.total("TOTAL", "", "", entero(nv), entero(lista.stream().mapToInt(UsuarioFila::unidades).sum()), m(tv),
                m(nv == 0 ? 0 : tv / nv), pct(tv, tv), entero(nc), m(tc));
        return r;
    }

    // =====================================================================
    // 12. CATÁLOGO
    // =====================================================================

    private Reporte catalogo(FiltroReporte f) {
        Reporte r = base(TipoReporte.CATALOGO, f, "Prendas, stock y valor por categoría y unidad de medida");
        List<GrupoFila> cats = repo.resumenCategorias();
        List<GrupoFila> unis = repo.resumenUnidades();
        r.resumen(
                new Kpi("Categorías", entero(cats.size())),
                new Kpi("Unidades de medida", entero(unis.size())),
                new Kpi("Prendas registradas", entero(cats.stream().mapToInt(GrupoFila::prendas).sum())),
                new Kpi("Valor del inventario", m(suma(cats, GrupoFila::valorCosto))));
        r.seccion("Categorías");
        tablaGrupo(r, "Categoría", cats);
        r.seccion("Unidades de medida");
        tablaGrupo(r, "Unidad de medida", unis);
        return r;
    }

    private void tablaGrupo(Reporte r, String titulo, List<GrupoFila> lista) {
        Tabla t = r.tabla(new Columna("N°", 0.5, Alin.CENTRO), new Columna(titulo, 3),
                new Columna("Prendas", 0.9, Alin.DERECHA), new Columna("Unid. en stock", 1.1, Alin.DERECHA),
                new Columna("Valor costo", 1.3, Alin.DERECHA), new Columna("Valor venta", 1.3, Alin.DERECHA));
        int n = 1;
        for (GrupoFila g : lista) {
            t.dato(String.valueOf(n++), g.nombre(), entero(g.prendas()), entero(g.stock()), m(g.valorCosto()), m(g.valorVenta()));
        }
        t.total("TOTAL", "", entero(lista.stream().mapToInt(GrupoFila::prendas).sum()),
                entero(lista.stream().mapToLong(GrupoFila::stock).sum()),
                m(suma(lista, GrupoFila::valorCosto)), m(suma(lista, GrupoFila::valorVenta)));
    }

    // =====================================================================
    // utilidades
    // =====================================================================

    private Reporte base(TipoReporte tipo, FiltroReporte f, String subtitulo) {
        Reporte r = new Reporte(tipo.getNombre().toUpperCase(ES), subtitulo).usuario(f.usuarioSesion());
        if (tipo.usa(TipoReporte.Filtro.FECHAS)) {
            r.filtro("Desde", fecha(f.desde())).filtro("Hasta", fecha(f.hasta()));
        }
        return r;
    }

    private static <T> double suma(List<T> lista, java.util.function.ToDoubleFunction<T> campo) {
        return lista.stream().mapToDouble(campo).sum();
    }

    /** Agrupa por una clave: {cantidad, suma} ordenado de mayor a menor suma. */
    private static <T> Map<String, double[]> agrupar(List<T> lista, Function<T, String> clave,
                                                     java.util.function.ToDoubleFunction<T> monto) {
        Map<String, double[]> mapa = new LinkedHashMap<>();
        for (T x : lista) {
            double[] a = mapa.computeIfAbsent(clave.apply(x), k -> new double[2]);
            a[0]++;
            a[1] += monto.applyAsDouble(x);
        }
        return mapa.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static Map<Long, List<ItemFila>> porDocumento(List<ItemFila> items) {
        return items.stream().collect(Collectors.groupingBy(ItemFila::idDocumento));
    }

    private static String nombreOpcion(List<Opcion> opciones, Long id, String siNulo) {
        if (id == null) return siNulo;
        return opciones.stream().filter(o -> id.equals(o.id())).map(Opcion::nombre).findFirst().orElse(siNulo);
    }

    /** Boleta (DNI) o factura (RUC), igual que en Comprobante. */
    static String comprobante(VentaFila v) {
        return ("RUC".equals(v.tipoDocCliente()) ? "F001-" : "B001-") + String.format("%08d", v.id());
    }

    static String documento(CompraFila c) {
        return c.tipoDoc() + " " + c.serie() + "-" + c.numDoc();
    }

    private static double precioVenta(ProductoFila p) {
        return Producto.calcularPrecioVenta(p.costo(), p.porceUtil());
    }

    private static String estadoStock(int stock) {
        if (stock <= 0) return "AGOTADO";
        return stock <= FiltroReporte.STOCK_BAJO ? "BAJO" : "OK";
    }

    private static Tono tono(double v) {
        return v < 0 ? Tono.NEGATIVO : Tono.POSITIVO;
    }

    static String m(double v) {
        return String.format(Locale.US, "S/ %,.2f", v);
    }

    private static String dec(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    private static String entero(double v) {
        return String.format(Locale.US, "%,d", Math.round(v));
    }

    private static String pct(double parte, double total) {
        return total == 0 ? "0.0 %" : String.format(Locale.US, "%.1f %%", parte * 100 / total);
    }

    private static String fecha(LocalDate d) {
        return d == null ? "" : d.format(FECHA);
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(ES) + s.substring(1);
    }

    private static String nombreMes(YearMonth ym) {
        return capital(ym.getMonth().getDisplayName(TextStyle.FULL, ES)) + " " + ym.getYear();
    }
}
