package pe.edu.upeu.sysventas.reporte;

import javafx.geometry.VPos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.stage.Window;

import java.util.List;

/**
 * Dibuja las páginas del reporte con nodos JavaFX (vista previa en pantalla e impresión).
 * Usa exactamente los mismos trazos que el PDF, así la vista previa coincide con el archivo.
 */
public final class VistaReporte {

    private VistaReporte() {
    }

    /** Arial tiene las mismas medidas que Helvetica (la letra del PDF). */
    private static final String FAMILIA = Font.getFamilies().contains("Arial") ? "Arial"
            : Font.getFamilies().contains("Liberation Sans") ? "Liberation Sans" : Font.getDefault().getFamily();

    /** Crea una hoja blanca del tamaño real de la página (1 punto = 1 píxel). */
    public static Pane hoja(Pagina p) {
        Pane hoja = new Pane();
        hoja.setPrefSize(p.ancho(), p.alto());
        hoja.setMinSize(p.ancho(), p.alto());
        hoja.setMaxSize(p.ancho(), p.alto());
        hoja.setStyle("-fx-background-color: white;");

        for (Pagina.Trazo t : p.trazos()) {
            switch (t) {
                case Pagina.Rect r -> {
                    Rectangle rect = new Rectangle(r.x(), r.y(), r.w(), r.h());
                    rect.setFill(r.relleno() >= 0 ? color(r.relleno()) : Color.TRANSPARENT);
                    if (r.borde() >= 0) {
                        rect.setStroke(color(r.borde()));
                        rect.setStrokeWidth(0.6);
                    }
                    hoja.getChildren().add(rect);
                }
                case Pagina.Linea l -> {
                    Line linea = new Line(l.x1(), l.y1(), l.x2(), l.y2());
                    linea.setStroke(color(l.color()));
                    linea.setStrokeWidth(l.grosor());
                    hoja.getChildren().add(linea);
                }
                case Pagina.Texto tx -> {
                    Text texto = new Text(tx.texto());
                    texto.setFont(Font.font(FAMILIA, tx.negrita() ? FontWeight.BOLD : FontWeight.NORMAL, tx.tam()));
                    texto.setFill(color(tx.color()));
                    texto.setTextOrigin(VPos.BASELINE);
                    double ancho = texto.getLayoutBounds().getWidth();
                    double x = switch (tx.alin()) {
                        case IZQUIERDA -> tx.x();
                        case CENTRO -> tx.x() - ancho / 2;
                        case DERECHA -> tx.x() - ancho;
                    };
                    texto.setX(x);
                    texto.setY(tx.y());
                    hoja.getChildren().add(texto);
                }
            }
        }
        return hoja;
    }

    /**
     * Envía todas las páginas a la impresora (sale el diálogo del sistema; ahí también
     * se puede elegir "Microsoft Print to PDF"). Devuelve true si se imprimió.
     */
    public static boolean imprimir(List<Pagina> paginas, boolean horizontal, Window owner) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) throw new IllegalStateException("No se encontró ninguna impresora instalada.");
        if (!job.showPrintDialog(owner)) return false;   // el usuario canceló

        Printer impresora = job.getPrinter();
        PageLayout layout = impresora.createPageLayout(Paper.A4,
                horizontal ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT, Printer.MarginType.HARDWARE_MINIMUM);
        job.getJobSettings().setPageLayout(layout);

        for (Pagina p : paginas) {
            Pane hoja = hoja(p);
            double escala = Math.min(layout.getPrintableWidth() / p.ancho(), layout.getPrintableHeight() / p.alto());
            hoja.getTransforms().add(new Scale(escala, escala));
            if (!job.printPage(layout, new Group(hoja))) {
                job.cancelJob();
                return false;
            }
        }
        return job.endJob();
    }

    private static Color color(int rgb) {
        return Color.rgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }
}
