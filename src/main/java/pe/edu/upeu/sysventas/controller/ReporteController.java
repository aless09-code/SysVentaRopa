package pe.edu.upeu.sysventas.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import pe.edu.upeu.sysventas.components.Iconos;
import pe.edu.upeu.sysventas.components.Mensaje;
import pe.edu.upeu.sysventas.dto.FiltroReporte;
import pe.edu.upeu.sysventas.dto.SessionManager;
import pe.edu.upeu.sysventas.enums.TipoReporte;
import pe.edu.upeu.sysventas.enums.TipoReporte.Filtro;
import pe.edu.upeu.sysventas.reporte.Maquetador;
import pe.edu.upeu.sysventas.reporte.Pagina;
import pe.edu.upeu.sysventas.reporte.PdfWriter;
import pe.edu.upeu.sysventas.reporte.Reporte;
import pe.edu.upeu.sysventas.reporte.VistaReporte;
import pe.edu.upeu.sysventas.repository.ReporteRepository.Opcion;
import pe.edu.upeu.sysventas.service.IReporteService;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Pantalla "Reportes": elige el reporte, aplica filtros, muestra la vista previa de las hojas
 * y permite guardarlas como PDF o imprimirlas.
 */
@RequiredArgsConstructor
public class ReporteController {

    private static final double ZOOM_MIN = 0.4;
    private static final double ZOOM_MAX = 2.0;

    private final IReporteService rs;

    @FXML VBox miContenedor, vboxPaginas;
    @FXML VBox boxDesde, boxHasta, boxRangos, boxCliente, boxProveedor, boxProducto, boxCategoria, boxUsuario,
            boxTexto, boxStock, boxDetalle;
    @FXML ComboBox<TipoReporte> cbxReporte;
    @FXML ComboBox<Opcion> cbxCliente, cbxProveedor, cbxProducto, cbxCategoria, cbxUsuario;
    @FXML DatePicker dpDesde, dpHasta;
    @FXML TextField txtTexto, txtStockMax;
    @FXML CheckBox chkStockBajo, chkDetalle;
    @FXML Label lblDescripcion, lblTexto, lblUsuario, lblEstado, lblZoom, lblPaginas;
    @FXML Button btnGenerar, btnPdf, btnImprimir;
    @FXML ScrollPane scrollVista;

    private Reporte reporte;
    private List<Pagina> paginas = List.of();
    private final List<Pane> hojas = new ArrayList<>();
    private double zoom = 1.0;
    private boolean generando;

    @FXML
    public void initialize() {
        btnGenerar.setGraphic(Iconos.crear("play", 14));
        btnPdf.setGraphic(Iconos.crear("descargar", 15));
        btnImprimir.setGraphic(Iconos.crear("imprimir", 15));

        // Combos de filtros (la primera opción = todos)
        llenar(cbxCliente, rs::clientes, "(Todos los clientes)");
        llenar(cbxProveedor, rs::proveedores, "(Todos los proveedores)");
        llenar(cbxProducto, rs::productos, "(Todas las prendas)");
        llenar(cbxCategoria, rs::categorias, "(Todas)");
        llenar(cbxUsuario, rs::usuarios, "(Todos)");
        rangoMes();

        // Solo los reportes que el perfil puede ver
        String perfil = SessionManager.getInstance().getUserPerfil();
        cbxReporte.getItems().setAll(TipoReporte.paraPerfil(perfil == null ? "" : perfil));
        cbxReporte.valueProperty().addListener((obs, old, tipo) -> {
            actualizarFiltros(tipo);
            generar();
        });
        mostrarVacio("Elija un reporte y presione Generar.");
        if (!cbxReporte.getItems().isEmpty()) cbxReporte.getSelectionModel().selectFirst();
    }

    private void llenar(ComboBox<Opcion> combo, Supplier<List<Opcion>> datos, String todos) {
        List<Opcion> lista = new ArrayList<>();
        lista.add(new Opcion(null, todos));
        try {
            lista.addAll(datos.get());
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        combo.getItems().setAll(lista);
        combo.getSelectionModel().selectFirst();
    }

    /** Muestra solo los filtros que usa el reporte elegido. */
    private void actualizarFiltros(TipoReporte tipo) {
        if (tipo == null) return;
        lblDescripcion.setText(tipo.getDescripcion());
        ver(boxDesde, tipo.usa(Filtro.FECHAS));
        ver(boxHasta, tipo.usa(Filtro.FECHAS));
        ver(boxRangos, tipo.usa(Filtro.FECHAS));
        ver(boxCliente, tipo.usa(Filtro.CLIENTE));
        ver(boxProveedor, tipo.usa(Filtro.PROVEEDOR));
        ver(boxProducto, tipo.usa(Filtro.PRODUCTO));
        ver(boxCategoria, tipo.usa(Filtro.CATEGORIA));
        ver(boxUsuario, tipo.usa(Filtro.USUARIO));
        ver(boxTexto, tipo.usa(Filtro.TEXTO));
        ver(boxStock, tipo.usa(Filtro.STOCK));
        ver(boxDetalle, tipo.usa(Filtro.DETALLE));

        lblUsuario.setText(tipo == TipoReporte.COMPRAS ? "REGISTRADO POR" : "VENDEDOR");
        String buscar = switch (tipo) {
            case INVENTARIO -> "PRENDA CONTIENE";
            case CLIENTES -> "NOMBRE O DNI / RUC";
            case PROVEEDORES -> "RAZÓN SOCIAL O RUC";
            default -> "BUSCAR";
        };
        lblTexto.setText(buscar);
        txtTexto.setPromptText(buscar.charAt(0) + buscar.substring(1).toLowerCase() + "...");
    }

    private static void ver(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    // ------------------------------------------------------------ períodos rápidos

    @FXML public void rangoHoy() { rango(LocalDate.now(), LocalDate.now()); }

    @FXML public void rangoMes() { rango(LocalDate.now().withDayOfMonth(1), LocalDate.now()); }

    @FXML
    public void rangoMesAnterior() {
        LocalDate ini = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        rango(ini, ini.withDayOfMonth(ini.lengthOfMonth()));
    }

    @FXML public void rangoAnio() { rango(LocalDate.now().withDayOfYear(1), LocalDate.now()); }

    @FXML public void rangoTodo() { rango(LocalDate.of(2000, 1, 1), LocalDate.now()); }

    private void rango(LocalDate desde, LocalDate hasta) {
        dpDesde.setValue(desde);
        dpHasta.setValue(hasta);
        if (cbxReporte.getValue() != null) generar();
    }

    // ------------------------------------------------------------ generar

    @FXML
    public void generar() {
        generarYLuego(null);
    }

    /** Genera el reporte en segundo plano (la ventana no se congela) y luego ejecuta "despues". */
    private void generarYLuego(Runnable despues) {
        TipoReporte tipo = cbxReporte.getValue();
        if (tipo == null || generando) return;
        FiltroReporte filtro = leerFiltros(tipo);
        if (filtro == null) return;

        generando = true;
        estado(true, "Generando reporte...");
        Task<Object[]> tarea = new Task<>() {
            @Override
            protected Object[] call() {
                Reporte r = rs.generar(tipo, filtro);
                return new Object[]{r, Maquetador.maquetar(r)};
            }
        };
        tarea.setOnSucceeded(e -> {
            generando = false;
            reporte = (Reporte) tarea.getValue()[0];
            @SuppressWarnings("unchecked")
            List<Pagina> p = (List<Pagina>) tarea.getValue()[1];
            paginas = p;
            mostrarPaginas();
            estado(false, "Generado: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            if (despues != null) despues.run();
        });
        tarea.setOnFailed(e -> {
            generando = false;
            estado(false, "");
            Throwable ex = tarea.getException();
            if (ex != null) ex.printStackTrace();
            Mensaje.errorDialogo("Reporte", "No se pudo generar el reporte:\n" + (ex == null ? "" : ex.getMessage()));
        });
        Thread hilo = new Thread(tarea, "reporte");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Lee y valida los filtros de la pantalla. Devuelve null si hay un error. */
    private FiltroReporte leerFiltros(TipoReporte tipo) {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        if (tipo.usa(Filtro.FECHAS)) {
            if (desde == null || hasta == null) {
                Mensaje.error(miContenedor, "Seleccione las fechas Desde y Hasta");
                return null;
            }
            if (desde.isAfter(hasta)) {
                Mensaje.error(miContenedor, "La fecha Desde no puede ser mayor que Hasta");
                return null;
            }
        } else {
            desde = LocalDate.of(2000, 1, 1);
            hasta = LocalDate.now();
        }
        Integer stockMax = null;
        String st = txtStockMax.getText() == null ? "" : txtStockMax.getText().trim();
        if (tipo.usa(Filtro.STOCK) && !st.isEmpty()) {
            try {
                stockMax = Integer.parseInt(st);
                if (stockMax < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                Mensaje.error(miContenedor, "El stock máximo debe ser un número entero positivo");
                return null;
            }
        }
        return new FiltroReporte(desde, hasta,
                tipo.usa(Filtro.CLIENTE) ? id(cbxCliente) : null,
                tipo.usa(Filtro.PROVEEDOR) ? id(cbxProveedor) : null,
                tipo.usa(Filtro.PRODUCTO) ? id(cbxProducto) : null,
                tipo.usa(Filtro.CATEGORIA) ? id(cbxCategoria) : null,
                tipo.usa(Filtro.USUARIO) ? id(cbxUsuario) : null,
                tipo.usa(Filtro.TEXTO) ? txtTexto.getText() : null,
                stockMax,
                tipo.usa(Filtro.STOCK) && chkStockBajo.isSelected(),
                tipo.usa(Filtro.DETALLE) && chkDetalle.isSelected(),
                SessionManager.getInstance().getUserName());
    }

    private static Long id(ComboBox<Opcion> combo) {
        Opcion o = combo.getValue();
        return o == null ? null : o.id();
    }

    private void estado(boolean ocupado, String texto) {
        btnGenerar.setDisable(ocupado);
        btnPdf.setDisable(ocupado);
        btnImprimir.setDisable(ocupado);
        lblEstado.setText(texto);
    }

    // ------------------------------------------------------------ vista previa

    private void mostrarVacio(String texto) {
        hojas.clear();
        Label l = new Label(texto);
        l.getStyleClass().add("reporte-vacio");
        vboxPaginas.getChildren().setAll(l);
        lblPaginas.setText("0 páginas");
    }

    private void mostrarPaginas() {
        hojas.clear();
        vboxPaginas.getChildren().clear();
        for (Pagina p : paginas) {
            Pane hoja = VistaReporte.hoja(p);
            hoja.getStyleClass().add("reporte-hoja");
            hojas.add(hoja);
            vboxPaginas.getChildren().add(new Group(hoja));
        }
        lblPaginas.setText(paginas.size() + (paginas.size() == 1 ? " página" : " páginas"));
        aplicarZoom();
        scrollVista.setVvalue(0);
    }

    private void aplicarZoom() {
        for (Pane h : hojas) h.getTransforms().setAll(new Scale(zoom, zoom));
        lblZoom.setText(Math.round(zoom * 100) + "%");
    }

    @FXML
    public void acercar() {
        zoom = Math.min(ZOOM_MAX, Math.round((zoom + 0.1) * 10) / 10.0);
        aplicarZoom();
    }

    @FXML
    public void alejar() {
        zoom = Math.max(ZOOM_MIN, Math.round((zoom - 0.1) * 10) / 10.0);
        aplicarZoom();
    }

    /** Ajusta la hoja al ancho de la vista previa. */
    @FXML
    public void ajustarAncho() {
        if (paginas.isEmpty()) return;
        double disponible = scrollVista.getViewportBounds().getWidth() - 50;
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, disponible / paginas.get(0).ancho()));
        aplicarZoom();
    }

    // ------------------------------------------------------------ PDF e impresión

    @FXML
    public void guardarPdf() {
        if (reporte == null) {
            generarYLuego(this::guardarPdf);
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Guardar reporte en PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));
        String nombre = (cbxReporte.getValue() == null ? "reporte" : cbxReporte.getValue().archivo()) + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf";
        fc.setInitialFileName(nombre);
        File docs = new File(System.getProperty("user.home"), "Documents");
        fc.setInitialDirectory(docs.isDirectory() ? docs : new File(System.getProperty("user.home")));

        File destino = fc.showSaveDialog(miContenedor.getScene().getWindow());
        if (destino == null) return;   // canceló
        if (!destino.getName().toLowerCase().endsWith(".pdf")) {
            destino = new File(destino.getParentFile(), destino.getName() + ".pdf");
        }
        try {
            Files.write(destino.toPath(), PdfWriter.generar(reporte.titulo(), paginas));
        } catch (Exception ex) {
            Mensaje.errorDialogo("Guardar PDF", "No se pudo guardar el archivo:\n" + ex.getMessage());
            return;
        }
        Mensaje.exito(miContenedor, "PDF guardado: " + destino.getName());
        if (Mensaje.confirmar("Guardar PDF", "El reporte se guardó en:\n" + destino.getAbsolutePath()
                + "\n\n¿Desea abrirlo ahora?")) {
            abrir(destino);
        }
    }

    /** Abre el PDF con el programa predeterminado del sistema (en otro hilo para no trabar la ventana). */
    private void abrir(File archivo) {
        Thread hilo = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(archivo);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, "abrir-pdf");
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML
    public void imprimir() {
        if (reporte == null) {
            generarYLuego(this::imprimir);
            return;
        }
        try {
            if (VistaReporte.imprimir(paginas, reporte.horizontal(), miContenedor.getScene().getWindow())) {
                Mensaje.exito(miContenedor, "Reporte enviado a la impresora");
            }
        } catch (Exception ex) {
            Mensaje.errorDialogo("Imprimir", ex.getMessage());
        }
    }
}
