# SysVentaRopa – Sistema de Gestión de Ventas de Ropa (JavaFX + H2)

Proyecto basado en el caso de estudio "Venta de Ropa". La base de datos sigue el modelo final
`db_modelo/modelo_venta_de_ropa.architect` (11 tablas).

## Caso de estudio
Una tienda de ropa necesita un sistema de escritorio para registrar sus ventas y compras,
controlar el stock de prendas y saber qué vendió cada cajero. Cada usuario entra con su
clave y solo ve las opciones de su perfil.

## Cómo instalarlo en Windows (sin programar nada)
1. Entra a la sección **Releases** de este repositorio (columna derecha) y descarga `SysVentaRopa-windows.zip`.
2. Descomprime el zip en cualquier carpeta.
3. Abre la carpeta `SysVentaRopa` y haz doble clic en **SysVentaRopa.exe**. No necesita tener Java instalado.
4. Ingresa con el usuario `ventita123` y la clave `123456` (Administrador).

También está `SysVentaRopa.jar`, que se abre con `java -jar SysVentaRopa.jar` si la PC tiene Java 21.

## Integrantes
- Emmanuel Alessandro Mantilla Sucasaire
- Luz Ericka Montalvo Mamani
- Yampier Anderson Machaca Machaca

## Documentos
- `docs/Caso_SysVentaRopa.pdf`: descripción del caso, requerimientos y modelo de base de datos.
- `docs/Manual_de_Usuario_SysVentaRopa.pdf`: manual de usuario por perfil, con capturas.
- `db_modelo/modelo_venta_de_ropa.architect`: modelo en SQL Power Architect.

## Lista de requerimientos
| N° | Requerimiento | Pantalla |
|---|---|---|
| RF01 | Iniciar sesión con usuario y clave (clave encriptada) | Login |
| RF02 | Mostrar el menú según el perfil (Administrador de Tienda, Vendedor/Cajero, Encargado de Almacén) | Principal |
| RF03 | CRUD de usuarios y asignación de perfil | Usuarios |
| RF04 | CRUD de clientes (DNI/RUC), con consulta automática de DNI (RENIEC) y RUC (SUNAT) | Clientes |
| RF05 | CRUD de categorías y unidades de medida | Categorías y unidades |
| RF06 | CRUD de prendas con precio, % de utilidad y stock | Prendas |
| RF07 | Registrar una venta con carrito, IGV 18 % y descuento de stock | Registrar venta |
| RF08 | Consultar el historial de ventas con su detalle | Historial de ventas |
| RF09 | CRUD de proveedores (RUC) | Proveedores |
| RF10 | Registrar una compra a proveedor y aumentar el stock | Registrar compra |
| RF11 | Consultar el historial de compras con su detalle | Historial de compras |
| RF12 | Generar e imprimir la Boleta (cliente con DNI) o Factura (cliente con RUC) de cada venta | Registrar venta, Historial de ventas |
| RF13 | Dashboard de inicio con indicadores de ventas, gráficos, ranking y alertas de stock | Dashboard |
| RF14 | Generar reportes en PDF (estado de cuenta, ventas, compras, inventario, kardex, clientes, proveedores, usuarios, catálogo) con vista previa, guardar e imprimir | Reportes |
| RNF01 | Validar los campos de cada formulario y mostrar un mensaje del resultado | Todas |
| RNF02 | Cambiar el tema (Claro, Oscuro, Rosa, Esmeralda) y el idioma (ES, EN, FR) | Principal |

## Dashboard de inicio (RF13)
Al iniciar sesión se abre un **Dashboard** con el estado de la tienda:
- **Banner de bienvenida** con saludo según la hora, ventas del día y accesos rápidos según el perfil.
- **Meta del mes**: barra de avance (meta = 10 % más que el mes anterior, mínimo S/ 5,000).
- **Indicadores (KPI)** animados: ventas de hoy (vs. ayer), ingresos del mes (vs. el mismo tramo del mes anterior),
  ticket promedio, utilidad estimada y margen, clientes, prendas, unidades/valor del inventario y compras del mes.
- **Gráficos**: tendencia de ventas (7 / 30 / 90 días), dona de ventas por categoría y barras de ventas vs. compras de los últimos 6 meses.
- **Listas**: prendas más vendidas, últimas ventas, prendas con stock bajo (≤ 20 uds.) y ranking de vendedores.
- Las consultas están en `repository/DashboardRepository.java` (SUM / COUNT / GROUP BY), la lógica en
  `service/impl/DashboardServiceImp.java` y la pantalla en `view/main_dashboard.fxml` + `controller/DashboardController.java`.
  Los datos se cargan en un hilo aparte (`Task`) para que la ventana no se congele.

**Datos de demostración**: si la base no tiene ventas al arrancar, `config/DemoDataSeeder.java` crea una tienda de
ejemplo: 7 categorías, ~28 prendas, ~30 clientes (DNI y RUC), 5 proveedores, 5 usuarios y ~6 meses de ventas y compras.
La tienda se **simula día a día**: las compras reponen las prendas que se agotan y las ventas solo usan el stock disponible,
así el stock final, el inventario y el Kardex cuadran (algunas prendas quedan con stock bajo y una agotada, para las alertas).
En cada arranque completa los días que falten hasta hoy (tabla `DemoControl`), así "Ventas de hoy" siempre tiene datos.
Para desactivarlo: `db.demo.data=false` en `application.properties`. Para empezar desde cero, borre la carpeta `data/` con la app cerrada.

**Usuarios de prueba** (clave `123456`, excepto `Emmanuel Mantilla (Admin)` que usa `654321`; `ventita123` y `Emmanuel Mantilla (Admin)` = Administrador,
`Ana Quispe (Cajera Turno M)` y `Rosa Ccama (Cajera Turno T)` = Cajeros, `Luis Mamani (Almacén)` = Almacén).

## Reportes PDF (RF14)
Menú **Reportes → Reportes PDF**. Se elige el reporte, se aplican los filtros y se presiona **Generar**:
se ve la vista previa de las hojas (con zoom) y se puede **Guardar PDF** o **Imprimir**.

| Reporte | Filtros | Perfiles |
|---|---|---|
| Estado de cuenta general (ingresos, egresos, saldo acumulado, IGV, resumen mensual) | Fechas | Administrador |
| Estado de cuenta por cliente | Fechas, cliente, detalle de prendas | Administrador, Vendedor |
| Estado de cuenta por proveedor | Fechas, proveedor, detalle de prendas | Administrador, Almacén |
| Reporte de ventas (agrupado por día) | Fechas, cliente, vendedor, detalle | Administrador, Vendedor |
| Reporte de compras (agrupado por día) | Fechas, proveedor, usuario, detalle | Administrador, Almacén |
| Ventas por prenda y categoría (utilidad y margen) | Fechas, categoría | Administrador, Vendedor |
| Inventario de prendas (valorizado por categoría) | Prenda contiene, categoría, stock máximo, solo stock bajo | Administrador, Almacén |
| Kardex de prendas (entradas, salidas y saldo) | Fechas, prenda | Administrador, Almacén |
| Clientes / Proveedores (compras y total) | Fechas, búsqueda | según módulo |
| Actividad de usuarios | Fechas | Administrador |
| Categorías y unidades de medida | — | Administrador, Almacén |

Cómo está hecho (sin librerías nuevas en el `pom.xml`):
- `repository/ReporteRepository.java`: consultas SQL de solo lectura (JOIN / GROUP BY).
- `service/impl/ReporteServiceImp.java`: arma cada reporte (tablas, indicadores, subtotales, totales).
- `reporte/Reporte.java` (modelo) → `reporte/Maquetador.java` (reparte en hojas A4 y numera "Página X de Y")
  → `reporte/PdfWriter.java` (escribe el PDF con las fuentes estándar Helvetica) y `reporte/VistaReporte.java`
  (dibuja las mismas hojas en pantalla y las imprime). Lo que se ve en la vista previa es lo que sale en el PDF.
- Pantalla: `view/main_reporte.fxml` + `controller/ReporteController.java` (la consulta corre en un hilo aparte).

## Empaquetado: .JAR y .EXE

### Opción rápida: doble clic en `generar-exe.bat` (Windows)
Está en la carpeta del proyecto (junto al `pom.xml`). Hace todo solo:
1. Busca un JDK 21 (el de IntelliJ en `C:\Users\<usuario>\.jdks`, o `JAVA_HOME`).
2. Compila y crea el **.jar** con todas las librerías (`mvnw clean package`).
3. Crea el **.exe** con `jpackage` (incluye Java: funciona en PCs sin Java instalado).
4. Abre la carpeta `Ejecutable/` con el resultado:

| Archivo | Para qué |
|---|---|
| `Ejecutable/SysVentaRopa/SysVentaRopa.exe` | Ejecutable de Windows (doble clic). Copie **toda** la carpeta `SysVentaRopa`, no solo el .exe |
| `Ejecutable/SysVentaRopa.jar` | Ejecutable Java: `java -jar SysVentaRopa.jar` (necesita Java 21) |
| `Ejecutable/SysVentaRopa-portable.zip` | La carpeta del .exe comprimida, para llevarla a otra PC |

La base de datos se crea en la carpeta `data/` junto al .exe (o junto al .jar) la primera vez que se abre.

### Solo el .jar desde IntelliJ
Pestaña **Maven → Lifecycle → package**. Resultado: `target/SysVentaRopa.jar`.

### Cómo está configurado
- `pom.xml` → plugin **maven-shade-plugin**: junta el código y todas las dependencias (JavaFX, H2, HikariCP…) en un solo
  .jar y define la clase principal `pe.edu.upeu.sysventas.App` (un lanzador que no extiende `Application`,
  requisito de JavaFX para un .jar ejecutable).
- `generar-exe.bat` → usa **jpackage** (herramienta incluida en el JDK) para crear el .exe con un Java propio
  y el ícono `sysventaropa.ico`.

## Rediseño visual
- Sistema de diseño en `css/style.css` basado en variables de color; los temas (`tema-oscuro`, `tema-rosa`,
  `tema-esmeralda`) solo cambian esas variables.
- Íconos vectoriales propios (`components/Iconos.java`), sin imágenes ni librerías extra.
- Menú lateral con íconos, modo compacto (botón ☰), reloj en vivo y ruta de la pantalla actual.
- Login en dos paneles con opción para ver la clave y animación de error.
- Notificaciones (toast) y diálogos con el tema activo; gráficos, tablas y barras de desplazamiento restilizados.

## Investigación adicional (Punto 6): encriptación de claves
Las claves de la tabla `Usuario` ya no se guardan en texto plano. Se guarda un **hash SHA-256 con sal**
(`utils/PasswordUtil.java`):
- **Hash SHA-256**: convierte la clave en un código que no se puede revertir.
- **Sal**: 16 bytes aleatorios por usuario, así dos usuarios con la misma clave tienen hashes distintos.
- En la BD se guarda `sal:hash` (69 caracteres). En el login se vuelve a calcular el hash de lo escrito y se compara.
- Al iniciar la app, las claves que aún estén en texto plano (datos de prueba del SQL) se encriptan solas.
- En **Usuarios** se ve la columna "Clave guardada" con el hash. Al editar, si deja la clave vacía se conserva la anterior.

## Boleta / Factura
- Al registrar una venta se pregunta si desea imprimir el comprobante.
- Cliente con **DNI** → BOLETA DE VENTA serie B001; con **RUC** → FACTURA DE VENTA serie F001 (N° = id de la venta, ej. B001-00000012).
- Se puede reimprimir desde **Historial de ventas** → seleccionar venta → *Imprimir boleta / factura*.
- Usa `javafx.print.PrinterJob` (`components/Comprobante.java`). Para guardarlo como PDF elija "Microsoft Print to PDF" en el diálogo.

## Consulta DNI / RUC
- **DNI** (8 dígitos): nombres desde eldni.com (`utils/ConsultaDNI.java`).
- **RUC 10...** (persona con negocio): usa el DNI que va dentro del RUC; no necesita nada más.
- **RUC 20...** (empresa): API de Decolecta (`utils/ConsultaRUC.java`). Registrarse gratis en https://decolecta.com,
  copiar el token y ponerlo en `application.properties` → `api.ruc.token=TU_TOKEN`.
- En **Registrar venta → Nuevo cliente** se llena solo al terminar de escribir el número.
  En **Clientes** se usa el botón *RENIEC / SUNAT*. Necesita internet.

## Ejecutar
```
mvn clean compile javafx:run
```
En IntelliJ: elegir arriba la configuración **SysVentaRopa (Maven)** y darle ▶ (ya hace `clean compile javafx:run`).
Ojo: `javafx:run` solo NO compila; si se usa sin `compile` abre la versión vieja o no abre nada.
o desde IntelliJ: ejecutar la clase `pe.edu.upeu.sysventas.App`.

La base H2 se crea sola en `./data/ventaropa_db` con los datos de prueba de
`src/main/resources/schema_ropa.sql`. Si cambias el esquema, borra el archivo `data/ventaropa_db.mv.db`.

## Usuarios de prueba
| Usuario | Clave | Perfil | Qué ve en el menú |
|---|---|---|---|
| ventita123 | 123456 | Administrador de Tienda | Todo |
| Emmanuel Mantilla (Admin) | 654321 | Administrador de Tienda | Todo |
| Ana Quispe (Cajera Turno M) | 123456 | Vendedor de Tienda / Cajero | Ventas, Clientes, Historial de ventas |

(El perfil "Encargado de Almacén" ve Prendas, Categorías y unidades, Compras, Proveedores e Historial de compras.
Se puede crear un usuario con ese perfil desde **Usuarios**.)

## Tablas del modelo → pantalla que las usa
| Tabla | Pantalla |
|---|---|
| Perfil, Usuario | Login, Usuarios |
| Cliente | Clientes (y registro rápido dentro de Ventas) |
| Categoria, UnidadMedida | Categorías y unidades |
| Productos | Prendas |
| Venta, VentaDetalle | Registrar venta, Historial de ventas |
| proveedor | Proveedores |
| compras, detalle_compra | Registrar compra, Historial de compras |

## Reglas de cálculo
- Precio de venta = Costo (preUnitario) × (1 + porceUtil / 100)  → `porceUtil` se guarda como porcentaje (25 = 25 %).
- Venta: Neto = Σ (precio de venta × cantidad), IGV = Neto × 18 %, Total = Neto + IGV.
- Compra: Neto = Σ (precioCompra × cantidad), IGV = Neto × 18 %, Total = Neto + IGV.
- Al registrar una **venta** el stock baja; al registrar una **compra** el stock sube.
  Cabecera + detalle + stock se guardan en una sola transacción (si algo falla, no se guarda nada).

## Arquitectura (capas)
```
model/         Clases de cada tabla (Venta y Compra heredan de DocumentoComercial)
repository/    SQL con JDBC (un repositorio por tabla)
service/       Reglas de negocio (interfaces + impl)
controller/    Controladores JavaFX (uno por pantalla)
config/        AppContext (inyección de dependencias) y DatabaseConfig (H2 + HikariCP)
components/    Utilidades de la interfaz: tabla, validación, mensajes, temas
resources/view Pantallas FXML    resources/css  Estilos (temas Claro, Oscuro, Rosa)
```
