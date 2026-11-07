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

IF NOT public.tiene_permiso_territorial_seguro('membresias.crear',NULL,NULL)
AND NOT EXISTS (
SELECT 1 FROM public.membresias_congregacionales mm
JOIN public.congregaciones cc ON cc.id=mm.congregacion_id
WHERE mm.persona_id=p_persona_id
AND public.tiene_permiso_territorial_seguro('membresias.consultar',cc.distrito_id,cc.id)
) THEN
RAISE EXCEPTION 'No tiene permiso para vincular esta persona desde su territorio';
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

IF EXISTS (SELECT 1 FROM public.membresias_congregacionales m
WHERE m.persona_id=p_persona_id AND m.fecha_salida>p_fecha_ingreso) THEN
RAISE EXCEPTION 'El ingreso no puede ser anterior a la última salida';
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
CREATE OR REPLACE FUNCTION public.listar_personas_resumen_app() RETURNS SETOF jsonb
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
SELECT to_jsonb(p) || jsonb_build_object('estado_membresia',
CASE WHEN EXISTS(SELECT 1 FROM public.membresias_congregacionales m
JOIN public.congregaciones c ON c.id=m.congregacion_id
WHERE m.persona_id=p.id AND m.estado='activa'
AND public.tiene_permiso_territorial_seguro('membresias.consultar',c.distrito_id,c.id))
THEN 'activa'
WHEN EXISTS(SELECT 1 FROM public.membresias_congregacionales m
JOIN public.congregaciones c ON c.id=m.congregacion_id
WHERE m.persona_id=p.id
AND public.tiene_permiso_territorial_seguro('membresias.consultar',c.distrito_id,c.id))
THEN 'sin_activa_visible' ELSE 'no_disponible' END)
FROM public.listar_personas_autorizadas_app() p;
$$;
REVOKE ALL ON FUNCTION public.listar_personas_resumen_app() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.listar_personas_resumen_app() TO authenticated;

