package pe.edu.upeu.sysventas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.edu.upeu.sysventas.model.DocumentoComercial;
import pe.edu.upeu.sysventas.model.Producto;
import pe.edu.upeu.sysventas.utils.PasswordUtil;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Genera datos de demostración realistas para el Dashboard y los Reportes:
 * catálogo de prendas, clientes, proveedores, usuarios y ~6 meses de ventas y compras.
 *
 * <p>La tienda se SIMULA día a día:
 * <ul>
 *   <li>Cada prenda empieza con un stock inicial.</li>
 *   <li>Las compras a proveedores reponen las prendas que se están agotando (suben el stock).</li>
 *   <li>Las ventas solo se hacen si hay stock (bajan el stock).</li>
 *   <li>Al final se guarda el stock resultante, así el Kardex cuadra con la pantalla de Prendas.</li>
 * </ul>
 *
 * <p>Reglas de seguridad:
 * <ul>
 *   <li>Solo actúa si {@code db.demo.data=true} en application.properties.</li>
 *   <li>La primera vez solo actúa si la tabla Venta está VACÍA (nunca mezcla con ventas reales).</li>
 *   <li>Si la base fue creada por este generador (tabla DemoControl), en cada arranque completa
 *       los días que faltan hasta hoy, así "Ventas de hoy" siempre tiene movimiento.</li>
 *   <li>Todo va en UNA transacción: si algo falla, no queda nada a medias.</li>
 * </ul>
 */
public final class DemoDataSeeder {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final int DIAS = 180;
    private static final String CLAVE_DEMO = "123456";

    private DemoDataSeeder() {
    }

    // =====================================================================
    // Datos del catálogo de ejemplo
    // =====================================================================

    private static final String[] CATEGORIAS = {
            "Casacas y Abrigos", "Polos y Camisetas", "Pantalones y Jeans",
            "Vestidos y Faldas", "Chompas y Polerones", "Ropa Deportiva", "Accesorios"
    };

    /** nombre, costo, % utilidad, categoría, unidad, popularidad (1 = poco, 5 = mucho) */
    private static final Object[][] PRENDAS = {
            {"Casaca de Cuero Sintético", 120.0, 30.0, "Casacas y Abrigos", "Unidad (Prenda)", 2},
            {"Abrigo de Paño Largo", 150.0, 28.0, "Casacas y Abrigos", "Unidad (Prenda)", 1},
            {"Blusa Seda Estampada", 48.0, 30.0, "Polos y Camisetas", "Unidad (Prenda)", 3},
            {"Polo Manga Larga Rayas", 38.0, 30.0, "Polos y Camisetas", "Unidad (Prenda)", 3},
            {"Jean Skinny Negro", 70.0, 25.0, "Pantalones y Jeans", "Unidad (Prenda)", 4},
            {"Short Denim Desflecado", 40.0, 30.0, "Pantalones y Jeans", "Unidad (Prenda)", 2},
            {"Vestido Floral Midi", 80.0, 35.0, "Vestidos y Faldas", "Unidad (Prenda)", 3},
            {"Falda Plisada Satinada", 45.0, 30.0, "Vestidos y Faldas", "Unidad (Prenda)", 2},
            {"Vestido Casual de Algodón", 60.0, 30.0, "Vestidos y Faldas", "Unidad (Prenda)", 2},
            {"Chompa de Alpaca Andina", 95.0, 35.0, "Chompas y Polerones", "Unidad (Prenda)", 4},
            {"Polerón Hoodie Básico", 55.0, 30.0, "Chompas y Polerones", "Unidad (Prenda)", 5},
            {"Cárdigan Tejido Largo", 70.0, 30.0, "Chompas y Polerones", "Unidad (Prenda)", 2},
            {"Conjunto Deportivo Mujer", 85.0, 30.0, "Ropa Deportiva", "Unidad (Prenda)", 2},
            {"Legging Deportivo", 38.0, 35.0, "Ropa Deportiva", "Unidad (Prenda)", 3},
            {"Jogger de Algodón", 45.0, 30.0, "Ropa Deportiva", "Unidad (Prenda)", 4},
            {"Gorro de Lana de Alpaca", 18.0, 50.0, "Accesorios", "Unidad (Prenda)", 4},
            {"Bufanda Tejida", 22.0, 45.0, "Accesorios", "Unidad (Prenda)", 3},
            {"Correa de Cuero", 25.0, 40.0, "Accesorios", "Unidad (Prenda)", 2},
            {"Medias Algodón Pack x3", 15.0, 40.0, "Accesorios", "Pack x3 (Conjunto)", 3},
            {"Polo Básico por Docena", 300.0, 15.0, "Polos y Camisetas", "Docena (Mayorista)", 1}
    };

    /** dni, nombres, dirección, celular */
    private static final String[][] CLIENTES_DNI = {
            {"71234589", "Lucía Paredes Huamán", "Jr. Moquegua 315 - Juliaca", "951234001"},
            {"70456123", "Diego Ramírez Quispe", "Av. Circunvalación 1120 - Juliaca", "951234002"},
            {"72981456", "Valeria Choque Apaza", "Jr. Tumbes 88 - Juliaca", "951234003"},
            {"46781230", "Jorge Luis Calla Mamani", "Jr. Ramón Castilla 540 - Juliaca", "951234004"},
            {"73560981", "Camila Flores Ticona", "Av. Huancané 760 - Juliaca", "951234005"},
            {"45678912", "Renato Vargas Cruz", "Jr. San Román 233 - Juliaca", "951234006"},
            {"74125896", "Andrea Condori Pari", "Jr. Lambayeque 410 - Juliaca", "951234007"},
            {"70985432", "Sebastián Huanca Ríos", "Av. Independencia 905 - Juliaca", "951234008"},
            {"75321468", "Ximena Quispe Larico", "Jr. Cusco 172 - Juliaca", "951234009"},
            {"47852369", "Fernando Apaza Coaquira", "Av. Mártires del 4 de Noviembre 330 - Juliaca", "951234010"},
            {"76543210", "Daniela Ccori Machaca", "Jr. Piérola 615 - Juliaca", "951234011"},
            {"44521789", "Raúl Mendoza Ticona", "Jr. 2 de Mayo 220 - Juliaca", "951234012"},
            {"73698521", "Milagros Huarachi Velasco", "Av. Ferrocarril 410 - Juliaca", "951234013"},
            {"71478523", "Kevin Pari Chambi", "Jr. Loreto 95 - Juliaca", "951234014"},
            {"72589631", "Rocío Cutipa Flores", "Urb. La Capilla Mz. C Lt. 8 - Juliaca", "951234015"},
            {"48963257", "Óscar Mamani Ayala", "Jr. Apurímac 540 - Juliaca", "951234016"},
            {"70123987", "Gabriela Torres Luque", "Av. Tacna 1015 - Juliaca", "951234017"},
            {"74852136", "Miguel Ángel Quisocala", "Jr. Huáscar 318 - Juliaca", "951234018"},
            {"75963124", "Nataly Yucra Choquehuanca", "Jr. Benigno Ballón 77 - Juliaca", "951234019"},
            {"43218765", "Elena Callo Sucasaca", "Av. Simón Bolívar 1450 - Puno", "951234020"},
            {"72014785", "Brayan Coila Apaza", "Jr. Ayacucho 260 - Juliaca", "951234021"},
            {"76321458", "Fiorella Ramos Ccama", "Urb. Santa Adriana Mz. F Lt. 3 - Juliaca", "951234022"}
    };

    /** ruc, razón social, representante legal, dirección, celular */
    private static final String[][] CLIENTES_RUC = {
            {"20601478523", "Boutique Altiplano E.I.R.L.", "Silvia Pari Mamani", "Jr. Moquegua 410 - Juliaca", "951300001"},
            {"20448521369", "Uniformes Escolares del Sur S.A.C.", "Héctor Calla Quispe", "Av. Huancané 1200 - Juliaca", "951300002"},
            {"10456789123", "Quispe Larico Martha (Bazar Martita)", "Martha Quispe Larico", "Mercado Túpac Amaru Pto. 45 - Juliaca", "951300003"},
            {"20609876541", "Corporación Textil Titicaca S.A.C.", "Julio Coaquira Ramos", "Av. El Sol 820 - Puno", "951300004"}
    };

    /** ruc, razón social, contacto, dirección, celular, email */
    private static final String[][] PROVEEDORES = {
            {"20554433221", "Alpaca Collection Perú S.A.C.", "Mariela Choque", "Av. Ejército 1020 - Arequipa", "959000333", "ventas@alpacacollection.pe"},
            {"20498765432", "Confecciones Deportivas Andes E.I.R.L.", "Wilber Huanca", "Jr. Los Incas 340 - Juliaca", "959000444", "pedidos@deportivasandes.pe"},
            {"20611223344", "Importaciones Fashion Lima S.A.C.", "Karina Salas", "Jr. Gamarra 778 - La Victoria, Lima", "959000555", "contacto@fashionlima.pe"}
    };

    /** usuario, perfil */
    private static final String[][] USUARIOS = {
            {"Luis Mamani (Almacén)", "Encargado de Almacén"},
            {"Rosa Ccama (Cajera Turno T)", "Vendedor de Tienda / Cajero"}
    };

    // =====================================================================
    // Punto de entrada
    // =====================================================================

    /** Se llama al iniciar la app (SysVentas.start). */
    public static void cargarSiEstaVacia() {
        if (!Boolean.parseBoolean(DatabaseConfig.propiedad("db.demo.data", "false"))) return;

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                crearTablaControl(conn);
                LocalDate hoy = LocalDate.now();
                String ultimo = leerControl(conn, "ultimoDia");
                LocalDate desde;
                boolean inicial = ultimo == null;
                if (inicial) {
                    if (contar(conn, "SELECT COUNT(*) FROM Venta") > 0) {   // ya hay ventas reales: no se toca
                        conn.rollback();
                        return;
                    }
                    agregarCatalogo(conn);
                    desde = hoy.minusDays(DIAS - 1);
                } else {
                    desde = LocalDate.parse(ultimo).plusDays(1);
                    if (desde.isAfter(hoy)) {     // ya está al día
                        conn.rollback();
                        return;
                    }
                    if (desde.isBefore(hoy.minusDays(DIAS - 1))) desde = hoy.minusDays(DIAS - 1);
                }

                Simulacion sim = new Simulacion(
                        new Random(inicial ? 2026 : hoy.toEpochDay()),   // semilla fija: siempre los mismos datos
                        prendas(conn, inicial),
                        ids(conn, "SELECT idCliente FROM Cliente ORDER BY idCliente"),
                        rucs(conn),
                        ids(conn, "SELECT idProveedor FROM proveedor ORDER BY idProveedor"),
                        usuariosPorPerfil(conn, "Vendedor de Tienda / Cajero"),
                        usuariosPorPerfil(conn, "Administrador de Tienda"),
                        usuariosPorPerfil(conn, "Encargado de Almacén"),
                        new DestinoBD(conn), inicial);
                if (!sim.valida()) {
                    conn.rollback();
                    return;
                }
                sim.simular(desde, hoy);
                sim.guardarStock(conn);
                guardarControl(conn, "ultimoDia", hoy.toString());
                conn.commit();
                LOG.info("Datos de demostración: {} ventas y {} compras generadas ({} a {})",
                        sim.ventas, sim.compras, desde, hoy);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            LOG.warn("No se pudieron generar los datos de demostración: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    // =====================================================================
    // Simulación de la tienda (independiente de la base de datos)
    // =====================================================================

    /** Prenda durante la simulación (el stock va cambiando). */
    static final class Prenda {
        final long id;
        final double costo;
        final double util;
        final int popularidad;
        final boolean mayorista;
        int stock;
        /** Si es true, ya no se repone en el último tramo (para que haya prendas con stock bajo / agotadas). */
        int sinReponerDesdeDia = Integer.MAX_VALUE;

        Prenda(long id, double costo, double util, int popularidad, boolean mayorista, int stock) {
            this.id = id;
            this.costo = costo;
            this.util = util;
            this.popularidad = popularidad;
            this.mayorista = mayorista;
            this.stock = stock;
        }
    }

    /** Dónde se guardan las ventas y compras simuladas (la BD, o una lista en pruebas). */
    interface Destino {
        long venta(LocalDate fecha, double neto, double igv, double total, long idCliente, long idUsuario) throws SQLException;

        void detalleVenta(long idVenta, Prenda p, int cantidad, double importe) throws SQLException;

        long compra(LocalDate fecha, String tipoDoc, String serie, String numDoc, double neto, double igv, double total,
                    long idProveedor, long idUsuario) throws SQLException;

        void detalleCompra(long idCompra, Prenda p, int cantidad, double precio, double subtotal) throws SQLException;

        default void terminar() throws SQLException { }
    }

    static final class Simulacion {
        private static final int PUNTO_REPOSICION = 14;   // si el stock baja de esto, se compra
        private final Random rnd;
        private final List<Prenda> prendas;
        private final List<Long> clientes;
        private final Set<Long> clientesRuc;
        private final List<Long> proveedores;
        private final List<Long> cajeros;
        private final List<Long> admins;
        private final List<Long> almacen;
        private final Destino destino;
        private final boolean inicial;
        private final Map<Long, Integer> correlativos = new HashMap<>();
        int ventas;
        int compras;

        Simulacion(Random rnd, List<Prenda> prendas, List<Long> clientes, Set<Long> clientesRuc, List<Long> proveedores,
                   List<Long> cajeros, List<Long> admins, List<Long> almacen, Destino destino, boolean inicial) {
            this.rnd = rnd;
            this.prendas = prendas;
            this.clientes = clientes;
            this.clientesRuc = clientesRuc;
            this.proveedores = proveedores;
            this.cajeros = cajeros;
            this.admins = admins;
            this.almacen = almacen;
            this.destino = destino;
            this.inicial = inicial;
        }

        boolean valida() {
            return !prendas.isEmpty() && !clientes.isEmpty() && !proveedores.isEmpty()
                    && (!cajeros.isEmpty() || !admins.isEmpty());
        }

        void simular(LocalDate desde, LocalDate hasta) throws SQLException {
            int totalDias = (int) (hasta.toEpochDay() - desde.toEpochDay()) + 1;
            if (inicial) marcarPrendasSinReponer(totalDias);
            int proximaCompra = 0;
            for (int d = 0; d < totalDias; d++) {
                LocalDate dia = desde.plusDays(d);
                // Compras: una cada 5-8 días, o antes si varias prendas se están agotando
                final int hoyDia = d;
                long agotandose = prendas.stream()
                        .filter(p -> p.stock <= PUNTO_REPOSICION && hoyDia < p.sinReponerDesdeDia).count();
                if (d >= proximaCompra || agotandose >= 4) {
                    if (comprar(dia, d)) proximaCompra = d + 5 + rnd.nextInt(4);
                }
                vender(dia, d, totalDias, inicial ? DIAS - totalDias + d : DIAS - 1);
            }
            destino.terminar();
        }

        /** Para que el inventario se vea real: unas prendas quedan con stock bajo y una se agota. */
        private void marcarPrendasSinReponer(int totalDias) {
            List<Prenda> populares = new ArrayList<>(prendas);
            populares.removeIf(p -> p.mayorista);
            populares.sort((a, b) -> b.popularidad - a.popularidad);
            if (populares.size() < 6) return;
            populares.get(1).sinReponerDesdeDia = totalDias - 80;   // se agota
            populares.get(3).sinReponerDesdeDia = totalDias - 25;   // stock bajo
            populares.get(5).sinReponerDesdeDia = totalDias - 20;
            populares.get(populares.size() - 1).sinReponerDesdeDia = totalDias - 30;
        }

        private boolean comprar(LocalDate dia, int d) throws SQLException {
            List<Prenda> porReponer = new ArrayList<>();
            for (Prenda p : prendas) {
                if (d >= p.sinReponerDesdeDia) continue;
                if (p.stock <= PUNTO_REPOSICION + (p.popularidad >= 4 ? 8 : 0)) porReponer.add(p);
            }
            if (porReponer.isEmpty()) {
                // compra pequeña de temporada
                Prenda p = prendas.get(rnd.nextInt(prendas.size()));
                if (d >= p.sinReponerDesdeDia) return false;
                porReponer.add(p);
            }
            porReponer.sort((a, b) -> a.stock - b.stock);
            if (porReponer.size() > 6) porReponer = porReponer.subList(0, 6);

            long idProv = proveedores.get(rnd.nextInt(proveedores.size()));
            boolean boleta = rnd.nextDouble() < 0.12;
            int numero = correlativos.merge(idProv, 1, Integer::sum) + (int) (idProv * 1000);
            double neto = 0;
            List<Object[]> lineas = new ArrayList<>();
            for (Prenda p : porReponer) {
                int cant = p.mayorista ? 2 + rnd.nextInt(3) : 6 * (4 + rnd.nextInt(5)) + (p.popularidad >= 4 ? 12 : 0);
                double precio = DocumentoComercial.redondear(p.costo);
                double sub = DocumentoComercial.redondear(precio * cant);
                neto += sub;
                lineas.add(new Object[]{p, cant, precio, sub});
            }
            neto = DocumentoComercial.redondear(neto);
            double igv = DocumentoComercial.redondear(neto * DocumentoComercial.TASA_IGV);
            long idUsuario = !almacen.isEmpty() && rnd.nextDouble() < 0.8 ? elegir(almacen) : elegir(admins.isEmpty() ? cajeros : admins);
            long idCompra = destino.compra(dia, boleta ? "BOLETA" : "FACTURA", boleta ? "B002" : "F00" + (1 + idProv % 3),
                    String.valueOf(numero), neto, igv, DocumentoComercial.redondear(neto + igv), idProv, idUsuario);
            for (Object[] l : lineas) {
                Prenda p = (Prenda) l[0];
                int cant = (Integer) l[1];
                destino.detalleCompra(idCompra, p, cant, (Double) l[2], (Double) l[3]);
                p.stock += cant;
            }
            compras++;
            return true;
        }

        private void vender(LocalDate dia, int d, int totalDias, int diaDelSemestre) throws SQLException {
            // El negocio crece con el tiempo y vende más los fines de semana y en diciembre / julio
            double tendencia = 0.7 + 0.6 * Math.max(0, diaDelSemestre) / (double) DIAS;
            DayOfWeek dow = dia.getDayOfWeek();
            double finde = dow == DayOfWeek.SATURDAY ? 1.7 : dow == DayOfWeek.SUNDAY ? 1.4
                    : dow == DayOfWeek.FRIDAY ? 1.25 : 1.0;
            double temporada = (dia.getMonthValue() == 12 || dia.getMonthValue() == 7) ? 1.3 : 1.0;
            int n = (int) Math.round(2.4 * tendencia * finde * temporada + rnd.nextGaussian() * 0.9);
            if (d == totalDias - 1) n = Math.max(n, 5);          // que "hoy" tenga movimiento
            n = Math.max(0, n);

            for (int k = 0; k < n; k++) {
                long idCliente = elegirCliente();
                boolean empresa = clientesRuc.contains(idCliente);
                int numLineas = empresa ? 2 + rnd.nextInt(3)
                        : 1 + (rnd.nextDouble() < 0.45 ? 1 : 0) + (rnd.nextDouble() < 0.18 ? 1 : 0);
                Map<Prenda, Integer> items = new java.util.LinkedHashMap<>();
                for (int l = 0; l < numLineas; l++) {
                    Prenda p = elegirPrenda(empresa);
                    if (p == null) break;
                    int disponible = p.stock - items.getOrDefault(p, 0);
                    int cant = p.mayorista ? 1
                            : empresa ? 3 + rnd.nextInt(6)
                            : 1 + (rnd.nextDouble() < 0.3 ? 1 : 0) + (rnd.nextDouble() < 0.08 ? 1 : 0);
                    cant = Math.min(cant, disponible);
                    if (cant <= 0) continue;
                    items.merge(p, cant, Integer::sum);
                }
                if (items.isEmpty()) continue;

                double neto = 0;
                for (Map.Entry<Prenda, Integer> e : items.entrySet()) {
                    neto += DocumentoComercial.redondear(precioVenta(e.getKey()) * e.getValue());
                }
                neto = DocumentoComercial.redondear(neto);
                double igv = DocumentoComercial.redondear(neto * DocumentoComercial.TASA_IGV);
                long idUsuario = !cajeros.isEmpty() && rnd.nextDouble() < 0.78 ? elegir(cajeros) : elegir(admins.isEmpty() ? cajeros : admins);
                long idVenta = destino.venta(dia, neto, igv, DocumentoComercial.redondear(neto + igv), idCliente, idUsuario);
                for (Map.Entry<Prenda, Integer> e : items.entrySet()) {
                    Prenda p = e.getKey();
                    int cant = e.getValue();
                    destino.detalleVenta(idVenta, p, cant, DocumentoComercial.redondear(precioVenta(p) * cant));
                    p.stock -= cant;
                }
                ventas++;
            }
        }

        private static double precioVenta(Prenda p) {
            return Producto.calcularPrecioVenta(p.costo, p.util);
        }

        /** Los primeros clientes son "frecuentes" y compran más seguido. */
        private long elegirCliente() {
            int i = rnd.nextDouble() < 0.45 ? rnd.nextInt(Math.min(8, clientes.size())) : rnd.nextInt(clientes.size());
            return clientes.get(i);
        }

        /** Elige una prenda con stock, más probable si es popular. */
        private Prenda elegirPrenda(boolean empresa) {
            double total = 0;
            for (Prenda p : prendas) if (p.stock > 0 && (empresa || !p.mayorista)) total += peso(p, empresa);
            if (total <= 0) return null;
            double r = rnd.nextDouble() * total;
            for (Prenda p : prendas) {
                if (p.stock <= 0 || (!empresa && p.mayorista)) continue;
                r -= peso(p, empresa);
                if (r <= 0) return p;
            }
            return null;
        }

        private static double peso(Prenda p, boolean empresa) {
            return p.mayorista ? (empresa ? 6 : 0) : p.popularidad * p.popularidad;
        }

        private long elegir(List<Long> lista) {
            return lista.get(rnd.nextInt(lista.size()));
        }

        void guardarStock(Connection conn) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE Productos SET stock = ? WHERE idProducto = ?")) {
                for (Prenda p : prendas) {
                    ps.setInt(1, Math.max(0, p.stock));
                    ps.setLong(2, p.id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    /** Guarda las ventas y compras simuladas en la base de datos. */
    private static final class DestinoBD implements Destino {
        private final PreparedStatement venta;
        private final PreparedStatement detVenta;
        private final PreparedStatement compra;
        private final PreparedStatement detCompra;

        DestinoBD(Connection conn) throws SQLException {
            venta = conn.prepareStatement("INSERT INTO Venta(fechaVenta,netoTotal,igv,precioTotal,idCliente,idUsuario) "
                    + "VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            detVenta = conn.prepareStatement("INSERT INTO VentaDetalle(precioUnit,porceUtil,cantidad,netoTotal,idVenta,idProducto) "
                    + "VALUES(?,?,?,?,?,?)");
            compra = conn.prepareStatement("INSERT INTO compras(fechaCompra,tipoDoc,serie,numDoc,netoTotal,igv,precioTotal,"
                    + "idProveedor,idUsuario) VALUES(?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            detCompra = conn.prepareStatement("INSERT INTO detalle_compra(precioCompra,cantidad,subtotal,idCompra,idProducto) "
                    + "VALUES(?,?,?,?,?)");
        }

        @Override
        public long venta(LocalDate fecha, double neto, double igv, double total, long idCliente, long idUsuario) throws SQLException {
            venta.setDate(1, Date.valueOf(fecha));
            venta.setDouble(2, neto);
            venta.setDouble(3, igv);
            venta.setDouble(4, total);
            venta.setLong(5, idCliente);
            venta.setLong(6, idUsuario);
            venta.executeUpdate();
            return clave(venta);
        }

        @Override
        public void detalleVenta(long idVenta, Prenda p, int cantidad, double importe) throws SQLException {
            detVenta.setDouble(1, p.costo);
            detVenta.setDouble(2, p.util);
            detVenta.setInt(3, cantidad);
            detVenta.setDouble(4, importe);
            detVenta.setLong(5, idVenta);
            detVenta.setLong(6, p.id);
            detVenta.executeUpdate();
        }

        @Override
        public long compra(LocalDate fecha, String tipoDoc, String serie, String numDoc, double neto, double igv,
                           double total, long idProveedor, long idUsuario) throws SQLException {
            compra.setDate(1, Date.valueOf(fecha));
            compra.setString(2, tipoDoc);
            compra.setString(3, serie);
            compra.setString(4, numDoc);
            compra.setDouble(5, neto);
            compra.setDouble(6, igv);
            compra.setDouble(7, total);
            compra.setLong(8, idProveedor);
            compra.setLong(9, idUsuario);
            compra.executeUpdate();
            return clave(compra);
        }

        @Override
        public void detalleCompra(long idCompra, Prenda p, int cantidad, double precio, double subtotal) throws SQLException {
            detCompra.setDouble(1, precio);
            detCompra.setInt(2, cantidad);
            detCompra.setDouble(3, subtotal);
            detCompra.setLong(4, idCompra);
            detCompra.setLong(5, p.id);
            detCompra.executeUpdate();
        }

        @Override
        public void terminar() throws SQLException {
            venta.close();
            detVenta.close();
            compra.close();
            detCompra.close();
        }

        private static long clave(PreparedStatement ps) throws SQLException {
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    // =====================================================================
    // Catálogo, clientes, proveedores y usuarios de ejemplo
    // =====================================================================

    private static void agregarCatalogo(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO Categoria(nombre) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM Categoria WHERE nombre=?)")) {
            for (String c : CATEGORIAS) {
                ps.setString(1, c);
                ps.setString(2, c);
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO Productos(nombre,preUnitario,porceUtil,stock,idUnidadMed,idCateg) "
                        + "SELECT ?,?,?,0,(SELECT MIN(idUnidadMed) FROM UnidadMedida WHERE nombre=?),"
                        + "(SELECT MIN(idCateg) FROM Categoria WHERE nombre=?) "
                        + "WHERE NOT EXISTS (SELECT 1 FROM Productos WHERE nombre=?) "
                        + "AND EXISTS (SELECT 1 FROM UnidadMedida WHERE nombre=?)")) {
            for (Object[] p : PRENDAS) {
                ps.setString(1, (String) p[0]);
                ps.setDouble(2, (Double) p[1]);
                ps.setDouble(3, (Double) p[2]);
                ps.setString(4, (String) p[4]);
                ps.setString(5, (String) p[3]);
                ps.setString(6, (String) p[0]);
                ps.setString(7, (String) p[4]);
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO Cliente(dniruc,nombres,tipoDocumento,direccion,celular) "
                        + "SELECT ?,?,'DNI',?,? WHERE NOT EXISTS (SELECT 1 FROM Cliente WHERE dniruc=?)")) {
            for (String[] c : CLIENTES_DNI) {
                ps.setString(1, c[0]);
                ps.setString(2, c[1]);
                ps.setString(3, c[2]);
                ps.setString(4, c[3]);
                ps.setString(5, c[0]);
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO Cliente(dniruc,nombres,tipoDocumento,repLegal,direccion,celular) "
                        + "SELECT ?,?,'RUC',?,?,? WHERE NOT EXISTS (SELECT 1 FROM Cliente WHERE dniruc=?)")) {
            for (String[] c : CLIENTES_RUC) {
                ps.setString(1, c[0]);
                ps.setString(2, c[1]);
                ps.setString(3, c[2]);
                ps.setString(4, c[3]);
                ps.setString(5, c[4]);
                ps.setString(6, c[0]);
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO proveedor(ruc,razonSocial,contacto,direccion,celular,email) "
                        + "SELECT ?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM proveedor WHERE ruc=?)")) {
            for (String[] p : PROVEEDORES) {
                for (int i = 0; i < 6; i++) ps.setString(i + 1, p[i]);
                ps.setString(7, p[0]);
                ps.executeUpdate();
            }
        }
        // Usuarios: la clave se guarda ya encriptada (SHA-256 + sal), igual que los demás
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO Usuario(usuario,clave,idPerfil) SELECT ?,?,(SELECT MIN(idPerfil) FROM Perfil WHERE nombre=?) "
                        + "WHERE NOT EXISTS (SELECT 1 FROM Usuario WHERE usuario=?) "
                        + "AND EXISTS (SELECT 1 FROM Perfil WHERE nombre=?)")) {
            for (String[] u : USUARIOS) {
                ps.setString(1, u[0]);
                ps.setString(2, PasswordUtil.encriptar(CLAVE_DEMO));
                ps.setString(3, u[1]);
                ps.setString(4, u[0]);
                ps.setString(5, u[1]);
                ps.executeUpdate();
            }
        }
    }

    // =====================================================================
    // Apoyo
    // =====================================================================

    /** Prendas de la base. En la carga inicial cada una empieza con un stock aleatorio. */
    private static List<Prenda> prendas(Connection conn, boolean inicial) throws SQLException {
        Map<String, Integer> popularidad = new HashMap<>();
        for (Object[] p : PRENDAS) popularidad.put((String) p[0], (Integer) p[5]);
        Random rnd = new Random(7);
        List<Prenda> lista = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT p.idProducto, p.nombre, p.preUnitario, p.porceUtil, p.stock, u.nombre "
                     + "FROM Productos p JOIN UnidadMedida u ON p.idUnidadMed = u.idUnidadMed ORDER BY p.idProducto")) {
            while (rs.next()) {
                boolean mayorista = rs.getString(6).toLowerCase().contains("docena");
                // las prendas originales del caso (sin popularidad definida) se venden bastante
                int pop = popularidad.getOrDefault(rs.getString(2), 3 + (int) (rs.getLong(1) % 3));
                int stock = inicial ? (mayorista ? 6 : 25 + rnd.nextInt(26)) : rs.getInt(5);
                lista.add(new Prenda(rs.getLong(1), rs.getDouble(3), rs.getDouble(4), pop, mayorista, stock));
            }
        }
        return lista;
    }

    private static List<Long> usuariosPorPerfil(Connection conn, String perfil) throws SQLException {
        List<Long> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT u.idUsuario FROM Usuario u JOIN Perfil p "
                + "ON u.idPerfil = p.idPerfil WHERE p.nombre = ? ORDER BY u.idUsuario")) {
            ps.setString(1, perfil);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(rs.getLong(1));
            }
        }
        return lista;
    }

    private static Set<Long> rucs(Connection conn) throws SQLException {
        return new HashSet<>(ids(conn, "SELECT idCliente FROM Cliente WHERE tipoDocumento = 'RUC'"));
    }

    private static void crearTablaControl(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS DemoControl (clave VARCHAR(30) PRIMARY KEY, valor VARCHAR(60))");
        }
    }

    private static String leerControl(Connection conn, String clave) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT valor FROM DemoControl WHERE clave = ?")) {
            ps.setString(1, clave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void guardarControl(Connection conn, String clave, String valor) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM DemoControl WHERE clave = ?");
             PreparedStatement ins = conn.prepareStatement("INSERT INTO DemoControl(clave, valor) VALUES(?, ?)")) {
            del.setString(1, clave);
            del.executeUpdate();
            ins.setString(1, clave);
            ins.setString(2, valor);
            ins.executeUpdate();
        }
    }

    private static List<Long> ids(Connection conn, String sql) throws SQLException {
        List<Long> lista = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) lista.add(rs.getLong(1));
        }
        return lista;
    }

    private static int contar(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
