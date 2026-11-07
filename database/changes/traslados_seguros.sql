-- Cambios aditivos y endurecimiento, sin borrar datos ni deshabilitar RLS.
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

-- Serializar con altas concurrentes antes de bloquear la membresía.
PERFORM 1 FROM public.personas WHERE id = p_persona_id AND estado = 'activo' FOR UPDATE;
IF NOT FOUND THEN
RAISE EXCEPTION 'Persona inexistente o inactiva';
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
IF auth.uid() IS NULL THEN
RAISE EXCEPTION 'No existe una sesión autenticada';
END IF;
IF p_fecha_ingreso > CURRENT_DATE THEN
RAISE EXCEPTION 'No se permite una fecha de ingreso futura';
END IF;
IF length(COALESCE(p_observaciones, '')) > 2000 THEN
RAISE EXCEPTION 'Las observaciones superan los 2000 caracteres';
END IF;

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
CREATE OR REPLACE FUNCTION public.listar_destinos_membresia_app()
RETURNS TABLE(id uuid, distrito_id uuid, codigo varchar, nombre varchar)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = ''
AS $$
SELECT c.id, c.distrito_id, c.codigo, c.nombre
FROM public.congregaciones c JOIN public.distritos d ON d.id = c.distrito_id
WHERE c.estado = 'activa' AND d.estado = 'activo'
AND public.tiene_permiso_territorial_seguro('membresias.crear', d.id, c.id)
ORDER BY c.nombre;
$$;
CREATE OR REPLACE FUNCTION public.listar_distritos_membresia_app()
RETURNS TABLE(id uuid, codigo varchar, nombre varchar)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = ''
AS $$
SELECT d.id, d.codigo, d.nombre FROM public.distritos d
WHERE d.estado = 'activo' AND EXISTS (
SELECT 1 FROM public.listar_destinos_membresia_app() c WHERE c.distrito_id = d.id
)
ORDER BY d.nombre;
$$;
REVOKE ALL ON FUNCTION public.registrar_auditoria_membresia() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.registrar_membresia_app(uuid,uuid,date,text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.registrar_membresia_app(uuid,uuid,date,text) TO authenticated;
REVOKE ALL ON FUNCTION public.listar_destinos_membresia_app() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.listar_distritos_membresia_app() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.listar_destinos_membresia_app() TO authenticated;
GRANT EXECUTE ON FUNCTION public.listar_distritos_membresia_app() TO authenticated;

