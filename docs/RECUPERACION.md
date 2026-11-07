# Recuperación Coiglesia — 7 de noviembre de 2026

GitHub permanece en 3fde7ae. El entorno reanudado contiene los cambios iniciales de traslados, administración y resumen, pero no conserva los archivos de la segunda etapa ni el commit local e86ac4e.
Supabase conserva nueve migraciones, incluyendo equipos/actividades, pastoral, territorio y obras. No repetirlas.
El APK encontrado al reanudar correspondía a la etapa anterior y no se entregó como una compilación actual.

## Recuperación efectuada

- Se recuperaron los cuatro archivos SQL faltantes directamente de `supabase_migrations.schema_migrations`, sin volver a ejecutarlos en producción.
- Se restauró la edición de distritos y congregaciones con confirmación de estado, una RPC, bloqueo durante el envío y actualización de la lista.
- Se conectaron los selectores de congregaciones y obras a sus RPC de permisos específicas. El listado nacional de distritos sigue separado.
- Se corrigió un atributo del tema que requería API 27, conservando compatibilidad con el mínimo API 26.
- El resumen incluye eventos, asambleas y asistencias, obtenidos de la RPC existente y exportados a CSV.
- La variante debug se identifica como `com.coiglesia.app.preview`, «Coiglesia Pruebas», versión 0.3.0-pruebas. Puede coexistir con la aplicación original. **Usa el backend real: no introducir datos ficticios ni realizar traslados de prueba en producción.**

## Evidencia y límites

- Compilación limpia local aprobada: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, 52 tareas. Siete pruebas JVM aprobadas (cuatro de validación y tres de sesión); lint sin errores. Persisten advertencias que requieren revisión posterior.

- 34 pruebas PostgreSQL aisladas aprobadas: traslados atómicos, duplicados, reversión, permisos, auditoría, administración y edición territorial. Las últimas cinco prueban la migración histórica de edición territorial; no representan cobertura completa de las nueve migraciones.
- Comprobación de producción, solo lectura: cero tablas públicas sin RLS, cero funciones SECURITY DEFINER ejecutables por anon y cero personas con más de una membresía activa. Estos indicadores no sustituyen una auditoría completa de las funciones.
- GitHub continúa en `3fde7ae`; una prueba de publicación sin escritura confirmó ausencia de autenticación. Ningún cambio de esta recuperación está publicado y GitHub Actions no los ha ejecutado.
- No se realizaron pruebas en dispositivo, inicio de sesión con cuentas reales ni operaciones ficticias en producción.

## Pendientes para continuar sin repetir trabajo

1. Restaurar las pantallas de equipos/ministerios/comités/músicos/eventos/asambleas y pastoral usando las RPC ya desplegadas; sus interfaces no están incluidas en esta recuperación.
2. Restaurar el editor de permisos por cargo y la edición de obras, conservando las restricciones del backend.
3. Ampliar pruebas aisladas al esquema final completo y a las funciones pastorales/actividades; comprobar flujos en un entorno de pruebas y dispositivo.
4. Publicar la rama con una conexión GitHub con escritura, ejecutar Actions y conservar el SHA de la entrega.
5. Completar informes PDF/Excel, paginación y revisión visual/accesible, gestión de credenciales ministeriales y preparación de firma de distribución. No considerar esta versión una entrega final de producción.

Los parches de recuperación se aplican sobre el commit base `3fde7ae` únicamente después de revisar el estado de la copia destino. No usar reset --hard ni sobrescribir cambios de Termux. Si GitHub tiene nuevos commits, integrar en una rama y resolver diferencias.
