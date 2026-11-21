package pe.edu.upeu.sysventas.controller;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import pe.edu.upeu.sysventas.components.Iconos;
import pe.edu.upeu.sysventas.components.Mensaje;
import pe.edu.upeu.sysventas.components.StageManager;
import pe.edu.upeu.sysventas.components.Tema;
import pe.edu.upeu.sysventas.config.AppContext;
import pe.edu.upeu.sysventas.dto.MenuMenuItenTO;
import pe.edu.upeu.sysventas.dto.SessionManager;
import pe.edu.upeu.sysventas.service.IMenuMenuItemDao;
import pe.edu.upeu.sysventas.utils.UtilsX;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.prefs.Preferences;

/** Ventana principal: menú lateral con íconos (según perfil) + barra superior + área de contenido. */
@RequiredArgsConstructor
public class MainguiController {

    private static final String FXML_DASHBOARD = "/view/main_dashboard.fxml";

    private final IMenuMenuItemDao mmiDao;

    @FXML BorderPane bp;
    @FXML VBox sidebar, menuBox, marcaTextos, ajustesBox;
    @FXML HBox marcaBox, fechaPill;
    @FXML StackPane contenido, logo;
    @FXML ScrollPane menuScroll;
    @FXML Button btnInicio, btnSalir, btnColapsar;
    @FXML ComboBox<String> cbxTema, cbxIdioma;
    @FXML Label lblApp, lblAppSub, lblTema, lblIdioma, lblRuta, lblTitulo, lblFecha, lblReloj,
            lblInicial, lblUsuario, lblPerfil;

    private final Preferences userPrefs = Preferences.userRoot().node("sysventaropa");
    private final UtilsX util = new UtilsX();
    private Properties idioma = new Properties();
    private Button botonActivo;
    private String fxmlActual;
    private boolean colapsado;
    private Timeline reloj;
    /** Opciones del menú que puede ver el perfil (id -> opción), para los accesos rápidos. */
    private final Map<String, MenuMenuItenTO> opciones = new LinkedHashMap<>();
    private final Map<String, Button> botones = new LinkedHashMap<>();

    @FXML
    public void initialize() {
        SessionManager s = SessionManager.getInstance();
        lblUsuario.setText(s.getUserName());
        lblPerfil.setText(s.getUserPerfil());
        lblInicial.setText(s.getUserName().substring(0, 1).toUpperCase());

        logo.getChildren().setAll(Iconos.crear("percha", 24));
        btnInicio.setGraphic(Iconos.crear("inicio", 18));
        btnSalir.setGraphic(Iconos.crear("salir", 18));
        btnColapsar.setGraphic(Iconos.crear("menu", 20));
        btnColapsar.setTooltip(new Tooltip("Menú"));
        fechaPill.getChildren().add(0, Iconos.crear("calendario", 14));

        cbxTema.setItems(FXCollections.observableArrayList(Tema.TEMAS));
        cbxTema.getSelectionModel().select(Tema.actual());
        cbxTema.setOnAction(e -> cambiarTema());

        cbxIdioma.setItems(FXCollections.observableArrayList("Español", "English", "Français"));
        String cod = userPrefs.get("IDIOMAX", "es");
        cbxIdioma.getSelectionModel().select(cod.equals("en") ? 1 : cod.equals("fr") ? 2 : 0);
        cbxIdioma.setOnAction(e -> cambiarIdioma());

        colapsado = false;
        fxmlActual = null;
        iniciarReloj();
        graficarMenus();
        irInicio();
    }

    /** Reloj de la barra superior (se actualiza cada segundo). */
    private void iniciarReloj() {
        if (reloj != null) reloj.stop();
        Locale es = Locale.of("es", "PE");
        DateTimeFormatter fFecha = DateTimeFormatter.ofPattern("EEE d 'de' MMM", es);
        DateTimeFormatter fHora = DateTimeFormatter.ofPattern("HH:mm");
        Runnable tic = () -> {
            String f = LocalDate.now().format(fFecha).replace(".", "");
            lblFecha.setText(f.substring(0, 1).toUpperCase() + f.substring(1));
            lblReloj.setText(LocalTime.now().format(fHora));
        };
        tic.run();
        reloj = new Timeline(new KeyFrame(Duration.seconds(1), e -> tic.run()));
        reloj.setCycleCount(Animation.INDEFINITE);
        reloj.play();
    }

    /** Dibuja las opciones del menú lateral agrupadas por sección, cada una con su ícono. */
    private void graficarMenus() {
        idioma = util.detectLanguage(userPrefs.get("IDIOMAX", "es"));
        lblApp.setText(idioma.getProperty("app.nombre", "SysVentaRopa"));
        lblAppSub.setText(idioma.getProperty("app.sub", "Tienda de ropa"));
        lblTema.setText(idioma.getProperty("label.tema", "Tema").toUpperCase());
        lblIdioma.setText(idioma.getProperty("label.idioma", "Idioma").toUpperCase());
        btnInicio.setText(idioma.getProperty("label.inicio", "Dashboard"));
        btnInicio.setTooltip(new Tooltip(btnInicio.getText()));

        List<MenuMenuItenTO> lista = mmiDao.listaAccesos(SessionManager.getInstance().getUserPerfil(), idioma);
        menuBox.getChildren().clear();
        opciones.clear();
        botones.clear();
        String seccion = null;
        for (MenuMenuItenTO op : lista) {
            if (op.getTipoTab().equals("C")) {          // cerrar sesión va abajo
                btnSalir.setText(op.getMenuitemnombre());
                btnSalir.setTooltip(new Tooltip(op.getMenuitemnombre()));
                continue;
            }
            if (!op.getMenunombre().equals(seccion)) {
                seccion = op.getMenunombre();
                Label lbl = new Label(seccion.toUpperCase());
                lbl.getStyleClass().add("menu-seccion");
                menuBox.getChildren().add(lbl);
            }
            Button b = new Button(op.getMenuitemnombre(), Iconos.crear(op.getIdNombreObj(), 18));
            b.getStyleClass().add("menu-btn");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setTooltip(new Tooltip(op.getMenuitemnombre()));
            b.setOnAction(e -> abrirPantalla(op, b));
            if (op.getRutaFile().equals(fxmlActual)) marcarActivo(b);
            menuBox.getChildren().add(b);
            opciones.put(op.getIdNombreObj(), op);
            botones.put(op.getIdNombreObj(), b);
        }
        if (fxmlActual == null) marcarActivo(btnInicio);
        aplicarColapso();
    }

    private void marcarActivo(Button b) {
        if (botonActivo != null) botonActivo.getStyleClass().remove("activo");
        botonActivo = b;
        if (!b.getStyleClass().contains("activo")) b.getStyleClass().add("activo");
    }

    /** Carga un FXML dentro del área central con una transición suave. */
    private void abrirPantalla(MenuMenuItenTO op, Button boton) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(op.getRutaFile()));
            loader.setControllerFactory(AppContext.getInstance()::getBean);
            Parent root = loader.load();
            mostrar(root, true);
            lblTitulo.setText(op.getNombreTab());
            lblRuta.setText(lblApp.getText() + "  /  " + op.getMenunombre());
            fxmlActual = op.getRutaFile();
            marcarActivo(boton);
        } catch (Exception ex) {
            ex.printStackTrace();
            Mensaje.errorDialogo("Error", "No se pudo abrir la pantalla: " + ex.getMessage());
        }
    }

    private void mostrar(Parent root, boolean ajustarAlto) {
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(ajustarAlto);
        contenido.getChildren().setAll(scroll);
        FadeTransition ft = new FadeTransition(Duration.millis(260), scroll);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    /** Pantalla de inicio: el Dashboard de ventas. */
    @FXML
    public void irInicio() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_DASHBOARD));
            loader.setControllerFactory(AppContext.getInstance()::getBean);
            Parent root = loader.load();
            DashboardController dash = loader.getController();
            dash.mostrarAccesos(accesosRapidos());
            mostrar(root, false);
        } catch (Exception ex) {
            ex.printStackTrace();
            Label error = new Label("No se pudo cargar el dashboard: " + ex.getMessage());
            error.getStyleClass().add("msg-error");
            contenido.getChildren().setAll(error);
        }
        lblTitulo.setText(idioma.getProperty("label.inicio", "Dashboard"));
        lblRuta.setText(lblApp.getText() + "  /  " + idioma.getProperty("label.resumen", "Resumen general"));
        fxmlActual = null;
        marcarActivo(btnInicio);
    }

    /** Accesos rápidos del banner: los primeros 3 que el perfil tenga permitidos. */
    private List<DashboardController.Acceso> accesosRapidos() {
        List<DashboardController.Acceso> lista = new ArrayList<>();
        for (String id : List.of("venta", "compra", "cliente", "producto", "historialventa")) {
            MenuMenuItenTO op = opciones.get(id);
            if (op == null || lista.size() == 3) continue;
            lista.add(new DashboardController.Acceso(id, op.getMenuitemnombre(), () -> abrirPantalla(op, botones.get(id))));
        }
        return lista;
    }

    /** Muestra/oculta los textos del menú lateral (modo compacto solo con íconos). */
    @FXML
    public void alternarMenu() {
        colapsado = !colapsado;
        aplicarColapso();
    }

    private void aplicarColapso() {
        if (colapsado) {
            if (!sidebar.getStyleClass().contains("colapsado")) sidebar.getStyleClass().add("colapsado");
        } else {
            sidebar.getStyleClass().remove("colapsado");
        }
        ContentDisplay modo = colapsado ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT;
        btnInicio.setContentDisplay(modo);
        btnSalir.setContentDisplay(modo);
        for (Node n : menuBox.getChildren()) {
            if (n instanceof Button b) b.setContentDisplay(modo);
            else {
                n.setVisible(!colapsado);
                n.setManaged(!colapsado);
            }
        }
        for (Node n : List.of(marcaTextos, ajustesBox)) {
            n.setVisible(!colapsado);
            n.setManaged(!colapsado);
        }
    }

    private void cambiarTema() {
        Tema.guardar(cbxTema.getValue());
        Tema.aplicar(bp.getScene());
    }

    private void cambiarIdioma() {
        String cod = switch (cbxIdioma.getSelectionModel().getSelectedIndex()) {
            case 1 -> "en";
            case 2 -> "fr";
            default -> "es";
        };
        userPrefs.put("IDIOMAX", cod);
        graficarMenus();
        if (fxmlActual == null) irInicio();
    }

    /** Vuelve a la pantalla de login. */
    @FXML
    public void cerrarSesion() {
        if (!Mensaje.confirmar("Cerrar sesión", "¿Desea cerrar la sesión de " + lblUsuario.getText() + "?")) return;
        try {
            if (reloj != null) reloj.stop();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login.fxml"));
            loader.setControllerFactory(AppContext.getInstance()::getBean);
            Scene scene = new Scene(loader.load());
            Tema.aplicar(scene);
            Stage stage = (Stage) bp.getScene().getWindow();
            stage.setMaximized(false);
            stage.setMinWidth(0);
            stage.setMinHeight(0);
            stage.setResizable(false);
            stage.setScene(scene);
            stage.sizeToScene();
            stage.centerOnScreen();
            StageManager.setPrimaryStage(stage);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
