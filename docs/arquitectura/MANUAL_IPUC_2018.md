# COG-002A — Modelo organizacional, permisos y experiencia por perfil

## Alcance y fuente

Fuente aportada por el usuario: Manual de la Iglesia Pentecostal Unida de Colombia, revisión hasta abril de 2018, PDF de 198 páginas. Se revisaron las secciones organizativas, ministeriales, territoriales, locales, departamentales y disciplinarias; las páginas citadas corresponden a la numeración impresa. No se presenta este archivo como verificación de vigencia normativa en 2026 ni como interpretación jurídica. Los artículos de fe no se convierten en permisos informáticos.

Este documento es una especificación de producto para Coiglesia, no una migración ni una concesión de permisos. La IPUC es una organización beneficiaria, no una configuración obligatoria para todas las iglesias. Los nombres, requisitos y períodos se versionarán por organización. No se copia el PDF completo al repositorio público.

Base real revisada: bloque COG-002, commit remoto `f672d6c2fb6cd1ec74ecc21b0efcd05f2f453c15`, build exitoso https://github.com/coronel1206/coiglesia-android/actions/runs/42343104701. La carpeta de trabajo anterior volvió a un estado previo; se conservó intacta y se recuperó la rama publicada en una copia nueva.

La consulta en vivo de Supabase quedó pendiente por límite de uso de la revisión automática. Las referencias técnicas siguientes proceden de los archivos SQL publicados; no certifican ausencia de modificaciones posteriores en producción. No se ha cambiado producción durante este análisis.

## 1. Lo que cambia en el modelo conceptual

Separar explícitamente estas dimensiones:

1. **Organización**: titular de sus datos, reglamento, identidad visual y autoridades. El nivel nacional pertenece a una organización, no a toda la plataforma Coiglesia.
2. **Territorio**: organización → distrito → congregación. Una obra puede depender de congregación o administración distrital según el reglamento; las regiones mencionadas para coordinación no se convierten automáticamente en un nuevo nivel con acceso nacional.
3. **Gobierno**: órganos colegiados y cargos con jurisdicción. Una decisión de un órgano no equivale a la acción unilateral de uno de sus integrantes.
4. **Departamento y oficio**: áreas de servicio y sus responsabilidades. Ser director musical no equivale a gobernar el distrito.
5. **Persona y membresía congregacional**: historial de pertenencia, distinto de la cuenta de acceso y del ministerio pastoral.
6. **Credencial ministerial**: tipo, emisión, vigencia, autoridad, soporte y estado. Es distinta del cargo y del nombramiento pastoral.
7. **Asignación y delegación**: quién desempeña la responsabilidad, dónde, desde cuándo, hasta cuándo, con qué documento y facultades.
8. **Expediente o recurso**: una actividad, un equipo, una asamblea o un caso concreto puede requerir autorización más limitada que todo el territorio.

No se eliminarán `personas`, `cargos`, `asignaciones_cargos`, `membresias_congregacionales` ni las RPC que ya funcionan. Antes de cualquier migración se inspeccionarán tablas y funciones actuales, dependencias y datos agregados. Las modificaciones serán aditivas, con pruebas y plan de compatibilidad.

## 2. Trazabilidad: manual → necesidad de aplicación

| ID | Fuente del manual | Necesidad funcional | Estado respecto al bloque 02 |
|---|---|---|---|
| ORG-01 | pp. 35–40, art. 10 y siguientes | Asamblea General, Consistorio, Presidencia, Directivos Distritales, Asamblea de Directivos y Asamblea Distrital como órganos diferenciados | Cargos/asambleas genéricos; gobierno colegiado pendiente |
| MIN-01 | pp. 31–34 y 102–106 | Separar feligrés, predicador, licencias local/general/ordenación e identificación vigente; historial de promociones y aprobaciones | Credenciales y funciones pastorales existentes en SQL; no asumir equivalencia de sus categorías con el manual |
| ASAM-01 | pp. 36–37, 54, 59, 97 y 107 | Plena/delegataria/distrital; padrón habilitado, delegado, voz, voto y observador; retiro ministerial no confiere voto por sí solo | Registro y asistencia disponibles; quórum, padrón electoral y votación pendientes |
| GOB-01 | pp. 63–65, 74–76 | Postulación, calificación, impedimentos, elección, ratificación; incompatibilidad de cargos de gobierno | Asignación directa existente; no implementa el proceso electoral ni todas las incompatibilidades |
| VIG-01 | pp. 40, 50, 75, 91, 113 y 167 | Períodos diferentes por cargo/oficio, reemplazos por saldo del período, acta de empalme | Fechas de asignación disponibles; empalme y reglas versionadas pendientes |
| DIS-01 | pp. 87–90 | Supervisor, presbítero, secretario y tesorero tienen facultades distintas; registrar autorizaciones y consultas previas | Ámbito distrital existente; falta separar determinadas etapas y decisiones |
| LOC-01 | pp. 112–119 | Junta Local, pastor, secretario y tesorero; nominación, calificación, ratificación; actas, registros y archivo | Personas/cargos disponibles; junta formal, ratificación y libros pendientes |
| TES-01 | pp. 90, 95, 112 y 119 | Presupuesto, solicitud, aprobación, pago y comprobante; autorizaciones distintas de quien registra o paga | Módulo financiero no implementado; no habilitar como si existiera |
| SEC-01 | pp. 45, 89–90 y 118–119 | Archivo, correspondencia, bautismos, matrimonios, defunciones y presentación de niños; emisión de certificados | Registro básico de personas; libros y certificados pendientes |
| PAS-01 | pp. 106–108 y 118–120 | Pastor administrador/asistente, nombramiento, entrega y traslado pastoral; coordinación entre distritos | Traslado de membresía no debe utilizarse como sustituto de traslado pastoral |
| OBR-01 | pp. 109–110 y 120–122 | Proyección, formación y conformada; solicitud, evaluación, decisión escrita, posesión y cierre motivado | Obras existentes; clasificación y flujo completo requieren revisar compatibilidad |
| DEP-01 | pp. 91–92, 123, 125–126, 136–138, 144–151 y 162–167 | Departamentos nacionales/distritales/locales, sus oficios, planes, proyectos y reportes | Equipos genéricos disponibles; falta restringir por departamento/equipo |
| RES-01 | pp. 168–169, 177, 189 y 196–197 | Casos reservados, autoridades por instancia, impedimentos, notificaciones y recursos | No implementado; prohibido usar observaciones generales como expediente reservado |

## 3. Parámetros del documento que requieren versión y validación

Ejemplos del manual de 2018: cargos nacionales por tres años (p. 40), distritales por dos (p. 50), junta local por un año (p. 113); quórum de 60% para asamblea plena y 80% para delegataria (p. 37), y 80% para distrital (p. 54). Son valores documentados, **no reglas activadas** en la base actual.

Resolver antes de automatizar: p. 91 menciona tanto inicio tras empalme como inicio de vigencia el 1 de enero siguiente; p. 110 describe al menos dos años como obra en proyección y p. 120 una evaluación mínima de un año (etapas diferentes, no sustituir una por otra); p. 87 menciona apelación en tres meses y pp. 196–197 contienen plazos específicos para reposición y apelación. Los procedimientos y excepciones deben ser validados por la organización antes de fijar un cálculo automático.

Requisitos morales, doctrinales, de idoneidad y decisiones disciplinarias requieren evaluación humana autorizada. El sistema puede registrar una evaluación y su decisión, no inferir conducta, imponer sanciones ni declarar aptitud ministerial. No se recogerán expedientes médicos detallados por defecto: si la organización requiere un soporte, se definirá el mínimo necesario y quién lo consulta.

## 4. Autorización propuesta

Una operación requiere: sesión válida + usuario habilitado + pertenencia a la organización + asignación vigente + acción autorizada + territorio/recurso permitido + condiciones del proceso. La interfaz no es la barrera de seguridad: la RPC debe volver a verificar todo en cada llamada.

| Perfil de referencia | Trabajo principal y pantalla inicial | Límites esenciales |
|---|---|---|
| Presidencia / directivo nacional | Pendientes de decisión, territorio, planes e informes | Nacional solo en su organización; lo colegiado requiere decisión registrada |
| Secretario nacional/distrital | Documentos, actas, registro ministerial, vencimientos | Registrar o expedir no equivale a aprobar una promoción |
| Supervisor distrital | Congregaciones y obras del distrito, nombramientos, autorizaciones | Fuera del distrito requiere el procedimiento y autoridades correspondientes |
| Presbítero distrital | Visitas, seguimiento, encargos, posesiones autorizadas | No hereda traslado o suspensión unilateral del supervisor |
| Pastor local | Su congregación, membresías, equipos, agenda y solicitudes | No accede a otras congregaciones ni se autoasigna facultades nacionales |
| Secretario local | Personas, membresías, actas y libros habilitados | No recibe automáticamente tesorería, disciplina ni administración de permisos |
| Tesorero | Presupuesto, comprobantes, pagos autorizados e informes | No aprueba por sí solo sus pagos ni edita permisos; módulo futuro |
| Fiscal / auditor | Evidencia, informes y observaciones | Lectura/exportación autorizada; no modificar movimientos ni imponer sanciones (p. 49) |
| Director de departamento/equipo | Su equipo, integrantes, actividades y reportes | Restricción por equipo/departamento, además de territorio |
| Participante / delegado | Sus actividades, documentos y acreditaciones | Ser integrante o asistir no concede gestión ni voto automáticamente |
| Administrador técnico | Configuración operativa autorizada | No equivale a autoridad pastoral ni acceso universal a expedientes |

Separar acciones `consultar`, `crear`, `editar`, `solicitar`, `aprobar`, `ejecutar`, `anular`, `exportar` y `auditar` cuando el proceso lo necesite. Estos nombres son diseño; no se han creado códigos nuevos en `permisos`.

### Brechas concretas en el código publicado

- `eclesiastica.gestionar` gobierna todas las categorías de registros del territorio. No asignarlo a un director de música como si solo autorizara su equipo: se requiere alcance de recurso/departamento en backend primero.
- `usuarios.administrar` nacional también concede gestión eclesiástica en el helper actual. Separar administración técnica y autoridad institucional en una migración posterior probada, sin retirar accesos existentes a ciegas.
- `configurar_permisos_cargo_app` protege el cargo vigente del propio actor y restringe administración de usuarios a cargos nacionales, pero el cambio afecta a todos los titulares del cargo. El editor deberá mostrar ese alcance, los permisos añadidos/retirados y confirmación expresa. Revisar control de edición concurrente antes de habilitar modificaciones simultáneas.
- El menú de `MainActivity` es fijo. Ocultar módulos por nombre del cargo sería incorrecto: debe consumir capacidades efectivas del backend, sin devolver recuentos no autorizados.
- No se encontró `organizacion_id` ni equivalente de aislamiento entre organizaciones en los SQL versionados revisados. No incorporar una segunda organización al mismo conjunto de datos hasta demostrar aislamiento. Elegir y verificar una instancia separada por organización o una migración multi-organización completa; un filtro visual no basta.
- Los campos de texto libre de participantes (`rol`) no son autorizaciones. Un valor «presidente» escrito en ellos jamás debe otorgar permisos.

## 5. Flujos que se conservarán o ampliarán

**Membresía:** conservar `trasladar_persona_app` como única operación transaccional, origen esperado y unicidad de activa. No dividirlo en finalizar y crear desde Android.

**Traslado pastoral:** solicitud → revisión de autoridades correspondientes → aprobación/consulta documentada → acta de entrega y posesión → ejecución atómica del nombramiento. Separado del traslado congregacional de la persona; no encadenar escrituras independientes que dejen nombramientos incoherentes.

**Credenciales:** solicitud → revisión documental → evaluación humana → aprobación competente → emisión → renovación/suspensión/retiro. La credencial y la identificación vigente deben evaluarse juntas, conservando documentos históricos y motivo.

**Junta y oficios:** propuesta → evaluación/ratificación según reglamento → acta → inicio/vigencia → empalme → finalización. La suplencia conserva la fecha de término que corresponda; no reinicia un período sin acto autorizado.

**Asambleas:** convocatoria → padrón por versión de reglas → acreditación → asistencia → determinación de quórum → deliberación/votación si está habilitada → acta y cierre. La asistencia actual no implementa automáticamente sufragio, secreto del voto o elegibilidad.

**Obras:** propuesta y soporte → evaluación distrital → decisión escrita → responsable autorizado → seguimiento → cambio de categoría o cierre motivado. Una observación en texto no equivale al documento de aprobación.

**Finanzas y expedientes:** desarrollar después del núcleo de cargos/ámbitos/actas. La doble aprobación, las partes autorizadas y la reserva deben estar en backend antes de abrir formularios.

## 6. Experiencia por perfil (criterios de implementación)

La apariencia final se trabajará después de consolidar funciones y permisos, como solicita el usuario. Desde ahora los componentes deben admitir:

- Encabezado con organización, cargo(s) y territorio activo; selector explícito cuando existan varios ámbitos. Nunca mezclar totales de organizaciones.
- Inicio con pendientes y entre tres y cinco accesos frecuentes; navegación por Inicio, Gestión, Agenda y Mi perfil cuando esos destinos estén disponibles.
- Módulos sin autorización ocultos; acciones no permitidas omitidas. Ante sesión o permisos no verificables, no mostrar el menú completo como alternativa.
- Mostrar solo funciones implementadas. Los módulos futuros no aparecerán como botones operativos vacíos.
- Un perfil con varias responsabilidades ve la combinación autorizada en el contexto elegido, sin ampliar el territorio de una acción por tener otro cargo.
- Aviso de consulta únicamente cuando corresponda; formularios distintos para editar, solicitar aprobación y aprobar.
- Confirmación que explique persona/recurso, territorio, consecuencia y autoridad requerida; historial con fecha, actor y motivo legibles.
- Revocación y expiración de asignación reflejadas al volver a la pantalla y antes de cada escritura; manejo seguro de enlaces directos, pantalla antigua, exportación y caché.
- Identidad sobria configurable por iglesia, iconos consistentes, tipografía legible, espaciado en dp, objetivos táctiles de al menos 48 dp, etiquetas para lectores de pantalla, soporte de texto ampliado y buen contraste. No depender exclusivamente del color para estados.
- Estados de carga, vacío y error; conservar borradores cuando sea seguro, sin reintentar automáticamente operaciones de resultado incierto. Diseño adaptable a teléfonos y tabletas.

## 7. Orden actualizado de bloques

COG-002A: este análisis trazable y sus criterios de aceptación, sin cambios de aplicación o producción.
COG-003: editor de permisos existente, confirmación de alcance y pruebas; verificar en vivo las RPC al restablecerse el acceso. No cargar roles del manual ni conceder permisos automáticamente.
COG-004: aislamiento por organización y alcance por departamento/recurso; diseño de capacidades efectivas y migraciones compatibles tras inspección del backend.
COG-005: cargos/oficios, credenciales, nombramientos y empalme; decisiones colegiadas y delegación.
COG-006: actas, libros, asambleas y flujos de obras; conservar funcionalidades ya construidas.
COG-007: interfaz final por perfil y pruebas de accesibilidad, navegación y dispositivos; módulos financieros/reservados en entregas separadas posteriores con alcance validado.

La numeración es una secuencia propuesta revisable: no declara esas funcionalidades implementadas ni fechas de entrega. Cada bloque se prueba, se entrega y se guarda en GitHub antes del siguiente.

## 8. Criterios verificables antes de aceptar los bloques

| Caso | Resultado requerido |
|---|---|
| Usuario de organización A consulta un identificador válido de B | Rechazo backend; no existencia, nombre ni totales filtrados por error |
| Director musical administra un registro de otro departamento en su territorio | Rechazo aunque ambos registros sean locales |
| Secretario con consulta intenta editar o exportar sin permiso específico | Rechazo; botones coherentes con la respuesta efectiva |
| Administrador cambia permisos de su cargo vigente | Rechazo backend; no depender de casillas deshabilitadas |
| Dos administradores editan simultáneamente el mismo cargo | Detectar versión desactualizada, evitar sobrescritura silenciosa |
| Caduca o se revoca una asignación mientras la pantalla está abierta | Nueva operación rechazada; interfaz recarga contexto |
| Una persona tiene más de una responsabilidad permitida | Cada acción utiliza su alcance correcto, sin mezclar territorios |
| Se intenta una segunda asignación de gobierno incompatible | Rechazo conforme a la versión del reglamento; oficios no se bloquean por confundir categorías |
| Usuario figura en una lista de integrantes con rol escrito «administrador» | No adquiere permisos técnicos |
| Asistente de asamblea sin habilitación electoral | Puede registrar presencia si corresponde, pero no votar |
| Auditor consulta una operación y pretende modificarla | Consulta autorizada; modificación rechazada y registro de intento según política |
| Consulta expediente reservado mediante búsqueda global o exportación | No se filtra información; acceso solo a las partes habilitadas del caso |
| Falta respuesta al ejecutar traslado | Actualizar historial; nunca crear automáticamente otro traslado |
| Reanudación tras interrupción de Work | Recuperar rama publicada, SHA, docs y estado real de Supabase; no repetir migraciones |
| Texto ampliado, lector de pantalla y teléfono pequeño | Formularios y navegación utilizables; controles etiquetados y sin recortes |

Estos casos son criterios pendientes de implementación/prueba, no resultados aprobados. Las 51 pruebas de COG-002 continúan siendo la evidencia del bloque anterior; no prueban por sí solas las nuevas propuestas del manual.
