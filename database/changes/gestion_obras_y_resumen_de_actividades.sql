-- Migración ya aplicada: 20261107001140. Recuperada del historial de Supabase; no volver a aplicar.
CREATE FUNCTION public.listar_congregaciones_obras_app(p_distrito_id uuid) RETURNS SETOF public.congregaciones
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
SELECT c.* FROM public.congregaciones c WHERE c.distrito_id=p_distrito_id AND c.estado='activa'
AND (public.tiene_permiso_territorial_seguro('obras.consultar',c.distrito_id,c.id)
OR public.tiene_permiso_territorial_seguro('obras.crear',c.distrito_id,c.id)
OR public.tiene_permiso_territorial_seguro('obras.asumir',c.distrito_id,NULL)) ORDER BY c.nombre;
$$;
CREATE FUNCTION public.listar_distritos_obras_app() RETURNS SETOF public.distritos
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
SELECT d.* FROM public.distritos d WHERE d.estado='activo'
AND EXISTS(SELECT 1 FROM public.listar_congregaciones_obras_app(d.id)) ORDER BY d.nombre;
$$;
CREATE FUNCTION public.listar_obras_gestion_app(p_congregacion_id uuid) RETURNS SETOF public.obras_proyeccion
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
SELECT o.* FROM public.obras_proyeccion o JOIN public.congregaciones c ON c.id=o.congregacion_origen_id
WHERE c.id=p_congregacion_id AND (public.tiene_permiso_territorial_seguro('obras.consultar',c.distrito_id,c.id)
OR public.tiene_permiso_territorial_seguro('obras.crear',c.distrito_id,c.id)
OR public.tiene_permiso_territorial_seguro('obras.asumir',c.distrito_id,NULL)) ORDER BY o.nombre;
$$;
CREATE FUNCTION public.actualizar_obra_app(p_id uuid,p_codigo text,p_nombre text,p_municipio text,p_departamento text,p_direccion text,p_estado text)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE anterior public.obras_proyeccion; distrito uuid;
BEGIN
SELECT * INTO anterior FROM public.obras_proyeccion WHERE id=p_id FOR UPDATE;
SELECT distrito_id INTO distrito FROM public.congregaciones WHERE id=anterior.congregacion_origen_id;
IF anterior.id IS NULL OR NOT (public.tiene_permiso_territorial_seguro('obras.crear',distrito,anterior.congregacion_origen_id)
OR public.tiene_permiso_territorial_seguro('obras.asumir',distrito,NULL))
THEN RAISE EXCEPTION 'No tiene permiso para editar esta obra' USING ERRCODE='42501'; END IF;
IF NULLIF(btrim(p_codigo),'') IS NULL OR length(btrim(p_codigo))>30 OR NULLIF(btrim(p_nombre),'') IS NULL OR length(btrim(p_nombre))>180
OR length(COALESCE(p_municipio,''))>120 OR length(COALESCE(p_departamento,''))>120 OR length(COALESCE(p_direccion,''))>4000
OR p_estado IS NULL THEN RAISE EXCEPTION 'Revise código, nombre y longitudes'; END IF;
IF p_estado<>anterior.estado THEN
IF NOT public.tiene_permiso_territorial_seguro('obras.asumir',distrito,NULL)
OR NOT ((p_estado='cerrada' AND anterior.estado<>'constituida') OR (p_estado='activa' AND anterior.estado='cerrada'))
THEN RAISE EXCEPTION 'Este cambio de estado requiere un proceso administrativo autorizado'; END IF;
IF p_estado='cerrada' AND EXISTS(SELECT 1 FROM public.nombramientos_pastorales_obras WHERE obra_id=p_id AND estado='activo')
THEN RAISE EXCEPTION 'Finalice el nombramiento pastoral antes de cerrar la obra'; END IF;
END IF;
UPDATE public.obras_proyeccion SET codigo=upper(btrim(p_codigo)),nombre=btrim(p_nombre),municipio=NULLIF(btrim(p_municipio),''),
departamento=NULLIF(btrim(p_departamento),''),direccion_referencia=NULLIF(btrim(p_direccion),''),estado=p_estado WHERE id=p_id;
INSERT INTO public.auditoria_administracion(usuario_responsable_id,accion,entidad_id,datos_anteriores,datos_nuevos)
SELECT auth.uid(),'editar_obra',id,to_jsonb(anterior),to_jsonb(o) FROM public.obras_proyeccion o WHERE id=p_id;
RETURN p_id;
END $$;
REVOKE ALL ON FUNCTION public.listar_congregaciones_obras_app(uuid),public.listar_distritos_obras_app(),public.listar_obras_gestion_app(uuid),
public.actualizar_obra_app(uuid,text,text,text,text,text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.listar_congregaciones_obras_app(uuid),public.listar_distritos_obras_app(),public.listar_obras_gestion_app(uuid),
public.actualizar_obra_app(uuid,text,text,text,text,text,text) TO authenticated;
CREATE OR REPLACE FUNCTION public.resumen_territorial_app()
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
'eventos',(SELECT count(*) FROM public.registros_eclesiasticos r WHERE r.tipo='evento' AND r.estado IN ('activo','realizado')
AND public.puede_eclesiastica_app(false,r.distrito_id,r.congregacion_id)),
'asambleas',(SELECT count(*) FROM public.registros_eclesiasticos r WHERE r.tipo='asamblea' AND r.estado IN ('activo','realizado')
AND public.puede_eclesiastica_app(false,r.distrito_id,r.congregacion_id)),
'asistencias',(SELECT count(*) FROM public.participaciones_eclesiasticas p JOIN public.registros_eclesiasticos r ON r.id=p.registro_id
WHERE r.tipo IN ('evento','asamblea') AND r.estado IN ('activo','realizado') AND p.activo AND p.presente
AND public.puede_eclesiastica_app(false,r.distrito_id,r.congregacion_id)),
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
END $function$
;

