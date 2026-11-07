# COG-003 — Editor nacional de permisos por cargo

## Base y evidencia

Parte de `6597945cdc6d7efd06a55861df775c05e92a7677`, rama publicada `work/coiglesia-bloque-02a-manual`. Conserva COG-002 y el análisis IPUC de 2018.
El usuario aportó desde SQL Editor las definiciones actuales de `configurar_permisos_cargo_app`, `permisos_cargo_app`, `listar_administracion_app` y `mi_acceso_app`. Esas definiciones son el contrato utilizado; no se sustituyó la consulta bloqueada por otra conexión.

## Comportamiento nuevo

En Usuarios y cargos → Cargos y permisos establecidos aparece «Revisar y editar permisos». Consulta el catálogo real, muestra descripción y código, respeta la selección vigente y confirma explícitamente los permisos añadidos/retirados. El cambio afecta a todos los titulares del cargo en sus respectivos ámbitos y vigencias.

Una selección vacía requiere confirmación y deja ese cargo sin permisos; no elimina personas ni asignaciones. El backend impide alterar el cargo vigente del propio administrador. Un cargo no nacional no puede recibir `usuarios.administrar`. Ninguna plantilla del manual se carga o concede automáticamente.

Se informa que `eclesiastica.gestionar` aún abarca todos los equipos del territorio; el alcance por departamento/recurso corresponde al siguiente bloque. No se habilita la segunda organización en el mismo conjunto de datos.

## SQL propuesto, pendiente de ejecutar por el usuario

`database/manual/COG_003_permisos_verificados.sql` instala la nueva función `configurar_permisos_cargo_verificado_app(uuid,text[],text[])`. Bloquea la fila del cargo, compara el conjunto actual con el leído al abrir el editor y llama a la función original para validar y auditar en la misma transacción. Una edición desactualizada falla sin sobrescribir.

La firma original se conserva, pero se revoca su ejecución directa a PUBLIC, anon y authenticated, para que no pueda evitarse la comprobación de concurrencia. La firma nueva se concede solo a authenticated y verifica autorización nacional en su interior. El código de Android anterior a COG-003 no usa la función original de modificación. Si existe otro cliente externo que sí la use, necesita adoptar la nueva firma antes de aplicar el cambio. No se amplían los permisos asignados a cargos.

El SQL no se ha aplicado a Supabase desde Work. Debe ejecutarse en el proyecto correcto mediante SQL Editor; luego ejecutar `COG_003_verificar_instalacion.sql` y conservar la salida. Al ser una ejecución manual, no se debe afirmar que aparece automáticamente en `supabase_migrations.schema_migrations`; el script y su commit son el registro de esta entrega.

Resultado esperado de verificación:

| Función | anon ejecuta | authenticated ejecuta |
|---|---|---|
| configurar_permisos_cargo_app | false | false |
| configurar_permisos_cargo_verificado_app | false | true |
| permisos_cargo_app | false | true |

Todas deben conservar SECURITY DEFINER y search_path vacío. La verificación es de solo lectura; no guarda permisos en cargos reales.

## Recuperación ante fallos

Sin la nueva RPC, el editor no utiliza la firma antigua como alternativa: informa el error y pide cerrar y recargar. Ante timeout o conflicto no reenvía automáticamente. El usuario revisa la selección actual antes de repetir.

La comparación detecta cambios de conjunto desde la lectura; no implementa un registro de versiones que detecte cambios intermedios que luego vuelvan exactamente al conjunto original. Un administrador SQL con privilegios de propietario sigue pudiendo administrar la base: la protección se dirige a las operaciones de la app.

## Pruebas y compilación

64 escenarios PostgreSQL aislados: 51 anteriores y 13 nuevos, incluidos cargo propio, actor no nacional, permiso inexistente, auditoría, snapshot desactualizado, respuesta perdida, vaciado explícito y bloqueo de la firma antigua/anon. No son pruebas de carga o dos conexiones reales concurrentes en producción.

Compilación local aprobada: 52 tareas, diez pruebas JVM aprobadas, cero errores de lint y 204 advertencias pendientes.

Tres pruebas JVM nuevas validan el resumen que se confirma (altas/bajas, retirada total y cambios solo de orden), además de las siete previas.

La compilación local se realiza sin configuración de Supabase para verificar Kotlin, recursos, tests y lint. Ese APK de comprobación no se distribuye para instalar. El APK funcional 0.5.0-pruebas (versionCode 5) se obtiene de GitHub Actions, que ya usa las variables públicas configuradas por el usuario. No se piden claves administrativas.

No se realizaron operaciones de prueba en producción ni pruebas instrumentadas en dispositivo. Después de instalar el SQL y descargar el APK de Actions, verificar la interfaz con operaciones reales autorizadas o en un entorno de pruebas. Mantener main estable hasta aprobar ese recorrido.

## Próximo bloque

Diseñar y probar el alcance por organización y por equipo/departamento antes de ampliar privilegios. Conservar el documento `docs/arquitectura/MANUAL_IPUC_2018.md` como referencia configurable. La interfaz final por perfil se construirá sobre capacidades efectivas del backend, no sobre nombres de cargo.
