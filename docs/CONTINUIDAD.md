# COIGLESIA — punto de continuidad COG-002A (análisis del manual)

Trabajar por bloques: entregar, subir desde Termux, verificar GitHub Actions y continuar. No asumir persistencia de archivos temporales ni de la memoria del chat.

## Estado verificado

COG-001 publicado por el usuario en `3655d9138af0b00f788dde0a206a7c535b83a379`, rama `work/coiglesia-bloque-01`. GitHub Actions aprobado. Incluye traslados atómicos con origen esperado, membresías, administración inicial, resumen/CSV y edición territorial. El respaldo del avance de Termux se conserva en su rama separada; no mezclarlo de nuevo porque su funcionalidad ya está integrada.

COG-002 añade las interfaces de ministerios, comités, músicos, eventos, asambleas y participantes. Ver `docs/BLOQUE_02.md`. Rama de entrega: `work/coiglesia-bloque-02`; comprobar el SHA remoto y Actions después de que el usuario suba el bloque. La existencia de este documento en una copia local no demuestra que ya se haya publicado.

Supabase: `lmmrzmdhmagggddgdzmt`, nueve migraciones desde `20261106184506` hasta `20261107001140`. No reaplicarlas. En COG-002 no se cambió producción. El APK de pruebas usa este backend real.

## Complemento COG-002A

El usuario aportó el Manual IPUC revisado hasta abril de 2018. Leer primero `docs/arquitectura/MANUAL_IPUC_2018.md`: estructura, diferencias entre cargos/oficios/credenciales, permisos por recurso y organización, procesos colegiados y UX por perfil. El complemento contiene documentación, sin cambiar Android, conceder permisos ni ejecutar migraciones.

COG-002 está publicado en `f672d6c2fb6cd1ec74ecc21b0efcd05f2f453c15`; Actions 42343104701 terminó correctamente. La carpeta antigua reapareció con cambios de una etapa anterior: se conservó y se recuperó GitHub en una copia separada. No intentar aplicar esos cambios antiguos encima del bloque 02.

La revisión automática de la consulta Supabase alcanzó el límite de uso; la consulta no se ejecutó. Queda pendiente volver a verificar las funciones en vivo cuando el acceso se restablezca. No sustituir esa comprobación por suposiciones ni por otra vía para eludir el control.

## Próximo bloque COG-003

Consolidar la administración de permisos: inspeccionar `permisos_cargo_app` y `configurar_permisos_cargo_app`; implementar un editor nacional con confirmación y protección de los cargos propios. Revisar perfiles de lectura/gestión territorial y ampliar pruebas sin otorgar permisos generales. Después continuar pastoral, credenciales y edición de obras, informes y pruebas en dispositivo. No considerar la aplicación terminada únicamente porque compile.

## Mensaje de reanudación

Continúa Coiglesia desde el complemento COG-002A. Lee docs/CONTINUIDAD.md y docs/arquitectura/MANUAL_IPUC_2018.md en coronel1206/coiglesia-android, rama work/coiglesia-bloque-02a-manual (o main si ya se fusionó). La última versión funcional Android sigue siendo COG-002. Verifica el commit y Actions, compara con Supabase y desarrolla solo el bloque COG-003. No repitas migraciones ni sobrescribas cambios. Entrega parche, APK verificado, instrucciones Termux y punto de continuidad actualizado.

## Conservación

Guardar cada entrega descargable y subir el bloque a GitHub desde Termux. Si se interrumpe Work antes del push, adjuntar el ZIP e indicar el último comando exitoso. El script debe detenerse ante cambios locales y nunca usar reset --hard ni forzar publicaciones. No fusionar main antes de revisar CI y validar en dispositivo.
