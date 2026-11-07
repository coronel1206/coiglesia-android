# COG-002 — Equipos, actividades y participantes

Base publicada: `3655d9138af0b00f788dde0a206a7c535b83a379`, rama `work/coiglesia-bloque-01`.
El build GitHub del bloque 01 terminó en success: https://github.com/coronel1206/coiglesia-android/actions/runs/42342294040.

## Incluido

- Menús operativos de ministerios, comités, músicos/equipos musicales, eventos y asambleas.
- Listados con búsqueda por nombre/territorio y filtro por estado.
- Creación y edición, selección de territorio autorizado, fecha mediante calendario, lugar y descripción.
- Confirmación previa, protección contra doble envío y actualización tras guardar.
- Integrantes/participantes: rol o instrumento, activo/inactivo; asistencia para eventos/asambleas.
- Consulta para usuarios con permisos de lectura; gestión habilitada únicamente donde el backend la autoriza.
- Desactivación sin borrado. Ante respuesta incierta se exige actualizar antes de repetir una escritura.

## Contrato y permisos

Se inspeccionaron las definiciones reales de `listar_eclesiastica_app`, `guardar_eclesiastica_app`, `participantes_eclesiastica_app` y `guardar_participante_app`. Se reutilizan sin modificar la base de datos. No se aplicaron nuevas migraciones ni se insertaron registros de prueba en producción.

El permiso `usuarios.administrar` nacional habilita la gestión existente. Otros cargos requieren `eclesiastica.consultar` o `eclesiastica.gestionar`; la selección de personas también respeta `personas.consultar`. No se concedieron permisos adicionales automáticamente. Si una lista está vacía por falta de permisos, no se debe desactivar RLS para habilitarla. El editor de permisos por cargo corresponde al próximo bloque.

## Verificación

51 pruebas PostgreSQL aisladas: las 34 previas y 17 nuevas sobre contratos, alcance nacional/distrital/local, solo lectura, auditoría, participantes únicos, fecha, inactividad y rechazo de acceso directo/anon. Los fixtures reproducen la parte del esquema usada; no equivalen a validar todos los módulos del sistema ni las carreras concurrentes en producción.

Compilación limpia local aprobada: 52 tareas, siete pruebas JVM aprobadas, lint con cero errores y 197 advertencias. La entrega incluye el log de compilación, las pruebas JVM y el informe lint. No se han realizado pruebas instrumentadas en dispositivo ni recorridos con cuentas reales. Se requiere revisar legibilidad, teclado, rotación y navegación en un teléfono antes de fusionar en main.

APK debug: `com.coiglesia.app.preview`, versión `0.4.0-pruebas` (versionCode 4). Usa datos reales de Supabase. No se deben crear personas ni traslados ficticios para ensayarlo.

## Pendiente

Pastoral y credenciales ministeriales; editor de permisos por cargo; edición de obras; informes PDF/Excel; paginación y mejoras de accesibilidad. La gestión musical se organiza en equipos e integrantes, con campo de rol/instrumento; no constituye un catálogo especializado de instrumentos ni repertorios.

## Comprobación manual autorizada

En un entorno de pruebas: comprobar cada menú con perfiles nacional, distrital, local y solo lectura; crear un equipo y un evento, editar y desactivar, registrar integrante y asistencia, intentar territorio ajeno y evento futuro. Probar pérdida de conexión y actualizar antes de repetir. En producción utilizar únicamente operaciones reales necesarias y autorizadas.
