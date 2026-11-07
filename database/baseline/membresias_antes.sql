CREATE OR REPLACE FUNCTION public.finalizar_membresia_app(p_membresia_id uuid, p_fecha_salida date DEFAULT CURRENT_DATE, p_observaciones text DEFAULT NULL::text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
v_persona_id uuid;
v_congregacion_id uuid;
v_distrito_id uuid;
v_fecha_ingreso date;
v_fecha_salida date;
BEGIN
IF p_membresia_id IS NULL THEN
RAISE EXCEPTION
'Debe indicar la membresia';
END IF;

SELECT
m.persona_id,
m.congregacion_id,
c.distrito_id,
m.fecha_ingreso
INTO
v_persona_id,
v_congregacion_id,
v_distrito_id,
v_fecha_ingreso
FROM public.membresias_congregacionales m
JOIN public.personas p
ON p.id = m.persona_id
JOIN public.congregaciones c
ON c.id = m.congregacion_id
JOIN public.distritos d
ON d.id = c.distrito_id
WHERE m.id = p_membresia_id
AND m.estado = 'activa'
AND p.estado = 'activo'
AND c.estado = 'activa'
AND d.estado = 'activo'
FOR UPDATE OF m;

IF v_persona_id IS NULL THEN
RAISE EXCEPTION
'Membresia activa no encontrada';
END IF;

IF NOT public.tiene_permiso_territorial_seguro(
'membresias.finalizar',
v_distrito_id,
v_congregacion_id
) THEN
RAISE EXCEPTION
'No tiene permiso para finalizar esta membresia';
END IF;

v_fecha_salida := COALESCE(
p_fecha_salida,
CURRENT_DATE
);

IF v_fecha_salida < v_fecha_ingreso THEN
RAISE EXCEPTION
'La fecha de salida no puede ser anterior al ingreso';
END IF;

IF v_fecha_salida > CURRENT_DATE THEN
RAISE EXCEPTION
'No se permite una fecha de salida futura';
END IF;

IF length(COALESCE(p_observaciones, '')) > 2000 THEN
RAISE EXCEPTION
'Las observaciones superan los 2000 caracteres';
END IF;

UPDATE public.membresias_congregacionales
SET
estado = 'finalizada',
fecha_salida = v_fecha_salida,
observaciones = CASE
WHEN NULLIF(btrim(p_observaciones), '') IS NULL
THEN observaciones
ELSE btrim(p_observaciones)
END
WHERE id = p_membresia_id
AND estado = 'activa';

IF NOT FOUND THEN
RAISE EXCEPTION
'No fue posible finalizar la membresia';
END IF;

RETURN p_membresia_id;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.registrar_auditoria_membresia()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
v_usuario_id uuid;
v_accion varchar(30);
v_origen uuid;
v_destino uuid;
BEGIN
-- Identificar al usuario autenticado
SELECT u.id
INTO v_usuario_id
FROM public.usuarios u
WHERE u.id = (SELECT auth.uid())
AND u.estado = 'activo';

IF v_usuario_id IS NULL THEN
RAISE EXCEPTION
'No se pudo identificar al usuario responsable de la operación';
END IF;

IF TG_OP = 'INSERT' THEN
v_accion := 'registro';
v_origen := NULL;
v_destino := NEW.congregacion_id;

INSERT INTO public.auditoria_membresias (
persona_id,
membresia_id,
congregacion_origen_id,
congregacion_destino_id,
accion,
datos_anteriores,
datos_nuevos,
observaciones,
usuario_responsable_id
)
VALUES (
NEW.persona_id,
NEW.id,
v_origen,
v_destino,
v_accion,
NULL,
to_jsonb(NEW),
NEW.observaciones,
v_usuario_id
);

RETURN NEW;

ELSIF TG_OP = 'UPDATE'
AND OLD.estado = 'activa'
AND NEW.estado = 'finalizada' THEN

v_accion := 'finalizacion';
v_origen := OLD.congregacion_id;
v_destino := NULL;

INSERT INTO public.auditoria_membresias (
persona_id,
membresia_id,
congregacion_origen_id,
congregacion_destino_id,
accion,
datos_anteriores,
datos_nuevos,
observaciones,
usuario_responsable_id
)
VALUES (
NEW.persona_id,
NEW.id,
v_origen,
v_destino,
v_accion,
to_jsonb(OLD),
to_jsonb(NEW),
NEW.observaciones,
v_usuario_id
);

RETURN NEW;
END IF;

RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.registrar_membresia_app(p_persona_id uuid, p_congregacion_id uuid, p_fecha_ingreso date DEFAULT CURRENT_DATE, p_observaciones text DEFAULT NULL::text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
v_distrito_id uuid;
v_membresia_id uuid;
BEGIN

IF p_persona_id IS NULL OR p_congregacion_id IS NULL THEN
RAISE EXCEPTION 'Debe indicar persona y congregación';
END IF;

IF p_fecha_ingreso IS NULL THEN
RAISE EXCEPTION 'La fecha de ingreso es obligatoria';
END IF;

PERFORM 1
FROM public.personas p
WHERE p.id = p_persona_id
AND p.estado = 'activo'
FOR UPDATE;

IF NOT FOUND THEN
RAISE EXCEPTION 'Persona inexistente o inactiva';
END IF;

SELECT c.distrito_id
INTO v_distrito_id
FROM public.congregaciones c
JOIN public.distritos d
ON d.id = c.distrito_id
WHERE c.id = p_congregacion_id
AND c.estado = 'activa'
AND d.estado = 'activo';

IF v_distrito_id IS NULL THEN
RAISE EXCEPTION 'Congregación inexistente o inactiva';
END IF;

IF NOT public.tiene_permiso_territorial_seguro(
'membresias.crear',
v_distrito_id,
p_congregacion_id
) THEN
RAISE EXCEPTION
'No tiene permiso para registrar membresías en esta congregación';
END IF;

IF EXISTS (
SELECT 1
FROM public.membresias_congregacionales m
WHERE m.persona_id = p_persona_id
AND m.estado = 'activa'
) THEN
RAISE EXCEPTION
'La persona ya tiene una membresía activa. Finalícela antes de registrar otra';
END IF;

INSERT INTO public.membresias_congregacionales (
persona_id,
congregacion_id,
fecha_ingreso,
observaciones
)
VALUES (
p_persona_id,
p_congregacion_id,
p_fecha_ingreso,
NULLIF(btrim(p_observaciones), '')
)
RETURNING id INTO v_membresia_id;

RETURN v_membresia_id;

END;
$function$
;

CREATE OR REPLACE FUNCTION public.tiene_permiso_territorial(p_codigo_permiso text, p_distrito_id uuid DEFAULT NULL::uuid, p_congregacion_id uuid DEFAULT NULL::uuid)
RETURNS boolean
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path TO ''
AS $function$
SELECT
(
p_distrito_id IS NULL
OR p_congregacion_id IS NULL
OR EXISTS (
SELECT 1
FROM public.congregaciones cg
WHERE cg.id = p_congregacion_id
AND cg.distrito_id = p_distrito_id
)
)
AND EXISTS (
SELECT 1
FROM public.usuarios u
JOIN public.asignaciones_cargos ac
ON ac.persona_id = u.persona_id
JOIN public.cargos c
ON c.id = ac.cargo_id
JOIN public.cargos_permisos cp
ON cp.cargo_id = c.id
JOIN public.permisos p
ON p.id = cp.permiso_id
WHERE u.id = auth.uid()
AND u.estado = 'activo'
AND ac.estado = 'activo'
AND ac.fecha_inicio <= CURRENT_DATE
AND (
ac.fecha_fin IS NULL
OR ac.fecha_fin >= CURRENT_DATE
)
AND c.activo = TRUE
AND p.codigo = p_codigo_permiso
AND (
(
c.nivel = 'nacional'
AND ac.distrito_id IS NULL
AND ac.congregacion_id IS NULL
)
OR
(
c.nivel = 'distrital'
AND p_distrito_id IS NOT NULL
AND ac.distrito_id = p_distrito_id
AND ac.congregacion_id IS NULL
)
OR
(
c.nivel = 'local'
AND p_congregacion_id IS NOT NULL
AND ac.congregacion_id = p_congregacion_id
AND ac.distrito_id IS NULL
)
)
);
$function$
;

CREATE OR REPLACE FUNCTION public.tiene_permiso_territorial_seguro(p_codigo_permiso text, p_distrito_id uuid DEFAULT NULL::uuid, p_congregacion_id uuid DEFAULT NULL::uuid)
RETURNS boolean
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path TO ''
AS $function$
SELECT
public.tiene_permiso_territorial(
p_codigo_permiso,
p_distrito_id,
p_congregacion_id
)

AND EXISTS (
SELECT 1
FROM public.usuarios u
JOIN public.personas p
ON p.id = u.persona_id
WHERE u.id = (SELECT auth.uid())
AND u.estado = 'activo'
AND p.estado = 'activo'
)

AND (
p_distrito_id IS NULL
OR EXISTS (
SELECT 1
FROM public.distritos d
WHERE d.id = p_distrito_id
AND d.estado = 'activo'
)
)

AND (
p_congregacion_id IS NULL
OR EXISTS (
SELECT 1
FROM public.congregaciones c
JOIN public.distritos d
ON d.id = c.distrito_id
WHERE c.id = p_congregacion_id
AND c.estado = 'activa'
AND d.estado = 'activo'
AND (
p_distrito_id IS NULL
OR d.id = p_distrito_id
)
)
);
$function$
;

CREATE OR REPLACE FUNCTION public.trasladar_persona_app(p_persona_id uuid, p_congregacion_destino_id uuid, p_fecha_traslado date DEFAULT CURRENT_DATE, p_observaciones text DEFAULT NULL::text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
v_membresia_origen_id uuid;
v_congregacion_origen_id uuid;
v_distrito_origen_id uuid;
v_distrito_destino_id uuid;
v_nueva_membresia_id uuid;
BEGIN

-- Validaciones iniciales
IF p_persona_id IS NULL
OR p_congregacion_destino_id IS NULL THEN
RAISE EXCEPTION
'Debe indicar persona y congregación de destino';
END IF;

IF p_fecha_traslado IS NULL
OR p_fecha_traslado > CURRENT_DATE THEN
RAISE EXCEPTION
'La fecha de traslado no es válida';
END IF;

IF length(COALESCE(p_observaciones, '')) > 2000 THEN
RAISE EXCEPTION
'Las observaciones superan los 2000 caracteres';
END IF;

-- Verificar usuario autenticado
IF (SELECT auth.uid()) IS NULL THEN
RAISE EXCEPTION
'No existe una sesión autenticada';
END IF;

-- Bloquear y localizar membresía activa
SELECT
m.id,
m.congregacion_id,
c.distrito_id
INTO
v_membresia_origen_id,
v_congregacion_origen_id,
v_distrito_origen_id
FROM public.membresias_congregacionales m
JOIN public.personas p
ON p.id = m.persona_id
JOIN public.congregaciones c
ON c.id = m.congregacion_id
JOIN public.distritos d
ON d.id = c.distrito_id
WHERE m.persona_id = p_persona_id
AND m.estado = 'activa'
AND p.estado = 'activo'
AND c.estado = 'activa'
AND d.estado = 'activo'
FOR UPDATE OF m;

IF v_membresia_origen_id IS NULL THEN
RAISE EXCEPTION
'La persona no tiene una membresía activa válida';
END IF;

-- Evitar traslado a la misma congregación
IF v_congregacion_origen_id =
p_congregacion_destino_id THEN
RAISE EXCEPTION
'La persona ya pertenece a esta congregación';
END IF;

-- Verificar destino y obtener su distrito
SELECT c.distrito_id
INTO v_distrito_destino_id
FROM public.congregaciones c
JOIN public.distritos d
ON d.id = c.distrito_id
WHERE c.id = p_congregacion_destino_id
AND c.estado = 'activa'
AND d.estado = 'activo';

IF v_distrito_destino_id IS NULL THEN
RAISE EXCEPTION
'La congregación de destino no existe o está inactiva';
END IF;

-- Verificar permiso en origen
IF NOT COALESCE(
public.tiene_permiso_territorial_seguro(
'membresias.finalizar',
v_distrito_origen_id,
v_congregacion_origen_id
),
false
) THEN
RAISE EXCEPTION
'No tiene permiso para trasladar desde la congregación de origen';
END IF;

-- Verificar permiso en destino
IF NOT COALESCE(
public.tiene_permiso_territorial_seguro(
'membresias.crear',
v_distrito_destino_id,
p_congregacion_destino_id
),
false
) THEN
RAISE EXCEPTION
'No tiene permiso para registrar en la congregación de destino';
END IF;

-- Finalizar membresía anterior
PERFORM public.finalizar_membresia_app(
v_membresia_origen_id,
p_fecha_traslado,
p_observaciones
);

-- Crear nueva membresía
v_nueva_membresia_id :=
public.registrar_membresia_app(
p_persona_id,
p_congregacion_destino_id,
p_fecha_traslado,
p_observaciones
);

RETURN v_nueva_membresia_id;

END;
$function$
;
