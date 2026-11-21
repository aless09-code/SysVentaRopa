package pe.edu.upeu.sysventas.components;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Generador de códigos QR (norma ISO/IEC 18004) sin librerías externas.
 * Devuelve una matriz de módulos: true = cuadrito negro.
 *
 * <p>Se usa en la boleta/factura con el formato que pide SUNAT para los comprobantes:
 * {@code RUC|TIPO|SERIE|NUMERO|IGV|TOTAL|FECHA|TIPO_DOC_CLIENTE|NUM_DOC_CLIENTE|}
 *
 * <p>Pasos: 1) el texto se convierte en bytes, 2) se agregan códigos de corrección de errores
 * (Reed-Solomon, nivel M = se recupera hasta ~15 % dañado), 3) se dibujan los patrones fijos
 * (las 3 esquinas, líneas de tiempo, alineación) y los datos, 4) se aplica la "máscara" que
 * deja el dibujo más fácil de leer.
 */
public final class CodigoQR {

    private CodigoQR() {
    }

    // Nivel de corrección M: códigos de corrección por bloque y número de bloques, según versión (1..40)
    private static final int[] ECC_POR_BLOQUE = {-1,
            10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
            26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28};
    private static final int[] NUM_BLOQUES = {-1,
            1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
            17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49};
    private static final int BITS_FORMATO_M = 0;   // nivel M en la información de formato

    private boolean[][] modulos;
    private boolean[][] esFijo;
    private int tam;
    private int version;

    /** Genera el QR del texto. La matriz es cuadrada: [fila][columna]. */
    public static boolean[][] generar(String texto) {
        byte[] datos = texto.getBytes(StandardCharsets.UTF_8);
        for (int v = 1; v <= 40; v++) {
            int capacidadBits = codigosDatos(v) * 8;
            int bitsUsados = 4 + (v <= 9 ? 8 : 16) + datos.length * 8;
            if (bitsUsados <= capacidadBits) {
                CodigoQR qr = new CodigoQR();
                qr.construir(v, datos);
                return qr.modulos;
            }
        }
        throw new IllegalArgumentException("Texto demasiado largo para un código QR");
    }

    // ------------------------------------------------------------------ construcción

    private void construir(int ver, byte[] datos) {
        version = ver;
        tam = ver * 4 + 17;
        modulos = new boolean[tam][tam];
        esFijo = new boolean[tam][tam];

        // 1) Bits: modo byte (0100) + longitud + datos + terminador + relleno
        List<Boolean> bits = new ArrayList<>();
        agregarBits(bits, 0b0100, 4);
        agregarBits(bits, datos.length, ver <= 9 ? 8 : 16);
        for (byte b : datos) agregarBits(bits, b & 0xFF, 8);
        int capacidad = codigosDatos(ver) * 8;
        agregarBits(bits, 0, Math.min(4, capacidad - bits.size()));
        agregarBits(bits, 0, (8 - bits.size() % 8) % 8);
        for (int relleno = 0xEC; bits.size() < capacidad; relleno ^= 0xEC ^ 0x11) agregarBits(bits, relleno, 8);

        byte[] codigos = new byte[bits.size() / 8];
        for (int i = 0; i < bits.size(); i++) {
            if (bits.get(i)) codigos[i >>> 3] |= (byte) (1 << (7 - (i & 7)));
        }

        // 2) Corrección de errores e intercalado de bloques
        byte[] todo = agregarCorreccion(codigos);

        // 3) Patrones fijos y datos
        dibujarPatronesFijos();
        dibujarCodigos(todo);

        // 4) Elegir la mejor máscara (la de menor penalidad)
        int mejor = 0;
        long menor = Long.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            aplicarMascara(m);
            dibujarFormato(m);
            long p = penalidad();
            if (p < menor) {
                menor = p;
                mejor = m;
            }
            aplicarMascara(m);   // deshace (XOR)
        }
        aplicarMascara(mejor);
        dibujarFormato(mejor);
    }

    private static void agregarBits(List<Boolean> bits, int valor, int cantidad) {
        for (int i = cantidad - 1; i >= 0; i--) bits.add(((valor >>> i) & 1) != 0);
    }

    // ------------------------------------------------------------------ capacidad

    private static int modulosDatosCrudos(int ver) {
        int r = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int numAlin = ver / 7 + 2;
            r -= (25 * numAlin - 10) * numAlin - 55;
            if (ver >= 7) r -= 36;
        }
        return r;
    }

    private static int codigosDatos(int ver) {
        return modulosDatosCrudos(ver) / 8 - ECC_POR_BLOQUE[ver] * NUM_BLOQUES[ver];
    }

    // ------------------------------------------------------------------ Reed-Solomon

    private byte[] agregarCorreccion(byte[] datos) {
        int numBloques = NUM_BLOQUES[version];
        int eccLen = ECC_POR_BLOQUE[version];
        int totalCodigos = modulosDatosCrudos(version) / 8;
        int bloquesCortos = numBloques - totalCodigos % numBloques;
        int largoCorto = totalCodigos / numBloques;

        byte[] divisor = divisorRS(eccLen);
        byte[][] bloques = new byte[numBloques][];
        for (int i = 0, k = 0; i < numBloques; i++) {
            int largoDatos = largoCorto - eccLen + (i < bloquesCortos ? 0 : 1);
            byte[] dat = new byte[largoDatos];
            System.arraycopy(datos, k, dat, 0, largoDatos);
            k += largoDatos;
            byte[] ecc = restoRS(dat, divisor);
            byte[] bloque = new byte[largoCorto + 1];
            System.arraycopy(dat, 0, bloque, 0, largoDatos);
            // en los bloques cortos queda un hueco (posición largoDatos) que se salta al intercalar
            System.arraycopy(ecc, 0, bloque, largoCorto + 1 - eccLen, eccLen);
            bloques[i] = bloque;
        }

        byte[] res = new byte[totalCodigos];
        int n = 0;
        for (int i = 0; i < bloques[0].length; i++) {
            for (int j = 0; j < bloques.length; j++) {
                if (i != largoCorto - eccLen || j >= bloquesCortos) res[n++] = bloques[j][i];
            }
        }
        return res;
    }

    private static byte[] divisorRS(int grado) {
        byte[] r = new byte[grado];
        r[grado - 1] = 1;
        int raiz = 1;
        for (int i = 0; i < grado; i++) {
            for (int j = 0; j < r.length; j++) {
                r[j] = (byte) multiplicar(r[j] & 0xFF, raiz);
                if (j + 1 < r.length) r[j] ^= r[j + 1];
            }
            raiz = multiplicar(raiz, 0x02);
        }
        return r;
    }

    private static byte[] restoRS(byte[] datos, byte[] divisor) {
        byte[] r = new byte[divisor.length];
        for (byte b : datos) {
            int factor = (b ^ r[0]) & 0xFF;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= (byte) multiplicar(divisor[i] & 0xFF, factor);
        }
        return r;
    }

    /** Multiplicación en el campo de Galois GF(2^8) con el polinomio 0x11D. */
    private static int multiplicar(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    // ------------------------------------------------------------------ dibujo

    private void fijo(int x, int y, boolean negro) {
        modulos[y][x] = negro;
        esFijo[y][x] = true;
    }

    private void dibujarPatronesFijos() {
        // Líneas de tiempo
        for (int i = 0; i < tam; i++) {
            fijo(6, i, i % 2 == 0);
            fijo(i, 6, i % 2 == 0);
        }
        // Las 3 esquinas (patrones de búsqueda)
        buscador(3, 3);
        buscador(tam - 4, 3);
        buscador(3, tam - 4);
        // Patrones de alineación
        int[] pos = posicionesAlineacion();
        int n = pos.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
                alineacion(pos[i], pos[j]);
            }
        }
        dibujarFormato(0);   // reserva el lugar; se dibuja bien al final
        dibujarVersion();
    }

    private void buscador(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && xx < tam && yy >= 0 && yy < tam) fijo(xx, yy, dist != 2 && dist != 4);
            }
        }
    }

    private void alineacion(int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                fijo(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private int[] posicionesAlineacion() {
        if (version == 1) return new int[0];
        int num = version / 7 + 2;
        int paso = (version == 32) ? 26 : (version * 4 + num * 2 + 1) / (num * 2 - 2) * 2;
        int[] r = new int[num];
        r[0] = 6;
        for (int i = num - 1, pos = tam - 7; i >= 1; i--, pos -= paso) r[i] = pos;
        return r;
    }

    private void dibujarFormato(int mascara) {
        int datos = BITS_FORMATO_M << 3 | mascara;
        int resto = datos;
        for (int i = 0; i < 10; i++) resto = (resto << 1) ^ ((resto >>> 9) * 0x537);
        int bits = (datos << 10 | resto) ^ 0x5412;

        for (int i = 0; i <= 5; i++) fijo(8, i, bit(bits, i));
        fijo(8, 7, bit(bits, 6));
        fijo(8, 8, bit(bits, 7));
        fijo(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) fijo(14 - i, 8, bit(bits, i));

        for (int i = 0; i < 8; i++) fijo(tam - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) fijo(8, tam - 15 + i, bit(bits, i));
        fijo(8, tam - 8, true);   // módulo oscuro fijo
    }

    private void dibujarVersion() {
        if (version < 7) return;
        int resto = version;
        for (int i = 0; i < 12; i++) resto = (resto << 1) ^ ((resto >>> 11) * 0x1F25);
        long bits = (long) version << 12 | resto;
        for (int i = 0; i < 18; i++) {
            boolean b = ((bits >>> i) & 1) != 0;
            int a = tam - 11 + i % 3, c = i / 3;
            fijo(a, c, b);
            fijo(c, a, b);
        }
    }

    private void dibujarCodigos(byte[] datos) {
        int i = 0;
        for (int derecha = tam - 1; derecha >= 1; derecha -= 2) {
            if (derecha == 6) derecha = 5;
            for (int vert = 0; vert < tam; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = derecha - j;
                    boolean subiendo = ((derecha + 1) & 2) == 0;
                    int y = subiendo ? tam - 1 - vert : vert;
                    if (!esFijo[y][x] && i < datos.length * 8) {
                        modulos[y][x] = bit(datos[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void aplicarMascara(int m) {
        for (int y = 0; y < tam; y++) {
            for (int x = 0; x < tam; x++) {
                boolean invertir = switch (m) {
                    case 0 -> (x + y) % 2 == 0;
                    case 1 -> y % 2 == 0;
                    case 2 -> x % 3 == 0;
                    case 3 -> (x + y) % 3 == 0;
                    case 4 -> (x / 3 + y / 2) % 2 == 0;
                    case 5 -> x * y % 2 + x * y % 3 == 0;
                    case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
                    default -> ((x + y) % 2 + x * y % 3) % 2 == 0;
                };
                if (invertir && !esFijo[y][x]) modulos[y][x] = !modulos[y][x];
            }
        }
    }

    /** Penalidad de la norma (reglas 1 a 4): menos es mejor. */
    private long penalidad() {
        long p = 0;
        for (int y = 0; y < tam; y++) {          // regla 1: 5+ módulos iguales seguidos
            p += rachas(true, y);
            p += rachas(false, y);
        }
        for (int y = 0; y < tam - 1; y++) {      // regla 2: bloques 2x2 del mismo color
            for (int x = 0; x < tam - 1; x++) {
                boolean c = modulos[y][x];
                if (c == modulos[y][x + 1] && c == modulos[y + 1][x] && c == modulos[y + 1][x + 1]) p += 3;
            }
        }
        for (int i = 0; i < tam; i++) {          // regla 3: dibujos parecidos a las esquinas
            p += parecidoEsquina(true, i);
            p += parecidoEsquina(false, i);
        }
        int negros = 0;                          // regla 4: proporción de negros cerca de 50 %
        for (boolean[] fila : modulos) for (boolean b : fila) if (b) negros++;
        int total = tam * tam;
        int k = (Math.abs(negros * 20 - total * 10) + total - 1) / total - 1;
        p += Math.max(0, k) * 10L;
        return p;
    }

    /** Cuenta 1:1:3:1:1 (negro-blanco-negro x3-blanco-negro) con 4 blancos a un lado. */
    private long parecidoEsquina(boolean horizontal, int linea) {
        // se agregan 4 módulos blancos a cada lado (el margen del QR)
        boolean[] v = new boolean[tam + 8];
        for (int i = 0; i < tam; i++) v[i + 4] = horizontal ? modulos[linea][i] : modulos[i][linea];
        boolean[] patron = {true, false, true, true, true, false, true};
        long p = 0;
        for (int i = 0; i + 7 <= v.length; i++) {
            boolean coincide = true;
            for (int k = 0; k < 7 && coincide; k++) coincide = v[i + k] == patron[k];
            if (!coincide) continue;
            boolean blancosAntes = i >= 4 && !v[i - 1] && !v[i - 2] && !v[i - 3] && !v[i - 4];
            boolean blancosDespues = i + 11 <= v.length && !v[i + 7] && !v[i + 8] && !v[i + 9] && !v[i + 10];
            if (blancosAntes || blancosDespues) p += 40;
        }
        return p;
    }

    private long rachas(boolean horizontal, int linea) {
        long p = 0;
        int racha = 0;
        boolean anterior = false;
        for (int i = 0; i < tam; i++) {
            boolean c = horizontal ? modulos[linea][i] : modulos[i][linea];
            if (i > 0 && c == anterior) {
                racha++;
                if (racha == 5) p += 3;
                else if (racha > 5) p++;
            } else {
                racha = 1;
                anterior = c;
            }
        }
        return p;
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }
}
