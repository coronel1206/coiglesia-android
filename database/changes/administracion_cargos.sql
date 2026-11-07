CREATE TABLE IF NOT EXISTS public.auditoria_administracion (
id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
usuario_responsable_id uuid NOT NULL REFERENCES public.usuarios(id),
accion text NOT NULL, entidad_id uuid NOT NULL,
datos_anteriores jsonb, datos_nuevos jsonb,
fecha_evento timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.auditoria_administracion ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.auditoria_administracion FROM anon,authenticated;

CREATE OR REPLACE FUNCTION public.listar_administracion_app() RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE r jsonb;
BEGIN
IF NOT public.tiene_permiso_territorial_seguro('usuarios.administrar',NULL,NULL)
THEN RAISE EXCEPTION 'Requiere administración nacional de usuarios' USING ERRCODE='42501'; END IF;
SELECT jsonb_build_object(
'usuarios',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',u.id,'persona_id',u.persona_id,
'nombre',p.nombres || ' ' || p.apellidos,'estado',u.estado) ORDER BY p.apellidos,p.nombres)
FROM public.usuarios u JOIN public.personas p ON p.id=u.persona_id),'[]'::jsonb),
'personas',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',p.id,'nombre',p.nombres || ' ' || p.apellidos) ORDER BY p.apellidos,p.nombres)
FROM public.personas p WHERE p.estado='activo'),'[]'::jsonb),
'cargos',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',c.id,'nombre',c.nombre,'nivel',c.nivel,
'permisos',(SELECT jsonb_agg(p.codigo ORDER BY p.codigo) FROM public.cargos_permisos cp
JOIN public.permisos p ON p.id=cp.permiso_id WHERE cp.cargo_id=c.id)) ORDER BY c.nivel,c.nombre)
FROM public.cargos c WHERE c.activo),'[]'::jsonb),
'distritos',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',id,'nombre',nombre) ORDER BY nombre) FROM public.distritos WHERE estado='activo'),'[]'::jsonb),
'congregaciones',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',c.id,'nombre',c.nombre,'distrito_id',c.distrito_id) ORDER BY c.nombre)
FROM public.congregaciones c JOIN public.distritos d ON d.id=c.distrito_id WHERE c.estado='activa' AND d.estado='activo'),'[]'::jsonb),
'asignaciones',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',a.id,'persona',p.nombres || ' ' || p.apellidos,
'cargo',c.nombre,'nivel',c.nivel,'distrito',d.nombre,'congregacion',cg.nombre,
'estado',a.estado,'desde',a.fecha_inicio,'hasta',a.fecha_fin) ORDER BY a.fecha_registro DESC)
FROM public.asignaciones_cargos a JOIN public.personas p ON p.id=a.persona_id JOIN public.cargos c ON c.id=a.cargo_id
LEFT JOIN public.distritos d ON d.id=a.distrito_id LEFT JOIN public.congregaciones cg ON cg.id=a.congregacion_id),'[]'::jsonb))
INTO r; RETURN r;
END $$;

CREATE OR REPLACE FUNCTION public.asignar_cargo_app(p_persona_id uuid,p_cargo_id uuid,p_distrito_id uuid,
p_congregacion_id uuid,p_fecha_inicio date,p_fecha_fin date,p_observaciones text DEFAULT NULL)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE nivel text; nueva uuid;
BEGIN
IF NOT public.tiene_permiso_territorial_seguro('usuarios.administrar',NULL,NULL)
THEN RAISE EXCEPTION 'Requiere administración nacional de usuarios' USING ERRCODE='42501'; END IF;
IF p_fecha_inicio IS NULL OR (p_fecha_fin IS NOT NULL AND p_fecha_fin<p_fecha_inicio)
OR length(COALESCE(p_observaciones,''))>2000 THEN RAISE EXCEPTION 'Revise fechas y observaciones'; END IF;
PERFORM 1 FROM public.personas WHERE id=p_persona_id AND estado='activo' FOR UPDATE;
IF NOT FOUND THEN RAISE EXCEPTION 'Persona activa no encontrada'; END IF;
SELECT c.nivel INTO nivel FROM public.cargos c WHERE c.id=p_cargo_id AND c.activo;
IF nivel IS NULL THEN RAISE EXCEPTION 'Cargo activo no encontrado'; END IF;
IF (nivel='nacional' AND (p_distrito_id IS NOT NULL OR p_congregacion_id IS NOT NULL))
OR (nivel='distrital' AND (p_distrito_id IS NULL OR p_congregacion_id IS NOT NULL))
OR (nivel='local' AND (p_congregacion_id IS NULL OR p_distrito_id IS NOT NULL))
THEN RAISE EXCEPTION 'El territorio no corresponde al nivel del cargo'; END IF;
IF p_distrito_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.distritos WHERE id=p_distrito_id AND estado='activo')
THEN RAISE EXCEPTION 'Distrito inactivo o inexistente'; END IF;
IF p_congregacion_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.congregaciones c JOIN public.distritos d ON d.id=c.distrito_id
WHERE c.id=p_congregacion_id AND c.estado='activa' AND d.estado='activo')
THEN RAISE EXCEPTION 'Congregación inactiva o inexistente'; END IF;
IF EXISTS (SELECT 1 FROM public.asignaciones_cargos a WHERE a.persona_id=p_persona_id AND a.cargo_id=p_cargo_id
AND a.estado IN ('activo','suspendido') AND a.distrito_id IS NOT DISTINCT FROM p_distrito_id
AND a.congregacion_id IS NOT DISTINCT FROM p_congregacion_id
AND daterange(a.fecha_inicio,COALESCE(a.fecha_fin,'infinity'::date),'[]') &&
daterange(p_fecha_inicio,COALESCE(p_fecha_fin,'infinity'::date),'[]'))
THEN RAISE EXCEPTION 'Ya existe una asignación vigente o superpuesta de este cargo'; END IF;
INSERT INTO public.asignaciones_cargos(persona_id,cargo_id,distrito_id,congregacion_id,fecha_inicio,fecha_fin,observaciones)
VALUES(p_persona_id,p_cargo_id,p_distrito_id,p_congregacion_id,p_fecha_inicio,p_fecha_fin,NULLIF(btrim(p_observaciones),''))
RETURNING id INTO nueva;
INSERT INTO public.auditoria_administracion(usuario_responsable_id,accion,entidad_id,datos_nuevos)
SELECT auth.uid(),'asignar_cargo',a.id,to_jsonb(a) FROM public.asignaciones_cargos a WHERE a.id=nueva;
RETURN nueva;
END $$;

CREATE OR REPLACE FUNCTION public.finalizar_cargo_app(p_asignacion_id uuid,p_fecha_fin date) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE anterior public.asignaciones_cargos;
BEGIN
IF NOT public.tiene_permiso_territorial_seguro('usuarios.administrar',NULL,NULL)
THEN RAISE EXCEPTION 'Requiere administración nacional de usuarios' USING ERRCODE='42501'; END IF;
SELECT * INTO anterior FROM public.asignaciones_cargos WHERE id=p_asignacion_id FOR UPDATE;
IF anterior.id IS NULL OR anterior.estado='finalizado' THEN RAISE EXCEPTION 'Asignación no disponible'; END IF;
IF EXISTS(SELECT 1 FROM public.usuarios WHERE id=auth.uid() AND persona_id=anterior.persona_id)
THEN RAISE EXCEPTION 'Otro administrador debe finalizar sus propios cargos'; END IF;
IF p_fecha_fin IS NULL OR p_fecha_fin<anterior.fecha_inicio OR p_fecha_fin>CURRENT_DATE
THEN RAISE EXCEPTION 'Fecha final inválida'; END IF;
UPDATE public.asignaciones_cargos SET estado='finalizado',fecha_fin=p_fecha_fin WHERE id=p_asignacion_id;
INSERT INTO public.auditoria_administracion(usuario_responsable_id,accion,entidad_id,datos_anteriores,datos_nuevos)
SELECT auth.uid(),'finalizar_cargo',a.id,to_jsonb(anterior),to_jsonb(a) FROM public.asignaciones_cargos a WHERE a.id=p_asignacion_id;
RETURN p_asignacion_id;
END $$;

CREATE OR REPLACE FUNCTION public.cambiar_estado_usuario_app(p_usuario_id uuid,p_estado text) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE anterior public.usuarios;
BEGIN
IF NOT public.tiene_permiso_territorial_seguro('usuarios.administrar',NULL,NULL)
THEN RAISE EXCEPTION 'Requiere administración nacional de usuarios' USING ERRCODE='42501'; END IF;
IF p_usuario_id=auth.uid() THEN RAISE EXCEPTION 'No puede modificar su propio estado'; END IF;
IF p_estado IS NULL OR p_estado NOT IN ('activo','suspendido','bloqueado','pendiente') THEN RAISE EXCEPTION 'Estado inválido'; END IF;
SELECT * INTO anterior FROM public.usuarios WHERE id=p_usuario_id FOR UPDATE;
IF anterior.id IS NULL THEN RAISE EXCEPTION 'Usuario no encontrado'; END IF;
UPDATE public.usuarios SET estado=p_estado WHERE id=p_usuario_id;
INSERT INTO public.auditoria_administracion(usuario_responsable_id,accion,entidad_id,datos_anteriores,datos_nuevos)
SELECT auth.uid(),'estado_usuario',u.id,to_jsonb(anterior),to_jsonb(u) FROM public.usuarios u WHERE u.id=p_usuario_id;
RETURN p_usuario_id;
END $$;
REVOKE ALL ON FUNCTION public.listar_administracion_app() FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.asignar_cargo_app(uuid,uuid,uuid,uuid,date,date,text) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.finalizar_cargo_app(uuid,date) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.cambiar_estado_usuario_app(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.listar_administracion_app() TO authenticated;
GRANT EXECUTE ON FUNCTION public.asignar_cargo_app(uuid,uuid,uuid,uuid,date,date,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.finalizar_cargo_app(uuid,date) TO authenticated;
GRANT EXECUTE ON FUNCTION public.cambiar_estado_usuario_app(uuid,text) TO authenticated;

