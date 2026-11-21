package pe.edu.upeu.sysventas.controller;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import javafx.util.StringConverter;
import pe.edu.upeu.sysventas.components.Iconos;
import pe.edu.upeu.sysventas.dto.DashboardResumen;
import pe.edu.upeu.sysventas.dto.DashboardResumen.ItemMonto;
import pe.edu.upeu.sysventas.dto.DashboardResumen.ItemRanking;
import pe.edu.upeu.sysventas.dto.DashboardResumen.ItemStock;
import pe.edu.upeu.sysventas.dto.DashboardResumen.PuntoMes;
import pe.edu.upeu.sysventas.dto.DashboardResumen.VentaReciente;
import pe.edu.upeu.sysventas.dto.SessionManager;
import pe.edu.upeu.sysventas.service.IDashboardService;
import pe.edu.upeu.sysventas.utils.UtilsX;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.DoubleFunction;
import java.util.prefs.Preferences;

/**
 * Dashboard de inicio: indicadores (KPI), gráficos y listas con el estado de la tienda.
 * Los datos se consultan en un hilo aparte (Task) para que la ventana no se congele.
 */
public class DashboardController {

    /** Botón de acceso rápido que muestra el banner (lo arma MainguiController según el perfil). */
    public record Acceso(String icono, String texto, Runnable accion) { }

    private final IDashboardService dashboardService;

    @FXML VBox raiz, leyendaCategorias, listaTop, listaUltimas, listaStock, listaVendedores;
    @FXML HBox hero, accionesBox;
    @FXML GridPane kpiGrid;
    @FXML Label lblHeroFecha, lblSaludo, lblResumenHoy, lblMetaTitulo, lblMetaValor, lblMetaDetalle;
    @FXML Label lblTituloResumen, lblSubResumen, lblTituloTendencia, lblSubTendencia, lblTotalPeriodo;
    @FXML Label lblTituloCategorias, lblSubCategorias, lblDonaValor, lblDonaLabel;
    @FXML Label lblTituloMeses, lblSubMeses, lblTituloTop, lblSubTop;
    @FXML Label lblTituloUltimas, lblTituloStock, lblNumStock, lblTituloVendedores;
    @FXML ProgressBar pbMeta;
    @FXML ToggleGroup grupoPeriodo;
    @FXML ToggleButton tg7, tg30, tg90;
    @FXML Button btnActualizar;
    @FXML AreaChart<String, Number> chartVentas;
    @FXML CategoryAxis ejeXVentas;
    @FXML NumberAxis ejeYVentas, ejeYMeses;
    @FXML BarChart<String, Number> chartMeses;
    @FXML PieChart pieCategorias;
    @FXML StackPane donaPane;
    @FXML Circle donaHueco;

    private static final DecimalFormat MONEDA = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat ENTERO = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat UN_DECIMAL = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final Locale ES = Locale.of("es", "PE");

    private Properties idioma = new Properties();
    private int periodo = 30;

    public DashboardController(IDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @FXML
    public void initialize() {
        idioma = new UtilsX().detectLanguage(Preferences.userRoot().node("sysventaropa").get("IDIOMAX", "es"));
        traducir();
        btnActualizar.setGraphic(Iconos.crear("refrescar", 16));

        // Selector de periodo: siempre debe quedar uno elegido
        tg7.setUserData(7);
        tg30.setUserData(30);
        tg90.setUserData(90);
        (periodo == 7 ? tg7 : periodo == 90 ? tg90 : tg30).setSelected(true);
        grupoPeriodo.selectedToggleProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null) {
                anterior.setSelected(true);
                return;
            }
            periodo = (int) nuevo.getUserData();
            cargar();
        });

        // Formato "S/ 1.2k" para los ejes de dinero
        ejeYVentas.setTickLabelFormatter(convertidorCorto());
        ejeYMeses.setTickLabelFormatter(convertidorCorto());

        armarBanner();
        cargar();
    }

    /** Textos de la pantalla según el idioma elegido. */
    private void traducir() {
        lblTituloResumen.setText(t("dash.resumen", "Resumen del negocio"));
        lblSubResumen.setText(t("dash.resumen.sub", "Indicadores del mes y del periodo elegido."));
        lblMetaTitulo.setText(t("dash.meta", "META DEL MES"));
        lblTituloTendencia.setText(t("dash.tendencia", "Ventas del periodo"));
        lblTituloCategorias.setText(t("dash.categorias", "Ventas por categoría"));
        lblSubCategorias.setText(t("dash.categorias.sub", "Participación en el periodo (sin IGV)"));
        lblTituloMeses.setText(t("dash.meses", "Ventas vs. compras"));
        lblSubMeses.setText(t("dash.meses.sub", "Últimos 6 meses (con IGV)"));
        lblTituloTop.setText(t("dash.top", "Prendas más vendidas"));
        lblSubTop.setText(t("dash.top.sub", "Unidades vendidas en el periodo"));
        lblTituloUltimas.setText(t("dash.ultimas", "Últimas ventas"));
        lblTituloStock.setText(t("dash.stock", "Stock bajo"));
        lblTituloVendedores.setText(t("dash.vendedores", "Ranking de vendedores"));
        lblDonaLabel.setText(t("dash.total", "TOTAL"));
        btnActualizar.setText(t("dash.actualizar", "Actualizar"));
        String dias = t("dash.dias", "días");
        tg7.setText("7 " + dias);
        tg30.setText("30 " + dias);
        tg90.setText("90 " + dias);
    }

    private String t(String clave, String porDefecto) {
        return idioma.getProperty(clave, porDefecto);
    }

    /** Saludo según la hora del día + fecha. */
    private void armarBanner() {
        int hora = LocalTime.now().getHour();
        String saludo = hora < 12 ? t("dash.dias.saludo", "Buenos días")
                : hora < 19 ? t("dash.tardes", "Buenas tardes") : t("dash.noches", "Buenas noches");
        lblSaludo.setText(saludo + ", " + primerNombre(SessionManager.getInstance().getUserName()));
        lblHeroFecha.setText(LocalDate.now()
                .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, yyyy", ES)).toUpperCase(ES));
        lblResumenHoy.setText(t("dash.cargando", "Preparando el resumen de tu tienda..."));
    }

    /** Lo llama MainguiController para mostrar los accesos rápidos permitidos al perfil. */
    public void mostrarAccesos(List<Acceso> accesos) {
        accionesBox.getChildren().clear();
        for (Acceso a : accesos) {
            Button b = new Button(a.texto(), Iconos.crear(a.icono(), 16));
            b.getStyleClass().add("hero-accion");
            b.setOnAction(e -> a.accion().run());
            accionesBox.getChildren().add(b);
        }
    }

    // =====================================================================
    //  CARGA DE DATOS (en segundo plano)
    // =====================================================================
    @FXML
    public void actualizar() {
        cargar();
    }

    private void cargar() {
        final int dias = periodo;
        btnActualizar.setDisable(true);
        raiz.setOpacity(0.97);

        Task<DashboardResumen> tarea = new Task<>() {
            @Override
            protected DashboardResumen call() {
                return dashboardService.resumen(dias);
            }
        };
        tarea.setOnSucceeded(e -> {
            btnActualizar.setDisable(false);
            raiz.setOpacity(1);
            pintar(tarea.getValue());
        });
        tarea.setOnFailed(e -> {
            btnActualizar.setDisable(false);
            raiz.setOpacity(1);
            Throwable ex = tarea.getException();
            if (ex != null) ex.printStackTrace();
            lblResumenHoy.setText(t("dash.error", "No se pudo cargar el resumen: ")
                    + (ex == null ? "" : ex.getMessage()));
        });
        Thread hilo = new Thread(tarea, "dashboard-carga");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void pintar(DashboardResumen r) {
        pintarBanner(r);
        pintarKpis(r);
        pintarTendencia(r);
        pintarCategorias(r.ventasPorCategoria());
        pintarMeses(r.ventasVsCompras());
        pintarTopPrendas(r.topPrendas());
        pintarUltimas(r.ultimasVentas());
        pintarStock(r.stockBajo());
        pintarVendedores(r.topVendedores());
    }

    // =====================================================================
    //  BANNER
    // =====================================================================
    private void pintarBanner(DashboardResumen r) {
        if (r.numVentasHoy() == 0) {
            lblResumenHoy.setText(t("dash.hoy.vacio", "Aún no hay ventas hoy. ¡Que empiece la jornada!"));
        } else {
            String plantilla = t("dash.hoy", "Hoy llevas %s en %d ventas. %s");
            String animo = r.ventasHoy() >= r.ventasAyer()
                    ? t("dash.hoy.mejor", "¡Vas mejor que ayer!") : t("dash.hoy.sigue", "¡Sigue así!");
            lblResumenHoy.setText(String.format(plantilla, soles(r.ventasHoy()), r.numVentasHoy(), animo));
        }
        double avance = r.metaMensual() <= 0 ? 0 : r.ventasMes() / r.metaMensual();
        animarNumero(lblMetaValor, r.ventasMes(), this::soles);
        Timeline barra = new Timeline(new KeyFrame(Duration.millis(1100),
                new KeyValue(pbMeta.progressProperty(), Math.min(1.0, avance), Interpolator.EASE_OUT)));
        pbMeta.setProgress(0);
        barra.play();
        lblMetaDetalle.setText(Math.round(avance * 100) + "% " + t("dash.de", "de") + " " + soles(r.metaMensual()));
    }

    // =====================================================================
    //  KPI
    // =====================================================================
    private void pintarKpis(DashboardResumen r) {
        kpiGrid.getChildren().clear();
        String vsAyer = t("dash.vs.ayer", "vs. ayer");
        String vsMes = t("dash.vs.mes", "vs. mismo tramo del mes anterior");

        kpiGrid.add(kpi("tendencia", "c0", t("dash.kpi.hoy", "Ventas de hoy"), r.ventasHoy(), this::soles,
                delta(r.ventasHoy(), r.ventasAyer()), r.numVentasHoy() + " " + t("dash.ventas", "ventas") + " · " + vsAyer), 0, 0);
        kpiGrid.add(kpi("billetera", "c1", t("dash.kpi.mes", "Ingresos del mes"), r.ventasMes(), this::soles,
                delta(r.ventasMes(), r.ventasMesAnterior()), vsMes), 1, 0);
        kpiGrid.add(kpi("ticket", "c2", t("dash.kpi.ticket", "Ticket promedio"), r.ticketPromedio(), this::soles,
                delta(r.ticketPromedio(), r.ticketPromedioAnterior()), r.numVentasMes() + " " + t("dash.ventas.mes", "ventas este mes")), 2, 0);
        Label margen = new Label(UN_DECIMAL.format(r.margenMes()) + " %");
        margen.getStyleClass().add("chip-neutro");
        kpiGrid.add(kpi("estrella", "c3", t("dash.kpi.utilidad", "Utilidad estimada"), r.utilidadMes(), this::soles,
                margen, t("dash.margen", "margen sobre la venta neta del mes")), 3, 0);

        kpiGrid.add(kpiMini("cliente", "c4", t("dash.kpi.clientes", "Clientes"), r.totalClientes(), ENTERO::format), 0, 1);
        kpiGrid.add(kpiMini("producto", "c5", t("dash.kpi.prendas", "Prendas en catálogo"), r.totalPrendas(), ENTERO::format), 1, 1);
        kpiGrid.add(kpiMini("caja", "c2", t("dash.kpi.unidades", "Unidades en stock") + " · " + soles(r.valorInventario()),
                r.unidadesStock(), ENTERO::format), 2, 1);
        kpiGrid.add(kpiMini("compra", "c1", t("dash.kpi.compras", "Compras del mes") + " · " + r.numComprasMes(),
                r.comprasMes(), this::soles), 3, 1);

        int i = 0;
        for (Node n : kpiGrid.getChildren()) aparecer(n, i++ * 60);
    }

    private VBox kpi(String icono, String color, String titulo, double valor, DoubleFunction<String> fmt,
                     Node etiqueta, String pie) {
        StackPane ico = new StackPane(Iconos.crear(icono, 20));
        ico.getStyleClass().addAll("kpi-icono", color);
        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("kpi-titulo");
        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);
        HBox cabecera = new HBox(10, lblTitulo, esp, ico);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        Label lblValor = new Label(fmt.apply(0));
        lblValor.getStyleClass().add("kpi-valor");
        animarNumero(lblValor, valor, fmt);

        Label lblPie = new Label(pie);
        lblPie.getStyleClass().add("kpi-pie");
        lblPie.setWrapText(true);
        HBox pieBox = new HBox(8, etiqueta, lblPie);
        pieBox.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(cabecera, lblValor, pieBox);
        card.getStyleClass().add("kpi");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private HBox kpiMini(String icono, String color, String titulo, double valor, DoubleFunction<String> fmt) {
        StackPane ico = new StackPane(Iconos.crear(icono, 18));
        ico.getStyleClass().addAll("kpi-icono", color);
        Label lblValor = new Label(fmt.apply(0));
        lblValor.getStyleClass().add("kpi-valor");
        animarNumero(lblValor, valor, fmt);
        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("kpi-pie");
        HBox card = new HBox(ico, new VBox(2, lblValor, lblTitulo));
        card.getStyleClass().addAll("kpi", "kpi-mini");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /** Chip verde/rojo con la variación porcentual frente al periodo anterior. */
    private Label delta(double actual, double anterior) {
        Label l = new Label();
        if (anterior <= 0) {
            l.setText(actual > 0 ? t("dash.nuevo", "Nuevo") : "—");
            l.getStyleClass().add("chip-neutro");
        } else {
            double pct = (actual - anterior) / anterior * 100.0;
            l.setText((pct >= 0 ? "▲ " : "▼ ") + UN_DECIMAL.format(Math.abs(pct)) + " %");
            l.getStyleClass().add(pct >= 0 ? "chip-exito" : "chip-peligro");
        }
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    // =====================================================================
    //  GRÁFICO DE TENDENCIA (área)
    // =====================================================================
    private void pintarTendencia(DashboardResumen r) {
        Map<LocalDate, Double> dias = r.ventasPorDia();
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        List<String> categorias = new ArrayList<>();
        double total = 0;

        if (periodo > 31) {
            // 90 días: se agrupa por semana para que el gráfico se lea mejor
            DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM", ES);
            LocalDate inicio = dias.keySet().iterator().next();
            double acumulado = 0;
            LocalDate inicioSemana = inicio;
            for (Map.Entry<LocalDate, Double> e : dias.entrySet()) {
                if (ChronoUnit.DAYS.between(inicioSemana, e.getKey()) >= 7) {
                    String cat = inicioSemana.format(f).replace(".", "");
                    categorias.add(cat);
                    serie.getData().add(new XYChart.Data<>(cat, acumulado));
                    inicioSemana = e.getKey();
                    acumulado = 0;
                }
                acumulado += e.getValue();
                total += e.getValue();
            }
            String cat = inicioSemana.format(f).replace(".", "");
            categorias.add(cat);
            serie.getData().add(new XYChart.Data<>(cat, acumulado));
            lblSubTendencia.setText(t("dash.tendencia.semanal", "Total semanal de los últimos 90 días (con IGV)"));
        } else {
            DateTimeFormatter f = periodo <= 7 ? DateTimeFormatter.ofPattern("EEE d", ES) : DateTimeFormatter.ofPattern("d", ES);
            for (Map.Entry<LocalDate, Double> e : dias.entrySet()) {
                String cat = e.getKey().format(f).replace(".", "");
                categorias.add(cat);
                serie.getData().add(new XYChart.Data<>(cat, e.getValue()));
                total += e.getValue();
            }
            lblSubTendencia.setText(String.format(t("dash.tendencia.sub", "Total diario de los últimos %d días (con IGV)"), periodo));
        }

        chartVentas.setCreateSymbols(serie.getData().size() <= 31);
        chartVentas.getData().clear();
        chartVentas.getData().add(serie);
        lblTotalPeriodo.setText(soles(total));

        for (XYChart.Data<String, Number> d : serie.getData()) {
            if (d.getNode() != null) {
                Tooltip.install(d.getNode(), new Tooltip(d.getXValue() + "\n" + soles(d.getYValue().doubleValue())));
            }
        }
        aparecer(chartVentas, 0);
    }

    // =====================================================================
    //  DONA DE CATEGORÍAS
    // =====================================================================
    private void pintarCategorias(List<ItemMonto> datos) {
        // Máximo 5 categorías + "Otras" para que la dona sea legible
        List<ItemMonto> lista = new ArrayList<>();
        double otras = 0;
        for (int i = 0; i < datos.size(); i++) {
            if (i < 5) lista.add(datos.get(i));
            else otras += datos.get(i).monto();
        }
        if (otras > 0) lista.add(new ItemMonto(t("dash.otras", "Otras"), otras));
        double total = lista.stream().mapToDouble(ItemMonto::monto).sum();

        pieCategorias.getData().clear();
        leyendaCategorias.getChildren().clear();
        lblDonaValor.setText(solesCorto(total));
        if (total <= 0) {
            donaHueco.setVisible(false);
            leyendaCategorias.getChildren().add(vacio("caja", t("dash.sin.ventas", "Sin ventas en el periodo")));
            return;
        }
        donaHueco.setVisible(true);
        for (int i = 0; i < lista.size(); i++) {
            ItemMonto m = lista.get(i);
            PieChart.Data d = new PieChart.Data(m.nombre(), m.monto());
            pieCategorias.getData().add(d);
            double pct = m.monto() / total * 100.0;
            if (d.getNode() != null) {
                Tooltip.install(d.getNode(), new Tooltip(m.nombre() + "\n" + soles(m.monto()) + " · " + UN_DECIMAL.format(pct) + " %"));
            }

            Region punto = new Region();
            punto.getStyleClass().addAll("punto", "c" + (i % 6));
            Label nombre = new Label(m.nombre());
            nombre.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nombre, Priority.ALWAYS);
            Label monto = new Label(soles(m.monto()));
            monto.getStyleClass().add("fila-sub");
            Label porc = new Label(UN_DECIMAL.format(pct) + " %");
            porc.getStyleClass().add("dato");
            porc.setMinWidth(52);
            porc.setAlignment(Pos.CENTER_RIGHT);
            HBox fila = new HBox(10, punto, nombre, monto, porc);
            fila.setAlignment(Pos.CENTER_LEFT);
            leyendaCategorias.getChildren().add(fila);
        }
        aparecer(donaPane, 0);
    }

    // =====================================================================
    //  BARRAS: VENTAS VS COMPRAS
    // =====================================================================
    private void pintarMeses(List<PuntoMes> meses) {
        XYChart.Series<String, Number> ventas = new XYChart.Series<>();
        ventas.setName(t("dash.serie.ventas", "Ventas"));
        XYChart.Series<String, Number> compras = new XYChart.Series<>();
        compras.setName(t("dash.serie.compras", "Compras"));
        for (PuntoMes p : meses) {
            ventas.getData().add(new XYChart.Data<>(p.mes(), p.ventas()));
            compras.getData().add(new XYChart.Data<>(p.mes(), p.compras()));
        }
        chartMeses.getData().clear();
        chartMeses.getData().add(ventas);
        chartMeses.getData().add(compras);
        for (XYChart.Series<String, Number> s : chartMeses.getData()) {
            for (XYChart.Data<String, Number> d : s.getData()) {
                if (d.getNode() != null) {
                    Tooltip.install(d.getNode(), new Tooltip(s.getName() + " · " + d.getXValue() + "\n" + soles(d.getYValue().doubleValue())));
                }
            }
        }
        aparecer(chartMeses, 0);
    }

    // =====================================================================
    //  LISTAS
    // =====================================================================
    private void pintarTopPrendas(List<ItemRanking> top) {
        listaTop.getChildren().clear();
        if (top.isEmpty()) {
            listaTop.getChildren().add(vacio("producto", t("dash.sin.ventas", "Sin ventas en el periodo")));
            return;
        }
        int max = top.get(0).cantidad();
        int i = 0;
        for (ItemRanking p : top) {
            StackPane rank = new StackPane(new Label(String.valueOf(i + 1)));
            rank.getStyleClass().add("ranking");
            if (i == 0) rank.getStyleClass().add("oro");
            Label nombre = new Label(p.nombre());
            nombre.getStyleClass().add("fila-titulo");
            nombre.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nombre, Priority.ALWAYS);
            Label unidades = new Label(ENTERO.format(p.cantidad()) + " " + t("dash.uds", "uds."));
            unidades.getStyleClass().add("dato");
            HBox fila = new HBox(10, nombre, unidades);
            fila.setAlignment(Pos.CENTER_LEFT);

            ProgressBar barra = new ProgressBar(0);
            barra.getStyleClass().add("barra");
            barra.setMaxWidth(Double.MAX_VALUE);
            animarBarra(barra, max == 0 ? 0 : (double) p.cantidad() / max, i * 90);
            Label monto = new Label(soles(p.monto()) + " " + t("dash.sin.igv", "sin IGV"));
            monto.getStyleClass().add("fila-sub");

            VBox info = new VBox(5, fila, barra, monto);
            HBox.setHgrow(info, Priority.ALWAYS);
            HBox item = new HBox(12, rank, info);
            item.setAlignment(Pos.TOP_LEFT);
            listaTop.getChildren().add(item);
            i++;
        }
    }

    private void pintarUltimas(List<VentaReciente> ventas) {
        listaUltimas.getChildren().clear();
        if (ventas.isEmpty()) {
            listaUltimas.getChildren().add(vacio("venta", t("dash.sin.registros", "Aún no hay ventas registradas")));
            return;
        }
        DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM", ES);
        LocalDate hoy = LocalDate.now();
        int i = 0;
        for (VentaReciente v : ventas) {
            StackPane av = new StackPane(new Label(iniciales(v.cliente())));
            av.getStyleClass().addAll("mini-avatar", "c" + (Math.abs(v.cliente().hashCode()) % 6));
            Label cliente = new Label(v.cliente());
            cliente.getStyleClass().add("fila-titulo");
            String cuando = v.fecha().equals(hoy) ? t("dash.hoy.corto", "Hoy")
                    : v.fecha().equals(hoy.minusDays(1)) ? t("dash.ayer", "Ayer") : v.fecha().format(f).replace(".", "");
            Label sub = new Label("#" + String.format("%05d", v.idVenta()) + " · " + cuando + " · " + primerNombre(v.vendedor()));
            sub.getStyleClass().add("fila-sub");
            VBox textos = new VBox(2, cliente, sub);
            HBox.setHgrow(textos, Priority.ALWAYS);
            Label monto = new Label(soles(v.total()));
            monto.getStyleClass().add("fila-monto");
            monto.setMinWidth(Region.USE_PREF_SIZE);
            HBox fila = new HBox(12, av, textos, monto);
            fila.getStyleClass().add("fila-lista");
            listaUltimas.getChildren().add(fila);
            aparecer(fila, i++ * 50);
        }
    }

    private void pintarStock(List<ItemStock> stock) {
        listaStock.getChildren().clear();
        lblNumStock.setText(String.valueOf(stock.size()));
        lblNumStock.getStyleClass().removeAll("chip-aviso", "chip-exito");
        lblNumStock.getStyleClass().add(stock.isEmpty() ? "chip-exito" : "chip-aviso");
        if (stock.isEmpty()) {
            listaStock.getChildren().add(vacio("check", t("dash.stock.ok", "¡Todo el inventario está bien abastecido!")));
            return;
        }
        int minimo = IDashboardService.STOCK_MINIMO;
        for (ItemStock s : stock) {
            Label nombre = new Label(s.nombre());
            nombre.getStyleClass().add("fila-titulo");
            nombre.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nombre, Priority.ALWAYS);
            Label cant = new Label(s.stock() + " " + t("dash.uds", "uds."));
            boolean critico = s.stock() <= minimo / 2;
            cant.getStyleClass().add(critico ? "chip-peligro" : "chip-aviso");
            cant.setMinWidth(Region.USE_PREF_SIZE);
            HBox fila = new HBox(8, nombre, cant);
            fila.setAlignment(Pos.CENTER_LEFT);
            ProgressBar barra = new ProgressBar(0);
            barra.getStyleClass().addAll("barra", critico ? "peligro" : "aviso");
            barra.setMaxWidth(Double.MAX_VALUE);
            animarBarra(barra, Math.max(0.03, (double) s.stock() / minimo), 0);
            Label cat = new Label(s.categoria());
            cat.getStyleClass().add("fila-sub");
            listaStock.getChildren().add(new VBox(4, fila, barra, cat));
        }
    }

    private void pintarVendedores(List<ItemRanking> vendedores) {
        listaVendedores.getChildren().clear();
        if (vendedores.isEmpty()) {
            listaVendedores.getChildren().add(vacio("cliente", t("dash.sin.ventas", "Sin ventas en el periodo")));
            return;
        }
        int i = 0;
        for (ItemRanking v : vendedores) {
            StackPane av = new StackPane(new Label(iniciales(v.nombre())));
            av.getStyleClass().addAll("mini-avatar", "c" + (i % 6));
            Label nombre = new Label(v.nombre());
            nombre.getStyleClass().add("fila-titulo");
            Label sub = new Label(v.cantidad() + " " + t("dash.ventas", "ventas"));
            sub.getStyleClass().add("fila-sub");
            VBox textos = new VBox(2, nombre, sub);
            HBox.setHgrow(textos, Priority.ALWAYS);
            Label monto = new Label(soles(v.monto()));
            monto.getStyleClass().add("fila-monto");
            monto.setMinWidth(Region.USE_PREF_SIZE);
            HBox fila = new HBox(12, av, textos, monto);
            fila.getStyleClass().add("fila-lista");
            if (i == 0) {
                Node corona = Iconos.crear("estrella", 14);
                corona.getStyleClass().add("estrella-top");
                fila.getChildren().add(1, corona);
            }
            listaVendedores.getChildren().add(fila);
            aparecer(fila, i++ * 60);
        }
    }

    private VBox vacio(String icono, String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("vacio");
        l.setWrapText(true);
        VBox caja = new VBox(Iconos.crear(icono, 28), l);
        caja.getStyleClass().add("vacio-caja");
        return caja;
    }

    // =====================================================================
    //  ANIMACIONES Y FORMATO
    // =====================================================================
    /** Cuenta desde 0 hasta el valor final (efecto "contador"). */
    private void animarNumero(Label label, double valor, DoubleFunction<String> fmt) {
        DoubleProperty p = new SimpleDoubleProperty(0);
        p.addListener((o, a, b) -> label.setText(fmt.apply(b.doubleValue())));
        Timeline t = new Timeline(new KeyFrame(Duration.millis(900), new KeyValue(p, valor, Interpolator.EASE_OUT)));
        t.setOnFinished(e -> label.setText(fmt.apply(valor)));
        t.play();
    }

    private void animarBarra(ProgressBar barra, double destino, int retrasoMs) {
        Timeline t = new Timeline(new KeyFrame(Duration.millis(900),
                new KeyValue(barra.progressProperty(), Math.min(1, destino), Interpolator.EASE_OUT)));
        t.setDelay(Duration.millis(retrasoMs));
        t.play();
    }

    /** Aparece suavemente desde abajo. */
    private void aparecer(Node n, int retrasoMs) {
        n.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(420), n);
        fade.setToValue(1);
        TranslateTransition sube = new TranslateTransition(Duration.millis(420), n);
        sube.setFromY(14);
        sube.setToY(0);
        sube.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition p = new ParallelTransition(fade, sube);
        p.setDelay(Duration.millis(retrasoMs));
        p.play();
    }

    private String soles(double valor) {
        return "S/ " + MONEDA.format(valor);
    }

    private String solesCorto(double valor) {
        if (valor >= 1_000_000) return "S/ " + UN_DECIMAL.format(valor / 1_000_000) + "M";
        if (valor >= 1_000) return "S/ " + UN_DECIMAL.format(valor / 1_000) + "k";
        return "S/ " + ENTERO.format(valor);
    }

    private StringConverter<Number> convertidorCorto() {
        return new StringConverter<>() {
            @Override
            public String toString(Number n) {
                return n == null ? "" : solesCorto(n.doubleValue());
            }

            @Override
            public Number fromString(String s) {
                return 0;
            }
        };
    }

    private static String primerNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return "";
        String limpio = nombre.replaceAll("\\s*\\(.*?\\)", "").trim();
        if (limpio.isEmpty()) limpio = nombre.trim();
        return limpio.split("\\s+")[0];
    }

    private static String iniciales(String nombre) {
        if (nombre == null || nombre.isBlank()) return "?";
        String[] partes = nombre.replaceAll("\\s*\\(.*?\\)", "").trim().split("\\s+");
        String ini = partes[0].isEmpty() ? "?" : partes[0].substring(0, 1);
        if (partes.length > 1 && !partes[1].isEmpty()) ini += partes[1].substring(0, 1);
        return ini.toUpperCase();
    }
}
