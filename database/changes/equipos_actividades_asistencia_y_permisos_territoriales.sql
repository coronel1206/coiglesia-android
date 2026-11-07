-- Migración ya aplicada: 20261107000349. Recuperada del historial de Supabase; no volver a aplicar.
CREATE TABLE public.registros_eclesiasticos (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 tipo text NOT NULL CHECK(tipo IN ('ministerio','comite','musica','evento','asamblea')),
 nombre text NOT NULL CHECK(length(btrim(nombre)) BETWEEN 1 AND 180),
 descripcion text CHECK(length(descripcion)<=4000),
 distrito_id uuid REFERENCES public.distritos(id),
 congregacion_id uuid REFERENCES public.congregaciones(id),
 fecha date, lugar text CHECK(length(lugar)<=250),
 estado text NOT NULL DEFAULT 'activo' CHECK(estado IN ('activo','inactivo','realizado','cancelado')),
 creado_por uuid NOT NULL REFERENCES public.usuarios(id),
 creado_en timestamptz NOT NULL DEFAULT now(),
 actualizado_en timestamptz NOT NULL DEFAULT now(),
 CHECK(congregacion_id IS NULL OR distrito_id IS NOT NULL),
 CHECK(tipo NOT IN ('evento','asamblea') OR fecha IS NOT NULL),
 CHECK(tipo IN ('evento','asamblea') OR estado IN ('activo','inactivo'))
);
CREATE TABLE public.participaciones_eclesiasticas (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 registro_id uuid NOT NULL REFERENCES public.registros_eclesiasticos(id),
 persona_id uuid NOT NULL REFERENCES public.personas(id),
 rol text CHECK(length(rol)<=120),
 presente boolean NOT NULL DEFAULT false,
 activo boolean NOT NULL DEFAULT true,
 actualizado_en timestamptz NOT NULL DEFAULT now(),
 UNIQUE(registro_id,persona_id)
);
CREATE INDEX registros_eclesiasticos_territorio ON public.registros_eclesiasticos(distrito_id,congregacion_id,tipo);
CREATE INDEX participaciones_eclesiasticas_persona ON public.participaciones_eclesiasticas(persona_id);
ALTER TABLE public.registros_eclesiasticos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.participaciones_eclesiasticas ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.registros_eclesiasticos,public.participaciones_eclesiasticas FROM anon,authenticated;

INSERT INTO public.permisos(codigo,descripcion) VALUES
('eclesiastica.consultar','Consultar equipos, actividades e integrantes en el territorio autorizado'),
('eclesiastica.gestionar','Crear y editar equipos, actividades, integrantes y asistencia en el territorio autorizado')
ON CONFLICT(codigo) DO NOTHING;

CREATE FUNCTION public.puede_eclesiastica_app(p_gestion boolean,p_distrito_id uuid,p_congregacion_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
SELECT public.tiene_acceso_app() AND
(public.tiene_permiso_territorial_seguro('usuarios.administrar',NULL,NULL)
OR public.tiene_permiso_territorial_seguro('eclesiastica.gestionar',p_distrito_id,p_congregacion_id)
OR (NOT p_gestion AND public.tiene_permiso_territorial_seguro('eclesiastica.consultar',p_distrito_id,p_congregacion_id)));
$$;

CREATE FUNCTION public.listar_eclesiastica_app(p_tipo text) RETURNS jsonb
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
SELECT jsonb_build_object('registros',COALESCE((SELECT jsonb_agg(to_jsonb(x) ORDER BY x.actualizado_en DESC)
FROM (SELECT r.*,d.nombre AS distrito,c.nombre AS congregacion,
public.puede_eclesiastica_app(true,r.distrito_id,r.congregacion_id) AS editable,
(SELECT count(*) FROM public.participaciones_eclesiasticas p WHERE p.registro_id=r.id AND p.activo) AS integrantes,
(SELECT count(*) FROM public.participaciones_eclesiasticas p WHERE p.registro_id=r.id AND p.activo AND p.presente) AS asistentes
FROM public.registros_eclesiasticos r LEFT JOIN public.distritos d ON d.id=r.distrito_id
LEFT JOIN public.congregaciones c ON c.id=r.congregacion_id
WHERE r.tipo=p_tipo AND public.puede_eclesiastica_app(false,r.distrito_id,r.congregacion_id)) x),'[]'::jsonb),
'nacional',public.puede_eclesiastica_app(true,NULL,NULL),
'territorios',COALESCE((SELECT jsonb_agg(to_jsonb(t)) FROM (
SELECT d.id AS distrito_id,d.nombre AS distrito,NULL::uuid AS congregacion_id,NULL::varchar AS congregacion
FROM public.distritos d WHERE d.estado='activo' AND public.puede_eclesiastica_app(true,d.id,NULL)
UNION ALL SELECT d.id,d.nombre,c.id,c.nombre FROM public.congregaciones c JOIN public.distritos d ON d.id=c.distrito_id
WHERE c.estado='activa' AND d.estado='activo' AND public.puede_eclesiastica_app(true,d.id,c.id))t),'[]'::jsonb));
$$;

CREATE FUNCTION public.guardar_eclesiastica_app(p_id uuid,p_tipo text,p_nombre text,p_descripcion text,
p_distrito_id uuid,p_congregacion_id uuid,p_fecha date,p_lugar text,p_estado text) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE anterior public.registros_eclesiasticos; nueva uuid;
BEGIN
IF NOT public.puede_eclesiastica_app(true,p_distrito_id,p_congregacion_id)
THEN RAISE EXCEPTION 'No tiene permiso para gestionar este territorio' USING ERRCODE='42501'; END IF;
IF p_tipo IS NULL OR p_tipo NOT IN ('ministerio','comite','musica','evento','asamblea')
OR NULLIF(btrim(p_nombre),'') IS NULL OR length(btrim(p_nombre))>180
OR length(COALESCE(p_descripcion,''))>4000 OR length(COALESCE(p_lugar,''))>250
OR p_estado IS NULL OR p_estado NOT IN ('activo','inactivo','realizado','cancelado')
OR (p_tipo IN ('evento','asamblea') AND p_fecha IS NULL)
OR (p_tipo NOT IN ('evento','asamblea') AND p_estado NOT IN ('activo','inactivo'))
THEN RAISE EXCEPTION 'Revise nombre, tipo, estado, fecha y longitudes'; END IF;
IF p_distrito_id IS NOT NULL THEN
PERFORM 1 FROM public.distritos WHERE id=p_distrito_id AND estado='activo' FOR SHARE;
IF NOT FOUND THEN RAISE EXCEPTION 'Distrito no disponible'; END IF;
END IF;
IF p_congregacion_id IS NOT NULL THEN
PERFORM 1 FROM public.congregaciones WHERE id=p_congregacion_id AND distrito_id=p_distrito_id AND estado='activa' FOR SHARE;
IF NOT FOUND THEN RAISE EXCEPTION 'Congregación y distrito no coinciden o están inactivos'; END IF;
END IF;
IF p_id IS NOT NULL THEN
SELECT * INTO anterior FROM public.registros_eclesiasticos WHERE id=p_id FOR UPDATE;
IF anterior.id IS NULL THEN RAISE EXCEPTION 'Registro no encontrado'; END IF;
IF NOT public.puede_eclesiastica_app(true,anterior.distrito_id,anterior.congregacion_id)
THEN RAISE EXCEPTION 'No tiene permiso sobre el registro original' USING ERRCODE='42501'; END IF;
IF anterior.tipo<>p_tipo OR anterior.distrito_id IS DISTINCT FROM p_distrito_id
OR anterior.congregacion_id IS DISTINCT FROM p_congregacion_id
THEN RAISE EXCEPTION 'No se permite cambiar tipo o territorio de un registro existente'; END IF;
UPDATE public.registros_eclesiasticos SET nombre=btrim(p_nombre),descripcion=NULLIF(btrim(p_descripcion),''),
fecha=p_fecha,lugar=NULLIF(btrim(p_lugar),''),estado=p_estado,actualizado_en=now() WHERE id=p_id RETURNING id INTO nueva;
ELSE
INSERT INTO public.registros_eclesiasticos(tipo,nombre,descripcion,distrito_id,congregacion_id,fecha,lugar,estado,creado_por)
VALUES(p_tipo,btrim(p_nombre),NULLIF(btrim(p_descripcion),''),p_distrito_id,p_congregacion_id,p_fecha,NULLIF(btrim(p_lugar),''),p_estado,auth.uid())
RETURNING id INTO nueva;
END IF;
INSERT INTO public.auditoria_administracion(usuario_responsable_id,accion,entidad_id,datos_anteriores,datos_nuevos)
SELECT auth.uid(),'guardar_'||p_tipo,id,CASE WHEN anterior.id IS NULL THEN NULL ELSE to_jsonb(anterior) END,to_jsonb(r)
FROM public.registros_eclesiasticos r WHERE id=nueva;
RETURN nueva;
END $$;

CREATE FUNCTION public.persona_elegible_eclesiastica_app(p_persona_id uuid,p_distrito_id uuid,p_congregacion_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
SELECT EXISTS(SELECT 1 FROM public.personas p WHERE p.id=p_persona_id AND p.estado='activo'
AND (public.tiene_permiso_territorial_seguro('personas.consultar',NULL,NULL)
OR EXISTS(SELECT 1 FROM public.membresias_congregacionales m JOIN public.congregaciones c ON c.id=m.congregacion_id
WHERE m.persona_id=p.id AND m.estado='activa'
AND (p_distrito_id IS NULL OR c.distrito_id=p_distrito_id)
AND (p_congregacion_id IS NULL OR c.id=p_congregacion_id)
AND public.tiene_permiso_territorial_seguro('personas.consultar',c.distrito_id,c.id))));
$$;

CREATE FUNCTION public.participantes_eclesiastica_app(p_registro_id uuid) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE r public.registros_eclesiasticos; resultado jsonb;
BEGIN
SELECT * INTO r FROM public.registros_eclesiasticos WHERE id=p_registro_id;
IF r.id IS NULL OR NOT public.puede_eclesiastica_app(false,r.distrito_id,r.congregacion_id)
THEN RAISE EXCEPTION 'Registro no disponible o sin autorización' USING ERRCODE='42501'; END IF;
SELECT jsonb_build_object('personas',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',p.id,'nombre',p.nombres||' '||p.apellidos) ORDER BY p.apellidos,p.nombres)
FROM public.personas p WHERE public.persona_elegible_eclesiastica_app(p.id,r.distrito_id,r.congregacion_id)),'[]'::jsonb),
'participantes',COALESCE((SELECT jsonb_agg(jsonb_build_object('persona_id',p.id,'nombre',p.nombres||' '||p.apellidos,'rol',pp.rol,'activo',pp.activo,'presente',pp.presente) ORDER BY p.apellidos,p.nombres)
FROM public.participaciones_eclesiasticas pp JOIN public.personas p ON p.id=pp.persona_id
WHERE pp.registro_id=r.id AND public.persona_elegible_eclesiastica_app(p.id,r.distrito_id,r.congregacion_id)),'[]'::jsonb)) INTO resultado;
RETURN resultado;
END $$;

CREATE FUNCTION public.guardar_participante_app(p_registro_id uuid,p_persona_id uuid,p_rol text,p_presente boolean,p_activo boolean)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE r public.registros_eclesiasticos; anterior public.participaciones_eclesiasticas; nueva uuid;
BEGIN
SELECT * INTO r FROM public.registros_eclesiasticos WHERE id=p_registro_id FOR UPDATE;
IF r.id IS NULL OR NOT public.puede_eclesiastica_app(true,r.distrito_id,r.congregacion_id)
THEN RAISE EXCEPTION 'Registro no disponible o sin autorización' USING ERRCODE='42501'; END IF;
IF r.estado IN ('inactivo','cancelado') THEN RAISE EXCEPTION 'Reactive el registro antes de modificar participantes'; END IF;
IF NOT public.persona_elegible_eclesiastica_app(p_persona_id,r.distrito_id,r.congregacion_id)
THEN RAISE EXCEPTION 'Persona fuera del territorio autorizado o inactiva' USING ERRCODE='42501'; END IF;
IF p_presente AND r.tipo NOT IN ('evento','asamblea') THEN RAISE EXCEPTION 'La asistencia solo aplica a eventos y asambleas'; END IF;
IF p_presente AND r.fecha>CURRENT_DATE THEN RAISE EXCEPTION 'No se puede marcar asistencia antes de la actividad'; END IF;
IF length(COALESCE(p_rol,''))>120 OR p_presente IS NULL OR p_activo IS NULL
THEN RAISE EXCEPTION 'Revise rol y estado'; END IF;
SELECT * INTO anterior FROM public.participaciones_eclesiasticas WHERE registro_id=p_registro_id AND persona_id=p_persona_id;
INSERT INTO public.participaciones_eclesiasticas(registro_id,persona_id,rol,presente,activo)
VALUES(p_registro_id,p_persona_id,NULLIF(btrim(p_rol),''),p_presente,p_activo)
ON CONFLICT(registro_id,persona_id) DO UPDATE SET rol=EXCLUDED.rol,presente=EXCLUDED.presente,activo=EXCLUDED.activo,actualizado_en=now()
RETURNING id INTO nueva;
INSERT INTO public.auditoria_administracion(usuario_responsable_id,accion,entidad_id,datos_anteriores,datos_nuevos)
SELECT auth.uid(),'guardar_participante',id,CASE WHEN anterior.id IS NULL THEN NULL ELSE to_jsonb(anterior) END,to_jsonb(pp)
FROM public.participaciones_eclesiasticas pp WHERE id=nueva;
RETURN nueva;
END $$;

REVOKE ALL ON FUNCTION public.puede_eclesiastica_app(boolean,uuid,uuid),public.persona_elegible_eclesiastica_app(uuid,uuid,uuid) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.listar_eclesiastica_app(text),public.guardar_eclesiastica_app(uuid,text,text,text,uuid,uuid,date,text,text),
public.participantes_eclesiastica_app(uuid),public.guardar_participante_app(uuid,uuid,text,boolean,boolean) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.listar_eclesiastica_app(text),public.guardar_eclesiastica_app(uuid,text,text,text,uuid,uuid,date,text,text),
public.participantes_eclesiastica_app(uuid),public.guardar_participante_app(uuid,uuid,text,boolean,boolean) TO authenticated;
CREATE FUNCTION public.permisos_cargo_app(p_cargo_id uuid) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE resultado jsonb;
BEGIN
IF NOT public.tiene_permiso_territorial_seguro('usuarios.administrar',NULL,NULL)
THEN RAISE EXCEPTION 'Requiere administración nacional' USING ERRCODE='42501'; END IF;
SELECT COALESCE(jsonb_agg(jsonb_build_object('codigo',p.codigo,'descripcion',p.descripcion,
'asignado',EXISTS(SELECT 1 FROM public.cargos_permisos cp WHERE cp.cargo_id=p_cargo_id AND cp.permiso_id=p.id)) ORDER BY p.codigo),'[]'::jsonb)
INTO resultado FROM public.permisos p;
RETURN resultado;
END $$;

CREATE FUNCTION public.configurar_permisos_cargo_app(p_cargo_id uuid,p_codigos text[]) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE anterior jsonb; nivel_cargo text;
BEGIN
IF NOT public.tiene_permiso_territorial_seguro('usuarios.administrar',NULL,NULL)
THEN RAISE EXCEPTION 'Requiere administración nacional' USING ERRCODE='42501'; END IF;
SELECT nivel INTO nivel_cargo FROM public.cargos WHERE id=p_cargo_id AND activo FOR UPDATE;
IF nivel_cargo IS NULL THEN RAISE EXCEPTION 'Cargo activo no encontrado'; END IF;
IF EXISTS(SELECT 1 FROM public.asignaciones_cargos a JOIN public.usuarios u ON u.persona_id=a.persona_id
WHERE u.id=auth.uid() AND a.cargo_id=p_cargo_id AND a.estado='activo'
AND a.fecha_inicio<=CURRENT_DATE AND (a.fecha_fin IS NULL OR a.fecha_fin>=CURRENT_DATE))
THEN RAISE EXCEPTION 'Otro administrador debe cambiar los permisos de su propio cargo'; END IF;
IF p_codigos IS NULL OR EXISTS(SELECT 1 FROM unnest(p_codigos) c WHERE c IS NULL OR NOT EXISTS(SELECT 1 FROM public.permisos p WHERE p.codigo=c))
THEN RAISE EXCEPTION 'La selección contiene permisos inexistentes'; END IF;
IF nivel_cargo<>'nacional' AND 'usuarios.administrar'=ANY(p_codigos)
THEN RAISE EXCEPTION 'La administración de usuarios requiere un cargo nacional'; END IF;
SELECT COALESCE(jsonb_agg(p.codigo ORDER BY p.codigo),'[]'::jsonb) INTO anterior FROM public.cargos_permisos cp JOIN public.permisos p ON p.id=cp.permiso_id WHERE cp.cargo_id=p_cargo_id;
DELETE FROM public.cargos_permisos WHERE cargo_id=p_cargo_id;
INSERT INTO public.cargos_permisos(cargo_id,permiso_id) SELECT p_cargo_id,p.id FROM public.permisos p WHERE p.codigo=ANY(p_codigos);
INSERT INTO public.auditoria_administracion(usuario_responsable_id,accion,entidad_id,datos_anteriores,datos_nuevos)
VALUES(auth.uid(),'configurar_permisos_cargo',p_cargo_id,anterior,to_jsonb(p_codigos));
RETURN p_cargo_id;
END $$;
REVOKE ALL ON FUNCTION public.permisos_cargo_app(uuid),public.configurar_permisos_cargo_app(uuid,text[]) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.permisos_cargo_app(uuid),public.configurar_permisos_cargo_app(uuid,text[]) TO authenticated;
CREATE OR REPLACE FUNCTION public.actualizar_distrito_app(p_id uuid,p_codigo text,p_nombre text,p_descripcion text,p_estado text)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE anterior public.distritos;
BEGIN
IF NOT public.tiene_permiso_territorial_seguro('distritos.crear',NULL,NULL)
THEN RAISE EXCEPTION 'Requiere permiso nacional para administrar distritos' USING ERRCODE='42501'; END IF;
IF NULLIF(btrim(p_codigo),'') IS NULL OR length(btrim(p_codigo))>20
OR NULLIF(btrim(p_nombre),'') IS NULL OR length(btrim(p_nombre))>150
OR p_estado IS NULL OR p_estado NOT IN ('activo','inactivo') OR length(COALESCE(p_descripcion,''))>4000
THEN RAISE EXCEPTION 'Revise código, nombre, estado y descripción'; END IF;
SELECT * INTO anterior FROM public.distritos WHERE id=p_id FOR UPDATE;
IF anterior.id IS NULL THEN RAISE EXCEPTION 'Distrito no encontrado'; END IF;
IF p_estado='inactivo' AND (EXISTS(SELECT 1 FROM public.registros_eclesiasticos WHERE distrito_id=p_id AND estado='activo')
OR EXISTS(SELECT 1 FROM public.congregaciones WHERE distrito_id=p_id AND estado='activa')
OR EXISTS(SELECT 1 FROM public.asignaciones_cargos WHERE distrito_id=p_id AND estado IN ('activo','suspendido')))
THEN RAISE EXCEPTION 'Finalice las asignaciones, equipos y actividades, y desactive las congregaciones antes de desactivar el distrito'; END IF;
UPDATE public.distritos SET codigo=upper(btrim(p_codigo)),nombre=btrim(p_nombre),descripcion=NULLIF(btrim(p_descripcion),''),estado=p_estado WHERE id=p_id;
INSERT INTO public.auditoria_administracion(usuario_responsable_id,accion,entidad_id,datos_anteriores,datos_nuevos)
SELECT auth.uid(),'editar_distrito',id,to_jsonb(anterior),to_jsonb(d) FROM public.distritos d WHERE id=p_id;
RETURN p_id;
END $$;

CREATE OR REPLACE FUNCTION public.actualizar_congregacion_app(p_id uuid,p_codigo text,p_nombre text,p_municipio text,p_departamento text,p_estado text)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE anterior public.congregaciones;
BEGIN
SELECT * INTO anterior FROM public.congregaciones WHERE id=p_id FOR UPDATE;
IF anterior.id IS NULL THEN RAISE EXCEPTION 'Congregación no encontrada'; END IF;
IF NOT public.tiene_acceso_app() OR NOT public.tiene_permiso_territorial('congregaciones.editar',anterior.distrito_id,p_id)
THEN RAISE EXCEPTION 'No tiene permiso para editar esta congregación' USING ERRCODE='42501'; END IF;
IF NULLIF(btrim(p_codigo),'') IS NULL OR length(btrim(p_codigo))>30
OR NULLIF(btrim(p_nombre),'') IS NULL OR length(btrim(p_nombre))>180
OR length(COALESCE(p_municipio,''))>120 OR length(COALESCE(p_departamento,''))>120
OR p_estado IS NULL OR p_estado NOT IN ('activa','inactiva','cerrada')
THEN RAISE EXCEPTION 'Revise los datos de la congregación'; END IF;
IF p_estado<>'activa' AND (
EXISTS(SELECT 1 FROM public.registros_eclesiasticos WHERE congregacion_id=p_id AND estado='activo') OR EXISTS(SELECT 1 FROM public.membresias_congregacionales WHERE congregacion_id=p_id AND estado='activa')
OR EXISTS(SELECT 1 FROM public.obras_proyeccion WHERE congregacion_origen_id=p_id AND estado NOT IN ('cerrada','constituida'))
OR EXISTS(SELECT 1 FROM public.asignaciones_cargos WHERE congregacion_id=p_id AND estado IN ('activo','suspendido')))
THEN RAISE EXCEPTION 'La congregación tiene membresías, obras, cargos o actividades vigentes. Resuélvalos antes de desactivarla'; END IF;
UPDATE public.congregaciones SET codigo=upper(btrim(p_codigo)),nombre=btrim(p_nombre),municipio=NULLIF(btrim(p_municipio),''),
departamento=NULLIF(btrim(p_departamento),''),estado=p_estado WHERE id=p_id;
INSERT INTO public.auditoria_administracion(usuario_responsable_id,accion,entidad_id,datos_anteriores,datos_nuevos)
SELECT auth.uid(),'editar_congregacion',id,to_jsonb(anterior),to_jsonb(c) FROM public.congregaciones c WHERE id=p_id;
RETURN p_id;
END $$;


