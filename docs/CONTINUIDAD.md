# COIGLESIA — punto de continuidad COG-001

## Regla de trabajo por bloques

Entregar un bloque funcional verificado antes de comenzar el siguiente. Cada bloque debe incluir cambios, pruebas ejecutadas, pendientes, archivos descargables y comandos de integración en una rama. Después de que el usuario lo suba desde Termux, verificar el commit remoto antes de avanzar. No asumir que un archivo local o la memoria del chat seguirán disponibles tras una interrupción.

## Bloque COG-001: recuperación, traslados y edición territorial

Base remota verificada: `3fde7ae52af1daca871106919269aa1a6fc7e495`, repositorio `coronel1206/coiglesia-android`.
Aplicación debug: `com.coiglesia.app.preview`, versión `0.3.0-pruebas`.
Incluye traslados con origen esperado y una RPC atómica, administración inicial de cargos/usuarios, resumen/CSV, edición de distritos y congregaciones y selectores de obras según permisos.

El APK usa Supabase real y puede instalarse junto a la aplicación original. No insertar datos ficticios en producción. No se ha probado en un dispositivo ni con cuentas reales.

Supabase: proyecto `lmmrzmdhmagggddgdzmt`. Nueve migraciones aplicadas desde `20261106184506` hasta `20261107001140`; no reaplicar. Los archivos en `database/changes` documentan esas migraciones. Los fixtures de `tests/database` se usan exclusivamente en PostgreSQL aislado.

Estado detallado: `docs/RECUPERACION.md`. El paquete de entrega incluye resultados de compilación/pruebas y el SHA local. La publicación por Termux producirá un SHA propio, que debe consultarse en GitHub.

## Próximo bloque COG-002

Recuperar y probar las pantallas de ministerios, comités, músicos, eventos, asambleas y participantes usando las funciones existentes: `listar_eclesiastica_app`, `guardar_eclesiastica_app`, `participantes_eclesiastica_app`, `guardar_participante_app`. Inspeccionar firmas y permisos actuales antes de implementar. Ampliar pruebas aisladas al esquema final. No diseñar tablas duplicadas.

Después: pastoral y credenciales; editor de permisos por cargo; edición de obras; informes y pulido visual; validación en dispositivo y firma de distribución. Las funciones backend de varias de estas tareas ya existen, pero su presencia no significa que la interfaz Android esté terminada.

## Mensaje para reanudar en Work

Continúa Coiglesia desde el bloque COG-001. Revisa el repositorio coronel1206/coiglesia-android, la rama work/coiglesia-bloque-01 y docs/CONTINUIDAD.md y docs/RECUPERACION.md. Compara el estado remoto con Supabase antes de modificar. Si la rama fue fusionada, continúa desde main. No repitas las nueve migraciones ya aplicadas. Desarrolla únicamente el siguiente bloque COG-002 y entrégalo probado, con parche, instrucciones para Termux y punto de continuidad actualizado. No sobrescribas cambios existentes ni supongas que conservas los archivos de la sesión anterior.

## Publicación y compilación remota

No hay conexión GitHub con escritura en Work. El usuario integra y publica desde Termux. Mantener main estable y trabajar en una rama.
El workflow requiere las variables de repositorio `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY` (clave pública; nunca service_role). Si faltan, documentar el fallo de Actions y configurar esas variables antes de declarar la compilación remota aprobada.
