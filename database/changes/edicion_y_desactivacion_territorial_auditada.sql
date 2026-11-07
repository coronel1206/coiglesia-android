-- Migración ya aplicada: 20261106235504. Recuperada del historial de Supabase; no volver a aplicar.
-- Edición y desactivación lógica. No borra registros ni mueve membresías.
CREATE OR REPLACE FUNCTION public.listar_distritos_congregaciones_app()
RETURNS SETOF public.distritos LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
SELECT d.* FROM public.distritos d WHERE d.estado='activo' AND public.tiene_acceso_app()
AND (public.tiene_permiso_territorial_seguro('congregaciones.crear',d.id,NULL)
OR EXISTS(SELECT 1 FROM public.congregaciones c WHERE c.distrito_id=d.id
AND public.tiene_permiso_territorial('congregaciones.editar',d.id,c.id))) ORDER BY d.nombre;
$$;

CREATE OR REPLACE FUNCTION public.listar_congregaciones_gestion_app(p_distrito_id uuid)
RETURNS SETOF public.congregaciones LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
SELECT c.* FROM public.congregaciones c WHERE c.distrito_id=p_distrito_id AND public.tiene_acceso_app()
AND (public.tiene_permiso_territorial_seguro('congregaciones.crear',c.distrito_id,NULL)
OR public.tiene_permiso_territorial('congregaciones.editar',c.distrito_id,c.id)) ORDER BY c.nombre;
$$;

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
IF p_estado='inactivo' AND (EXISTS(SELECT 1 FROM public.congregaciones WHERE distrito_id=p_id AND estado='activa')
OR EXISTS(SELECT 1 FROM public.asignaciones_cargos WHERE distrito_id=p_id AND estado IN ('activo','suspendido')))
THEN RAISE EXCEPTION 'Finalice las asignaciones y desactive las congregaciones antes de desactivar el distrito'; END IF;
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
EXISTS(SELECT 1 FROM public.membresias_congregacionales WHERE congregacion_id=p_id AND estado='activa')
OR EXISTS(SELECT 1 FROM public.obras_proyeccion WHERE congregacion_origen_id=p_id AND estado NOT IN ('cerrada','constituida'))
OR EXISTS(SELECT 1 FROM public.asignaciones_cargos WHERE congregacion_id=p_id AND estado IN ('activo','suspendido')))
THEN RAISE EXCEPTION 'La congregación tiene membresías, obras o cargos vigentes. Resuélvalos antes de desactivarla'; END IF;
UPDATE public.congregaciones SET codigo=upper(btrim(p_codigo)),nombre=btrim(p_nombre),municipio=NULLIF(btrim(p_municipio),''),
departamento=NULLIF(btrim(p_departamento),''),estado=p_estado WHERE id=p_id;
INSERT INTO public.auditoria_administracion(usuario_responsable_id,accion,entidad_id,datos_anteriores,datos_nuevos)
SELECT auth.uid(),'editar_congregacion',id,to_jsonb(anterior),to_jsonb(c) FROM public.congregaciones c WHERE id=p_id;
RETURN p_id;
END $$;

-- Coordina altas con desactivaciones concurrentes del territorio.
CREATE OR REPLACE FUNCTION public.validar_territorio_membresia() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
IF NEW.estado='activa' THEN
PERFORM 1 FROM public.congregaciones c JOIN public.distritos d ON d.id=c.distrito_id
WHERE c.id=NEW.congregacion_id AND c.estado='activa' AND d.estado='activo' FOR SHARE OF c,d;
IF NOT FOUND THEN RAISE EXCEPTION 'La membresía requiere una congregación y distrito activos'; END IF;
END IF;
RETURN NEW;
END $$;
CREATE TRIGGER validar_territorio_membresia BEFORE INSERT OR UPDATE OF congregacion_id,estado
ON public.membresias_congregacionales FOR EACH ROW EXECUTE FUNCTION public.validar_territorio_membresia();

CREATE OR REPLACE FUNCTION public.validar_distrito_congregacion() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
IF NEW.estado='activa' THEN
PERFORM 1 FROM public.distritos WHERE id=NEW.distrito_id AND estado='activo' FOR SHARE;
IF NOT FOUND THEN RAISE EXCEPTION 'La congregación requiere un distrito activo'; END IF;
END IF;
RETURN NEW;
END $$;
CREATE TRIGGER validar_distrito_congregacion BEFORE INSERT OR UPDATE OF distrito_id,estado
ON public.congregaciones FOR EACH ROW EXECUTE FUNCTION public.validar_distrito_congregacion();

REVOKE ALL ON FUNCTION public.validar_territorio_membresia(),public.validar_distrito_congregacion() FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.listar_distritos_congregaciones_app(),public.listar_congregaciones_gestion_app(uuid),
public.actualizar_distrito_app(uuid,text,text,text,text),public.actualizar_congregacion_app(uuid,text,text,text,text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.listar_distritos_congregaciones_app(),public.listar_congregaciones_gestion_app(uuid),
public.actualizar_distrito_app(uuid,text,text,text,text),public.actualizar_congregacion_app(uuid,text,text,text,text,text) TO authenticated;

