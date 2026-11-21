package pe.edu.upeu.sysventas.service;

import pe.edu.upeu.sysventas.dto.FiltroReporte;
import pe.edu.upeu.sysventas.enums.TipoReporte;
import pe.edu.upeu.sysventas.reporte.Reporte;
import pe.edu.upeu.sysventas.repository.ReporteRepository.Opcion;

import java.util.List;

/** Arma los reportes (estado de cuenta, ventas, compras, inventario, kardex, etc.). */
public interface IReporteService {

    Reporte generar(TipoReporte tipo, FiltroReporte filtro);

    List<Opcion> clientes();

    List<Opcion> proveedores();

    List<Opcion> productos();

    List<Opcion> categorias();

    List<Opcion> usuarios();
}
