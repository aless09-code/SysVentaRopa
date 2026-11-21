package pe.edu.upeu.sysventas.service;

import pe.edu.upeu.sysventas.dto.DashboardResumen;

public interface IDashboardService {
    /** Stock igual o menor a este valor se considera "stock bajo". */
    int STOCK_MINIMO = 20;

    /**
     * Calcula todos los indicadores del dashboard.
     * @param diasPeriodo días hacia atrás para los gráficos (7, 30 o 90).
     */
    DashboardResumen resumen(int diasPeriodo);
}
