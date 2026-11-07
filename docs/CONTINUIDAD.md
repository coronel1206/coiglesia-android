# COIGLESIA — punto de continuidad COG-003

Trabajar por bloques: desarrollar, probar, entregar para Termux, verificar publicación y Actions antes de continuar. No asumir que Work conserva archivos tras una interrupción.

## Historial publicado

- COG-001: `3655d91`, traslados atómicos y origen esperado, administración inicial y edición territorial. Actions aprobado.
- COG-002: `f672d6c`, equipos, actividades y participantes. Actions 42343104701 aprobado. El usuario confirmó instalación.
- COG-002A: `6597945cdc6d7efd06a55861df775c05e92a7677`, análisis del manual IPUC de 2018 y requisitos de permisos/UX. No cambió Android ni producción.

Se recuperó la base publicada en una copia separada porque la carpeta anterior reapareció con cambios de una etapa previa. No reaplicar esos cambios antiguos ni borrar la carpeta sin revisar.

## Bloque actual

COG-003 prepara el editor de permisos por cargo. Leer `docs/BLOQUE_03.md` y conservar `docs/arquitectura/MANUAL_IPUC_2018.md` como referencia configurable por organización.

Las definiciones de las cuatro funciones requeridas fueron aportadas por el usuario desde SQL Editor. La aplicación nueva usa `configurar_permisos_cargo_verificado_app` para detectar una selección desactualizada y conserva las validaciones y auditoría de la función original.

El SQL `database/manual/COG_003_permisos_verificados.sql` fue probado exclusivamente en una base aislada. **Su instalación en producción queda pendiente de confirmación del usuario.** Revisar `COG_003_verificar_instalacion.sql`; no ejecutar otra vez la instalación si ya existe la función ni sustituirla sin inspección. La firma antigua se conserva para uso interno y se retira su ejecución directa para usuarios de la app.

La rama de entrega es `work/coiglesia-bloque-03`. Su publicación y build remoto deben verificarse después del push del usuario. El APK funcional 0.5.0-pruebas se obtiene de Actions con las variables públicas del repositorio. El APK local de comprobación no tiene configuración de conexión y no se entrega para instalar.

Supabase: `lmmrzmdhmagggddgdzmt`. Conservar las nueve migraciones previamente aplicadas. El SQL nuevo se ejecutará manualmente: no afirmar que la herramienta lo registró automáticamente en el historial de migraciones.

## Siguiente bloque

Aislamiento entre organizaciones y alcance de permisos por equipo/departamento. Inspeccionar schema real y datos agregados antes de diseñar migraciones. No incorporar otra organización al mismo conjunto de datos sin comprobar aislamiento. No conceder `eclesiastica.gestionar` a un director musical suponiendo que solo autoriza música. Después: cargos/oficios, credenciales, actas y flujos pastorales, interfaz final por perfil y pruebas en dispositivo.

## Mensaje para reanudar

Continúa Coiglesia desde COG-003. Revisa work/coiglesia-bloque-03, docs/BLOQUE_03.md y docs/arquitectura/MANUAL_IPUC_2018.md. Comprueba el commit remoto, Actions y si el usuario aplicó el SQL manual. Conserva lo existente, no repitas migraciones y desarrolla el siguiente bloque con entrega e instrucciones de Termux.

Si el bloque aún no se publicó, recuperar el ZIP y preguntar por el último comando completado. No fusionar main antes de verificar CI y el recorrido en dispositivo. Las pruebas no deben introducir datos ficticios en producción.
