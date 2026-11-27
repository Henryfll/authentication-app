package com.henryfll.auth.shared.audit;

/**
 * Registro de auditoría. Lo usan los casos de uso de todos los módulos; lo implementa el módulo
 * audit.
 *
 * <p>Cada evento se guarda en su propia transacción: se conserva aunque la operación que lo
 * registra termine en error (p. ej. un login fallido).
 */
public interface AuditLog {

  void record(AuditEvent event);
}
