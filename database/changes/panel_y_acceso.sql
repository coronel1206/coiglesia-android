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

INSERT INTO public.auditoria_membresias
(persona_id,membresia_id,congregacion_origen_id,congregacion_destino_id,accion,
datos_anteriores,datos_nuevos,observaciones,usuario_responsable_id)
VALUES (p_persona_id,v_nueva_membresia_id,v_congregacion_origen_id,p_congregacion_destino_id,'traslado',
jsonb_build_object('membresia_origen_id',v_membresia_origen_id),
jsonb_build_object('membresia_destino_id',v_nueva_membresia_id,'fecha_traslado',p_fecha_traslado),
NULLIF(btrim(p_observaciones),''),auth.uid());
RETURN v_nueva_membresia_id;

END;
$function$
;

CREATE OR REPLACE FUNCTION public.mi_acceso_app() RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE v_result jsonb;
BEGIN
IF NOT public.tiene_acceso_app() THEN RAISE EXCEPTION 'Acceso no autorizado' USING ERRCODE='42501'; END IF;
SELECT jsonb_build_object('nombre',p.nombres || ' ' || p.apellidos,'estado',u.estado,
'cargos',COALESCE((SELECT jsonb_agg(jsonb_build_object('cargo',c.nombre,'nivel',c.nivel,
'distrito',d.nombre,'congregacion',cg.nombre,'desde',a.fecha_inicio,'hasta',a.fecha_fin,
'permisos',(SELECT jsonb_agg(pm.codigo ORDER BY pm.codigo) FROM public.cargos_permisos cp
JOIN public.permisos pm ON pm.id=cp.permiso_id WHERE cp.cargo_id=c.id)))
FROM public.asignaciones_cargos a JOIN public.cargos c ON c.id=a.cargo_id
LEFT JOIN public.distritos d ON d.id=a.distrito_id
LEFT JOIN public.congregaciones cg ON cg.id=a.congregacion_id
WHERE a.persona_id=p.id AND a.estado='activo' AND c.activo AND a.fecha_inicio<=CURRENT_DATE
AND (a.fecha_fin IS NULL OR a.fecha_fin>=CURRENT_DATE)
AND ((c.nivel='nacional' AND a.distrito_id IS NULL AND a.congregacion_id IS NULL)
OR (c.nivel='distrital' AND a.congregacion_id IS NULL AND d.estado='activo')
OR (c.nivel='local' AND a.distrito_id IS NULL AND cg.estado='activa'
AND EXISTS(SELECT 1 FROM public.distritos dd WHERE dd.id=cg.distrito_id AND dd.estado='activo')))), '[]'::jsonb))
INTO v_result FROM public.usuarios u JOIN public.personas p ON p.id=u.persona_id WHERE u.id=auth.uid();
RETURN v_result;
END $$;
CREATE OR REPLACE FUNCTION public.resumen_territorial_app() RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE v_result jsonb;
BEGIN
IF NOT public.tiene_acceso_app() THEN RAISE EXCEPTION 'Acceso no autorizado' USING ERRCODE='42501'; END IF;
WITH territorios AS (
SELECT c.id,c.nombre,c.distrito_id,d.nombre AS distrito,
public.tiene_permiso_territorial_seguro('personas.consultar',d.id,c.id) AS personas,
public.tiene_permiso_territorial_seguro('membresias.consultar',d.id,c.id) AS membresias
FROM public.congregaciones c JOIN public.distritos d ON d.id=c.distrito_id
WHERE c.estado='activa' AND d.estado='activo'
), visibles AS (SELECT * FROM territorios WHERE personas OR membresias),
movimientos AS (SELECT m.* FROM public.membresias_congregacionales m
JOIN visibles v ON v.id=m.congregacion_id WHERE v.membresias)
SELECT jsonb_build_object(
'distritos',(SELECT count(DISTINCT distrito_id) FROM visibles),
'congregaciones',(SELECT count(*) FROM visibles),
'personas',(SELECT count(DISTINCT m.persona_id) FROM public.membresias_congregacionales m
JOIN visibles v ON v.id=m.congregacion_id JOIN public.personas p ON p.id=m.persona_id WHERE v.personas AND p.estado='activo'),
'activas',(SELECT count(*) FROM movimientos WHERE estado='activa'),
'finalizadas',(SELECT count(*) FROM movimientos WHERE estado='finalizada'),
'traslados',(SELECT count(*) FROM public.auditoria_membresias a WHERE a.accion='traslado'
AND EXISTS(SELECT 1 FROM visibles v WHERE v.id=a.congregacion_origen_id AND v.membresias)
AND EXISTS(SELECT 1 FROM visibles v WHERE v.id=a.congregacion_destino_id AND v.membresias)),
'por_congregacion',COALESCE((SELECT jsonb_agg(jsonb_build_object('congregacion',v.nombre,'distrito',v.distrito,
'activas',(SELECT count(*) FROM movimientos m WHERE m.congregacion_id=v.id AND m.estado='activa'),
'finalizadas',(SELECT count(*) FROM movimientos m WHERE m.congregacion_id=v.id AND m.estado='finalizada'))
ORDER BY v.distrito,v.nombre) FROM visibles v WHERE v.membresias),'[]'::jsonb),
'movimientos_mensuales',COALESCE((SELECT jsonb_agg(jsonb_build_object('mes',x.mes,'ingresos',x.ingresos,'salidas',x.salidas) ORDER BY x.mes)
FROM (SELECT to_char(mes,'YYYY-MM') AS mes,
(SELECT count(*) FROM movimientos m WHERE m.fecha_ingreso>=mes::date AND m.fecha_ingreso<(mes+interval '1 month')::date) AS ingresos,
(SELECT count(*) FROM movimientos m WHERE m.fecha_salida>=mes::date AND m.fecha_salida<(mes+interval '1 month')::date) AS salidas
FROM generate_series(date_trunc('month',CURRENT_DATE)-interval '5 months',date_trunc('month',CURRENT_DATE),interval '1 month') mes) x),'[]'::jsonb))
INTO v_result;
RETURN v_result;
END $$;
REVOKE ALL ON FUNCTION public.mi_acceso_app() FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.resumen_territorial_app() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.mi_acceso_app() TO authenticated;
GRANT EXECUTE ON FUNCTION public.resumen_territorial_app() TO authenticated;

