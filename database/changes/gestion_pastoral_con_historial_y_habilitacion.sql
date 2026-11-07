-- Migración ya aplicada: 20261107000754. Recuperada del historial de Supabase; no volver a aplicar.
CREATE FUNCTION public.listar_pastoral_app() RETURNS jsonb
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
SELECT jsonb_build_object('obras',COALESCE((SELECT jsonb_agg(to_jsonb(x) ORDER BY x.nombre) FROM (
SELECT o.id,o.nombre,o.estado,c.nombre AS congregacion,d.nombre AS distrito,
public.tiene_permiso_territorial_seguro('pastores.autorizar_posesion',d.id,NULL) AS editable,
(SELECT jsonb_agg(jsonb_build_object('id',n.id,'persona_id',n.pastor_persona_id,'nombre',p.nombres||' '||p.apellidos,
'estado',n.estado,'desde',n.fecha_inicio,'hasta',n.fecha_fin) ORDER BY n.fecha_registro DESC)
FROM public.nombramientos_pastorales_obras n JOIN public.personas p ON p.id=n.pastor_persona_id WHERE n.obra_id=o.id) AS nombramientos
FROM public.obras_proyeccion o JOIN public.congregaciones c ON c.id=o.congregacion_origen_id JOIN public.distritos d ON d.id=c.distrito_id
WHERE public.tiene_acceso_app() AND (public.tiene_permiso_territorial_seguro('obras.consultar',d.id,c.id)
OR public.tiene_permiso_territorial_seguro('pastores.autorizar_posesion',d.id,NULL)))x),'[]'::jsonb),
'candidatos',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',p.id,'nombre',p.nombres||' '||p.apellidos) ORDER BY p.apellidos,p.nombres)
FROM public.personas p WHERE public.tiene_acceso_app() AND public.esta_habilitado_pastoralmente(p.id)
AND public.persona_elegible_eclesiastica_app(p.id,NULL,NULL)),'[]'::jsonb));
$$;

CREATE FUNCTION public.gestionar_pastor_obra_app(p_obra_id uuid,p_persona_id uuid,p_motivo text,p_nombramiento_esperado_id uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE distrito uuid; congregacion uuid; vigente uuid;
BEGIN
SELECT c.distrito_id,c.id INTO distrito,congregacion FROM public.obras_proyeccion o
JOIN public.congregaciones c ON c.id=o.congregacion_origen_id WHERE o.id=p_obra_id FOR UPDATE OF o;
IF distrito IS NULL OR NOT public.tiene_permiso_territorial_seguro('pastores.autorizar_posesion',distrito,NULL)
OR NOT EXISTS(SELECT 1 FROM public.congregaciones WHERE id=congregacion AND estado='activa')
THEN RAISE EXCEPTION 'No tiene autorización sobre esta obra' USING ERRCODE='42501'; END IF;
IF NOT public.esta_habilitado_pastoralmente(p_persona_id) THEN RAISE EXCEPTION 'La persona no tiene habilitación pastoral vigente'; END IF;
SELECT id INTO vigente FROM public.nombramientos_pastorales_obras WHERE obra_id=p_obra_id AND estado='activo' FOR UPDATE;
IF vigente IS DISTINCT FROM p_nombramiento_esperado_id THEN RAISE EXCEPTION 'El nombramiento cambió. Actualice antes de confirmar'; END IF;
IF vigente IS NULL THEN RETURN public.autorizar_pastor_obra(p_obra_id,p_persona_id,p_motivo);
ELSE RETURN public.reemplazar_pastor_obra(p_obra_id,p_persona_id,p_motivo,false); END IF;
END $$;

CREATE FUNCTION public.finalizar_pastor_obra_app(p_obra_id uuid,p_nombramiento_id uuid,p_motivo text) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE distrito uuid;
BEGIN
SELECT c.distrito_id INTO distrito FROM public.obras_proyeccion o JOIN public.congregaciones c ON c.id=o.congregacion_origen_id
WHERE o.id=p_obra_id FOR UPDATE OF o;
IF distrito IS NULL OR NOT public.tiene_permiso_territorial_seguro('pastores.autorizar_posesion',distrito,NULL)
THEN RAISE EXCEPTION 'No tiene autorización sobre esta obra' USING ERRCODE='42501'; END IF;
IF p_motivo IS NULL OR length(btrim(p_motivo)) NOT BETWEEN 10 AND 2000 THEN RAISE EXCEPTION 'Indique un motivo de 10 a 2000 caracteres'; END IF;
UPDATE public.nombramientos_pastorales_obras SET estado='finalizado',fecha_fin=CURRENT_DATE,
motivo=motivo||E'\nFinalización: '||btrim(p_motivo)
WHERE id=p_nombramiento_id AND obra_id=p_obra_id AND estado='activo' AND fecha_inicio<=CURRENT_DATE;
IF NOT FOUND THEN RAISE EXCEPTION 'El nombramiento cambió. Actualice el historial'; END IF;
RETURN p_nombramiento_id;
END $$;
REVOKE ALL ON FUNCTION public.listar_pastoral_app(),public.gestionar_pastor_obra_app(uuid,uuid,text,uuid),public.finalizar_pastor_obra_app(uuid,uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.listar_pastoral_app(),public.gestionar_pastor_obra_app(uuid,uuid,text,uuid),public.finalizar_pastor_obra_app(uuid,uuid,text) TO authenticated;

